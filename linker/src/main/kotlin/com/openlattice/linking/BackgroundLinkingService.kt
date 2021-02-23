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

import com.codahale.metrics.Histogram
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.MetricRegistry.name
import com.codahale.metrics.Snapshot
import com.google.common.base.Stopwatch
import com.google.common.collect.Sets
import com.google.common.util.concurrent.ListeningExecutorService
import com.hazelcast.core.HazelcastInstance
import com.hazelcast.query.Predicates
import com.openlattice.data.EntityDataKey
import com.openlattice.data.EntityKeyIdService
import com.openlattice.edm.EntitySet
import com.openlattice.edm.set.EntitySetFlag
import com.openlattice.hazelcast.HazelcastMap
import com.openlattice.hazelcast.HazelcastQueue
import com.openlattice.linking.blocking.Block
import com.openlattice.linking.blocking.Blocker
import com.openlattice.linking.clustering.Cluster
import com.openlattice.linking.clustering.Clusterer
import com.openlattice.linking.clustering.KeyedCluster
import com.openlattice.linking.matching.Matcher
import com.openlattice.postgres.mapstores.EntitySetMapstore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

internal const val REFRESH_PROPERTY_TYPES_INTERVAL_MILLIS = 30000L
internal const val LINKING_BATCH_TIMEOUT_MILLIS = 120000L
internal const val MINIMUM_SCORE = 0.75
internal const val LINKING_RATE = 300_000L

/**
 * Performs realtime linking of individuals as they are integrated ino the system.
 */
@Component
class BackgroundLinkingService(
        private val executor: ListeningExecutorService,
        hazelcastInstance: HazelcastInstance,
        private val blocker: Blocker,
        private val matcher: Matcher,
        private val ids: EntityKeyIdService,
        private val clusterer: Clusterer,
        private val lqs: LinkingQueryService,
        private val linkingFeedbackService: PostgresLinkingFeedbackService,
        private val linkableTypes: Set<UUID>,
        private val configuration: LinkingConfiguration
) {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(BackgroundLinkingService::class.java)

        fun <T> collectKeys(m: Map<EntityDataKey, Map<EntityDataKey, T>>): Set<EntityDataKey> {
            return m.keys + m.values.flatMap { it.keys }
        }

        val metrics: MetricRegistry = MetricRegistry()

        inline fun <R> MetricRegistry.histogramify(
                clazz: Class<*>,
                vararg names: String,
                block: (Logger, Stopwatch) -> R
        ): R {
            val hist = histogram(name(clazz, *names))
            val logger = logger //loggers.getOrPut(clazz) { LoggerFactory.getLogger(clazz) }
            val sw = Stopwatch.createStarted()
            val ret = block(logger, sw)
            hist.update(sw.stop().elapsed(TimeUnit.MILLISECONDS))
            return ret
        }

        fun printHistogram(name: String, histogram: Histogram) {
            val snapshot: Snapshot = histogram.snapshot
            logger.error("""
                         [$name] stats
                         count = ${histogram.count}
                           min = ${snapshot.min}
                           max = ${snapshot.max}
                          mean = ${snapshot.mean}
                        stddev = ${snapshot.stdDev}
                        median = ${snapshot.median}
                          75%% <= ${snapshot.get75thPercentile()}
                          95%% <= ${snapshot.get95thPercentile()}
                          98%% <= ${snapshot.get98thPercentile()}
                          99%% <= ${snapshot.get99thPercentile()}
                        99.9%% <= ${snapshot.get999thPercentile()}
            """.trimIndent())
        }
    }

    private val entitySets = HazelcastMap.ENTITY_SETS.getMap(hazelcastInstance)
    private val linkingLocks = HazelcastMap.LINKING_LOCKS.getMap(hazelcastInstance)
    private val candidates = HazelcastQueue.LINKING_CANDIDATES.getQueue( hazelcastInstance )
    private val priorityEntitySets = configuration.whitelist.orElseGet { setOf() }

    @Suppress("UNUSED")
    @Scheduled(fixedRate = LINKING_RATE)
    fun enqueue() {
        val linkedInSession = metrics.histogram(name(BackgroundLinkingService::class.java, "linking")).count
        logger.info("$linkedInSession entities linked since last startup.")

        metrics.histograms.forEach { (name, histogram) ->
            printHistogram(name, histogram)
        }
        if ( candidates.isNotEmpty() ){
            logger.info("Linking queue still has candidates on it, not adding more at the moment")
            return
        }
        try {
            val filteredLinkableEntitySetIds = entitySets.keySet(
                    Predicates.and(
                            Predicates.`in`<UUID, EntitySet>(EntitySetMapstore.ENTITY_TYPE_ID_INDEX, *linkableTypes.toTypedArray()),
                            Predicates.notEqual<UUID,EntitySet>(EntitySetMapstore.FLAGS_INDEX, EntitySetFlag.LINKING)
                    )
            )

            (priorityEntitySets + filteredLinkableEntitySetIds).filter {
                val es = entitySets[it]
                if ( es == null ){
                    logger.info("Entityset with id {} doesnt exist", it)
                    return@filter false
                }

                val keep = filteredLinkableEntitySetIds.contains(it)
                if ( keep ) {
                    logger.info("including entityset {} for candidate linking", es.id )
                } else {
                    logger.info("excluding entityset {} because its not linkable", es.name)
                }
                keep
            }.forEach { esid ->
                val es = entitySets.getValue(esid)
                logger.info("Starting to queue linking candidates from entity set {}({})", es.name, esid)
                val forLinking = lqs.getEntitiesNeedingLinking(
                        esid,
                        3 * configuration.loadSize
                )
                logger.debug("Entities needing linking: {}", forLinking)
                candidates.addAll(forLinking)
                logger.info( "Queued entities needing linking")
            }
        } catch (ex: Exception) {
            logger.info("Encountered error while updating candidates for linking.", ex)
        }
    }

    private val limiter = Semaphore(configuration.parallelism * 2)

    @Suppress("UNUSED")
    private val linkingWorker = if (isLinkingEnabled()) executor.submit {
        while (true) {
            try {
                val candidate = candidates.take()
                limiter.acquire()
                executor.submit( Runnable {
                    if( !tryLockCandidate(candidate) ) {
                        logger.info("candidate already locked for linking: {}\nNot resubmitting", candidate)
                        return@Runnable
                    }
                    logger.info("candidate freshly locked for linking: {}", candidate)
                    try {
                        logger.info("Linking {}", candidate)
                        metrics.histogramify(
                                BackgroundLinkingService::class.java,
                                "linking"
                        ) { _, _ ->
                            link(candidate)
                        }
                        logger.info("Finished linking {}", candidate)
                    } catch (ex: Exception) {
                        logger.error("Unable to link {}.", candidate, ex)
                    } finally {
                        logger.info("Unlocking candidate after linking: {}", candidate)
                        unlock(candidate)
                        limiter.release()
                    }
                })
            } catch (ex: Exception) {
                logger.info("Encountered error while linking candidates.", ex)
            }
        }
    } else null

    /**
     * Links a candidate entity to other matching entities.
     *
     * 1) Uses the results of blocking to identify candidate clusters
     * 2) Insert the results of the match scores
     * 3) Update the linked entities table.
     *
     * @param candidate The data key for the entity to perform linking upon.
     */
    private fun link(candidate: EntityDataKey) {
        metrics.histogramify(
                BackgroundLinkingService::class.java,
                "clearNeighborhoods"
        ) { _, _ ->
            clearNeighborhoods(candidate)
        }
        //TODO: if we have positive feedbacks on entity, we use its linking id and match them together
        // Run standard blocking + clustering
        val initialBlock = metrics.histogramify(
                BackgroundLinkingService::class.java,
                "initialBlocking"
        ) { log, sw ->
            val block = blocker.block(candidate)
            val time = sw.elapsed(TimeUnit.MILLISECONDS)
            log.info(
                    "Blocking ({}, {}) took {} ms.",
                    candidate.entitySetId,
                    candidate.entityKeyId,
                    time
            )
            block
        }

        //block contains element being blocked
        val elem = initialBlock.entities.getValue(candidate)

        // initialize
        val initializedBlock = metrics.histogramify(
                BackgroundLinkingService::class.java,
                "matcherInitialize"
        ) { log, sw ->
            log.info("Initializing matching for block {}", candidate)
            val init = matcher.initialize(initialBlock)
            log.info("Initialization took {} ms", sw.elapsed(TimeUnit.MILLISECONDS))
            init
        }

        val dataKeys = collectKeys(initializedBlock.matches)

        //Decision that needs to be made is whether to start new cluster or merge into existing cluster.
        try {
            val (linkingId, scores) = lqs.lockClustersDoWorkAndCommit( candidate, dataKeys) { clusters ->
                val maybeBestCluster = clusters.asSequence()
                            .map { cluster ->
                                metrics.histogramify(
                                        BackgroundLinkingService::class.java,
                                        "clusterer","cluster"
                                ) { _, _ ->
                                    clusterer.cluster(candidate, KeyedCluster.fromEntry(cluster))
                                }
                            }.filter { it.score > MINIMUM_SCORE }
                            .maxBy { it.score }

                return@lockClustersDoWorkAndCommit if ( maybeBestCluster != null ) {
                    Triple(maybeBestCluster.clusterId, maybeBestCluster.cluster, false)
                } else {
                    val linkingId = ids.reserveLinkingIds(1).first()
                    val block = Block(candidate, mapOf(candidate to elem))
                    val cluster = matcher.match(block).matches
                    //TODO: When creating new cluster do we really need to re-match or can we assume score of 1.0?
                    Triple(linkingId, cluster, true)
                }
            }
            insertMatches(linkingId, candidate, Cluster(scores))
        } catch (ex: Exception) {
            logger.error("An error occurred while performing linking.", ex)
            throw IllegalStateException("Error occured while performing linking.", ex)
        }
    }

    private fun clearNeighborhoods(candidate: EntityDataKey) {
        logger.debug("Starting neighborhood cleanup of {}", candidate)
        // TODO: gather Positive feedbacks
        val positiveFeedbacks = listOf<EntityKeyPair>()

        val clearedCount = lqs.deleteNeighborhood(candidate, positiveFeedbacks)
        logger.debug("Cleared {} neighbors from neighborhood of {}", clearedCount, candidate)
    }

    private fun insertMatches(
            linkingId: UUID,
            newMember: EntityDataKey,
            scores: Cluster
    ) {
        val scoresAsEsidToEkids = (collectKeys(scores) + newMember)
                .groupBy { edk -> edk.entitySetId }
                .mapValues { (_, edks) ->
                    Sets.newLinkedHashSet(edks.map { it.entityKeyId })
                }
        lqs.updateLinkingInformation( linkingId, newMember, scoresAsEsidToEkids )
    }

    private fun isLinkingEnabled(): Boolean {
        if (!configuration.backgroundLinkingEnabled) {
            logger.info("Skipping task as background linking is not enabled.")
            return false
        }

        return true
    }

    /**
     * Locks the candidate if possible
     *
     * Returns true if the candidate has been successfully locked, false if already locked
     */
    private fun tryLockCandidate(candidate: EntityDataKey): Boolean {
        return tryLockCandidateRaw(candidate) == null
    }

    private fun tryLockCandidateRaw(candidate: EntityDataKey): Long? {
        return linkingLocks.putIfAbsent(
                candidate,
                Instant.now().plusMillis(LINKING_BATCH_TIMEOUT_MILLIS).toEpochMilli(),
                LINKING_BATCH_TIMEOUT_MILLIS,
                TimeUnit.MILLISECONDS
        )
    }

    private fun unlock(candidate: EntityDataKey) {
        linkingLocks.delete(candidate)
    }

    private fun completeLinkCluster( matchedCluster: Cluster ): Double {
        return matchedCluster.values.flatMap { it.values }.min() ?: 0.0
    }
}
