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

package com.openlattice.linking.matching

import com.codahale.metrics.annotation.Timed
import com.openlattice.data.EntityDataKey
import com.openlattice.linking.Matcher
import com.openlattice.linking.util.PersonMetric
import com.openlattice.rhizome.hazelcast.DelegatedStringSet
import org.apache.olingo.commons.api.edm.FullQualifiedName
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.nd4j.linalg.factory.Nd4j
import org.springframework.stereotype.Component
import java.util.*

const val THRESHOLD = 0.9

/**
 * Performs matching using the model generated by Socrates.
 */
@Component
class SocratesMatcher(model: MultiLayerNetwork, private val fqnToIdMap: Map<FullQualifiedName, UUID>) : Matcher {
    private var localModel = ThreadLocal.withInitial { model }

    //            Thread.currentThread().contextClassLoader.getResourceAsStream("model.bin") }

    override fun updateMatchingModel(model: MultiLayerNetwork) {
        localModel = ThreadLocal.withInitial { model }
    }

    @Timed
    override fun initialize(
            block: Pair<EntityDataKey, Map<EntityDataKey, Map<UUID, Set<Any>>>>
    ): Pair<EntityDataKey, MutableMap<EntityDataKey, MutableMap<EntityDataKey, Double>>> {
        val model = localModel.get()

        val entityDataKey = block.first
        val entities = block.second

        val extractedEntities = entities.mapValues { extractProperties(it.value) }

        val matchedEntities = extractedEntities
                .mapValues {
                    model.getModelScore(
                            arrayOf(PersonMetric.pDistance(extractedEntities[entityDataKey], it.value, fqnToIdMap).map { it * 100.0 }.toDoubleArray())
                    )
                }
                .toMutableMap()
        val initializedBlock = entityDataKey to mutableMapOf(entityDataKey to matchedEntities)
        trimAndMerge(initializedBlock)
        return initializedBlock
    }

    /**
     * Computes the pairwise matching values for a block.
     * @param block The resulting block around for the entity data key in block.first
     * @return All pairs of entities in the block scored by the current model.
     */
    @Timed
    override fun match(
            block: Pair<EntityDataKey, Map<EntityDataKey, Map<UUID, Set<Any>>>>
    ): Pair<EntityDataKey, MutableMap<EntityDataKey, MutableMap<EntityDataKey, Double>>> {
        val model = localModel.get()

        val entityDataKey = block.first
        val entities = block.second

        val extractedEntities = entities.mapValues { extractProperties(it.value) }

        val matchedEntities = extractedEntities.mapValues {
            val entity = it.value
            extractedEntities
                    .mapValues {
                        model.getModelScore(arrayOf(PersonMetric.pDistance(entity, it.value, fqnToIdMap).map { it * 100.0 }.toDoubleArray()))
                    }
                    .toMutableMap()
        }.toMutableMap()

        return entityDataKey to matchedEntities
    }

    private fun extractProperties(entity: Map<UUID, Set<Any>>): Map<UUID, DelegatedStringSet> {
        return entity.map { it.key to DelegatedStringSet.wrap(it.value.map { it.toString() }.toSet()) }.toMap()
    }

    @Timed
    override fun trimAndMerge(
            matchedBlock: Pair<EntityDataKey, MutableMap<EntityDataKey, MutableMap<EntityDataKey, Double>>>
    ) {
        //Trim non-center matching thigns.
        matchedBlock.second[matchedBlock.first] = matchedBlock.second[matchedBlock.first]?.filter { it.value > THRESHOLD }?.toMutableMap() ?: mutableMapOf()
    }
}

fun MultiLayerNetwork.getModelScore(features: Array<DoubleArray>): Double {
    return output(Nd4j.create(features)).getDouble(0)
}
