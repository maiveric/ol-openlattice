/*
 * Copyright (C) 2018. OpenLattice, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * You can contact the owner of the copyright at support@openlattice.com
 *
 *
 */

package com.openlattice.linking

import com.codahale.metrics.annotation.Timed
import com.google.common.base.Stopwatch
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.util.concurrent.ListeningExecutorService
import com.openlattice.data.EntityDataKey
import com.openlattice.data.EntityKeyIdService
import com.openlattice.linking.clustering.ClusterUpdate
import com.openlattice.postgres.streams.PostgresIterable
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.stream.Stream
import javax.annotation.PostConstruct


/**
 * Entity sets ids are assigned by calling [UUID.randomUUID] as a result we know that this can never be accidentally
 * assigned to any real entity set.
 */
internal val LINKING_ENTITY_SET_ID = UUID(0, 0)
internal const val PERSON_FQN = "general.person"
internal const val REFRESH_PROPERTY_TYPES_INTERVAL_MILLIS = 30000L

/**
 * Performs realtime linking of individuals as they are integrated ino the system.
 */
@Component
class RealtimeLinkingService
(
        private val blocker: Blocker,
        private val matcher: Matcher,
        private val ids: EntityKeyIdService,
        private val loader: DataLoader,
        private val gqs: LinkingQueryService,
        private val executor: ListeningExecutorService,
        private val linkableTypes: Set<UUID>,
        private val blockSize: Int
) {
    companion object {
        private val logger = LoggerFactory.getLogger(RealtimeLinkingService::class.java)
        private val clusterLocks: MutableMap<UUID, ReentrantLock> = mutableMapOf()
        private val clusterUpdateLock = ReentrantLock()
    }

    private val running = ReentrantLock()
    /**
     * Linking:
     * 1) For each new person entity perform blocking
     * 2) Use the results of block to identify candidate clusters
     * 3) Insert the results of the match scores
     * 4) Update the linked entities table.
     */
    private fun runIterativeLinking(
            entitySetId: UUID,
            entityKeyIds: Stream<UUID>
    ) {

        entityKeyIds
                .parallel()
                .map {
                    val sw = Stopwatch.createStarted()
                    val block = blocker.block(entitySetId, it)
                    logger.info("Blocking ($entitySetId, $it) took ${sw.elapsed(TimeUnit.MILLISECONDS)} ms.")
                    block
                }
                .filter {
                    if (it.second.containsKey(it.first)) {
                        return@filter true
                    } else {
                        logger.error("Skipping block for data key: {}", it.first)
                    }

                    false
                }
                .map {
                    //block contains element being blocked
                    val blockKey = it.first
                    val elem = it.second[blockKey]!!
                    val initializedBlock = matcher.initialize(it)
                    val dataKeys = collectKeys(initializedBlock.second)
                    //While a best cluster is being selected and updated we can't have other clusters being updated
                    //In the future we can
                    try {
                        clusterUpdateLock.lock()
                        var requiredClusters = gqs.getIdsOfClustersContaining(dataKeys).toList()
                        requiredClusters.forEach { clusterLocks.getOrPut(it) { ReentrantLock() }.lock() }
                        clusterUpdateLock.unlock()

                        val clusters = gqs.getClustersContaining(requiredClusters)


                        if (clusters.isEmpty()) {
                            val clusterId = ids.reserveIds(LINKING_ENTITY_SET_ID, 1).first()
                            val block = blockKey to mapOf(blockKey to elem)
                            return@map ClusterUpdate(clusterId, blockKey, matcher.match(block).second)
                        }

                        var maybeBestCluster: Pair<UUID, Map<EntityDataKey, Map<EntityDataKey, Double>>>? = null
                        var lowestAvgScore: Double = -10.0 //Arbitrary any negative value should suffice

                        clusters
                                .forEach {
                                    val block = blockKey to loader.getEntities(collectKeys(it.value) + blockKey)
                                    val matchedBlock = matcher.match(block)
                                    val matchedCluster = matchedBlock.second
                                    val clusterSize = matchedCluster.values.sumBy { it.size }
                                    val avgScore = (matchedCluster.values.sumByDouble { it.values.sum() } / clusterSize)
                                    if (lowestAvgScore > avgScore || lowestAvgScore < 0) {
                                        lowestAvgScore = avgScore
                                        maybeBestCluster = it.key to matchedCluster
                                    }
                                    clusterLocks[it.key]!!.unlock()
                                }

                        val bestCluster = maybeBestCluster!!
                        ClusterUpdate(bestCluster.first, blockKey, bestCluster.second)
                    } catch (ex: Exception) {
                        if (clusterUpdateLock.isLocked) {
                            clusterUpdateLock.unlock()
                        }
                        clusterLocks.values.forEach { lock ->
                            if (lock.isLocked) {
                                lock.unlock()
                            }
                        }
                        throw IllegalStateException("Error occured while performing linking.", ex)
                    }
                }
                .forEach { clusterUpdate ->
                    clusterLocks[clusterUpdate.clusterId]!!.unlock()
                    gqs.insertMatchScores(clusterUpdate.clusterId, clusterUpdate.scores)
                    gqs.updateLinkingTable(clusterUpdate.clusterId, clusterUpdate.newMember)
                }

    }

    private fun <T> collectKeys(m: Map<EntityDataKey, Map<EntityDataKey, T>>): Set<EntityDataKey> {
        return m!!.keys + m.values.flatMap { it.keys }
    }

    private fun clearNeighborhoods(entitySetId: UUID, entityKeyIds: Stream<UUID>) {
        logger.debug("Starting neighborhood cleanup of {}", entitySetId)
        val clearedCount = entityKeyIds
                .parallel()
                .map { EntityDataKey(entitySetId, it) }
                .mapToInt(gqs::deleteNeighborhood)
                .sum()
        logger.debug("Cleared {} neighbors from neighborhood of {}", clearedCount, entitySetId)
    }

    @Scheduled(fixedRate = 30000)
    @Timed
    fun runLinking() {
        if (running.tryLock()) {
            try {
                gqs.getEntitySetsNeedingLinking(linkableTypes).forEach {
                    logger.info("Running linking on entity set {}.", it)

                    var entitiesNeedingLinking = gqs.getEntitiesNeedingLinking(it).toList()

                    while (entitiesNeedingLinking.isNotEmpty()) {
                        val sw = Stopwatch.createStarted()
                        refreshLinks(it, entitiesNeedingLinking)
                        entitiesNeedingLinking = gqs.getEntitiesNeedingLinking(it).toList()
                        logger.info(
                                "Linked {} entities in {} ms", entitiesNeedingLinking.size,
                                sw.elapsed(TimeUnit.MILLISECONDS)
                        )
                    }
                }
            } finally {
                running.unlock()
            }
        }
    }


    private fun refreshLinks(entitySetId: UUID, entityKeyIds: PostgresIterable<UUID>) {
        clearNeighborhoods(entitySetId, entityKeyIds.stream())
        runIterativeLinking(entitySetId, entityKeyIds.stream())
    }


    private fun refreshLinks(entitySetId: UUID, entityKeyIds: Collection<UUID>) {

        clearNeighborhoods(entitySetId, entityKeyIds.stream())
        runIterativeLinking(entitySetId, entityKeyIds.parallelStream())

    }

}