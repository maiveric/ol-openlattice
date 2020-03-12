package com.openlattice.users

import com.auth0.json.mgmt.users.User
import com.dataloom.mappers.ObjectMappers
import com.fasterxml.jackson.module.kotlin.readValue
import com.hazelcast.core.HazelcastInstance
import com.hazelcast.query.Predicate
import com.hazelcast.query.Predicates
import com.openlattice.IdConstants
import com.openlattice.authorization.*
import com.openlattice.authorization.mapstores.PrincipalMapstore
import com.openlattice.authorization.mapstores.ReadSecurablePrincipalAggregator
import com.openlattice.hazelcast.HazelcastMap
import com.openlattice.organizations.HazelcastOrganizationService
import com.openlattice.organizations.SortedPrincipalSet
import com.openlattice.organizations.roles.SecurePrincipalsManager
import com.openlattice.postgres.PostgresColumn.*
import com.openlattice.postgres.PostgresTable.USERS
import com.openlattice.postgres.streams.BasePostgresIterable
import com.openlattice.postgres.streams.PreparedStatementHolderSupplier
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.util.*

const val DELETE_BATCH_SIZE = 1024
private val markUserSql = "UPDATE ${USERS.name} SET ${EXPIRATION.name} = ? WHERE ${USER_ID.name} = ?"
private val expiredUsersSql = "SELECT ${USER_DATA.name} from ${USERS.name} WHERE ${EXPIRATION.name} < ? "

/**
 *
 * @author Matthew Tamayo-Rios &lt;matthew@openlattice.com&gt;
 */
class Auth0SyncService(
        hazelcastInstance: HazelcastInstance,
        private val hds: HikariDataSource,
        private val spm: SecurePrincipalsManager,
        private val orgService: HazelcastOrganizationService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(Auth0SyncService::class.java)
    }

    private val users = HazelcastMap.USERS.getMap(hazelcastInstance)
    private val principals = HazelcastMap.PRINCIPALS.getMap(hazelcastInstance)
    private val authnPrincipalCache = HazelcastMap.SECURABLE_PRINCIPALS.getMap(hazelcastInstance)
    private val authnRolesCache = HazelcastMap.RESOLVED_PRINCIPAL_TREES.getMap(hazelcastInstance)
    private val principalTrees = HazelcastMap.PRINCIPAL_TREES.getMap(hazelcastInstance)
    private val mapper = ObjectMappers.newJsonMapper()

    fun getCachedUsers(): Sequence<User> {
        return users.values.asSequence()
    }

    fun syncUser(user: User) {
        updateUser(user)
        syncUserEnrollmentsAndAuthentication(user)
    }

    fun updateUser(user: User) {
        logger.info("Updating user ${user.id}")
        ensureSecurablePrincipalExists(user)

        //Update the user in the users table
        users.set(user.id, user)
    }

    fun syncUserEnrollmentsAndAuthentication(user: User) {
        //Figure out which users need to be added to which organizations.
        //Since we don't want to do O( # organizations ) for each user, we need to lookup organizations on a per user
        //basis and see if the user needs to be added.
        logger.info("Synchronizing enrollments and authentication cache for user ${user.id}")
        val principal = getPrincipal(user)

        processGlobalEnrollments(principal, user)
        processOrganizationEnrollments(principal, user)

        syncAuthenticationCache(principal.id)
        markUser(user.id)
    }

    private fun markUser(userId: String) {
        hds.connection.use { connection ->
            connection.prepareStatement(markUserSql).use { ps ->
                ps.setLong(1, System.currentTimeMillis())
                ps.setString(2, userId)
                ps.executeUpdate()
            }
        }
    }

    private fun syncAuthenticationCache(principalId: String) {
        val sp = getPrincipal(principalId) ?: return
        authnPrincipalCache.set(principalId, sp)
        val securablePrincipals = getAllPrincipals(sp) ?: return

        val currentPrincipals: NavigableSet<Principal> = TreeSet()
        currentPrincipals.add(sp.principal)
        securablePrincipals.stream()
                .map(SecurablePrincipal::getPrincipal)
                .forEach { currentPrincipals.add(it) }

        authnRolesCache.set(principalId, SortedPrincipalSet(currentPrincipals))
    }

    private fun getLayer(aclKeys: Set<AclKey>): AclKeySet {
        return AclKeySet(principalTrees.getAll(aclKeys).values.flatMap { it.value })
    }

    private fun getAllPrincipals(sp: SecurablePrincipal): Collection<SecurablePrincipal>? {
        val roles = getLayer(setOf(sp.aclKey))
        var nextLayer: Set<AclKey> = roles

        while (nextLayer.isNotEmpty()) {
            nextLayer = getLayer(nextLayer) - roles
            roles.addAll(nextLayer)
        }

        return principals.getAll(roles).values
    }

    @Suppress("UNCHECKED_CAST")
    private fun getPrincipal(principalId: String): SecurablePrincipal? {
        return principals.aggregate(
                ReadSecurablePrincipalAggregator(),
                Predicates.equal(
                        PrincipalMapstore.PRINCIPAL_INDEX, Principals.getUserPrincipal(principalId)
                ) as Predicate<AclKey, SecurablePrincipal>
        )

    }

    private fun processGlobalEnrollments(principal: Principal, user: User) {
        orgService.addMembers(
                IdConstants.GLOBAL_ORGANIZATION_ID.id,
                setOf(principal),
                mapOf(principal to getAppMetadata(user))
        )
    }

    private fun processOrganizationEnrollments(
            principal: Principal,
            user: User,
            emailDomain: String = user.email ?: ""
    ) {
        val connections = getConnections(user).values

        val missingOrgsForEmailDomains = if (emailDomain.isNotBlank()) {
            orgService.getOrganizationsWithoutUserAndWithConnectionsAndDomains(
                    principal,
                    connections,
                    emailDomain
            )
        } else setOf()

        val missingOrgsForConnections = orgService.getOrganizationsWithoutUserAndWithConnection(connections, principal)


        (missingOrgsForEmailDomains + missingOrgsForConnections).forEach { orgId ->
            orgService.addMembers(orgId, setOf(principal))
        }

    }

    // TODO handle user expiration
    fun getExpiredUsers(): BasePostgresIterable<User> {
        val expirationThreshold = System.currentTimeMillis() - 6 * REFRESH_INTERVAL_MILLIS
        return BasePostgresIterable<User>(
                PreparedStatementHolderSupplier(hds, expiredUsersSql, DELETE_BATCH_SIZE) { ps ->
                    ps.setLong(1, expirationThreshold)
                }) { rs -> mapper.readValue(rs.getString(USER_DATA.name)) }
    }

    private fun ensureSecurablePrincipalExists(user: User): Principal {
        val principal = getPrincipal(user)
        if (!spm.principalExists(principal)) {
            val title = if (!user.nickname.isNullOrEmpty()) {
                user.nickname
            } else {
                user.email
            }

            spm.createSecurablePrincipalIfNotExists(
                    principal,
                    SecurablePrincipal(Optional.empty(), principal, title, Optional.empty())
            )
        }
        return principal
    }

}



