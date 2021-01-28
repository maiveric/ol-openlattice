package com.openlattice.users

import com.hazelcast.scheduledexecutor.StatefulTask
import com.openlattice.tasks.HazelcastFixedRateTask
import com.openlattice.tasks.HazelcastTaskDependencies
import com.openlattice.tasks.Task
import org.slf4j.Logger
import java.time.Instant
import java.util.concurrent.TimeUnit

internal const val REFRESH_INTERVAL_MILLIS = 120_000L // 2 min
internal const val DEFAULT_CHUNK_SIZE = 10_000
internal const val LAST_SYNC = "lastSync"

/**
 * @author Drew Bailey (drew@openlattice.com)
 */
interface Auth0SyncTask: HazelcastFixedRateTask<Auth0SyncTaskDependencies>,
        HazelcastTaskDependencies,
        StatefulTask<String, Instant> {

    val logger: Logger

    var lastSync: Instant

    override fun runTask() {
        if (!initialized()) {
            logger.warn("Users not yet initialized.")
            return
        }

        updateUsersCache()
        syncUsers()
    }

    override fun getDependenciesClass(): Class<Auth0SyncTaskDependencies> {
        return Auth0SyncTaskDependencies::class.java
    }

    override fun getTimeUnit(): TimeUnit {
        return TimeUnit.MILLISECONDS
    }

    override fun getInitialDelay(): Long {
        return REFRESH_INTERVAL_MILLIS
    }

    override fun getPeriod(): Long {
        return REFRESH_INTERVAL_MILLIS
    }

    override fun getName(): String {
        return Task.AUTH0_SYNC_TASK.name
    }

    override fun save(snapshot: MutableMap<String, Instant>) {
        snapshot[LAST_SYNC] = lastSync
    }

    override fun load(snapshot: MutableMap<String, Instant>) {
        if (snapshot.containsKey(LAST_SYNC)) {
            lastSync = snapshot.getValue(LAST_SYNC)
        }
    }

    fun initializeUsers()

    fun syncUsers()

    fun updateUsersCache()

    fun initialized(): Boolean
}