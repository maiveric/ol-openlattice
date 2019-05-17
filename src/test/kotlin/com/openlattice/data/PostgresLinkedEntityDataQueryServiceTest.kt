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

package com.openlattice.data

import com.openlattice.analysis.requests.DateRangeFilter
import com.openlattice.data.storage.*
import com.openlattice.postgres.DataTables
import com.openlattice.postgres.PostgresColumn
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.util.*


/**
 *
 * @author Matthew Tamayo-Rios &lt;matthew@openlattice.com&gt;
 */
class PostgresLinkedEntityDataQueryServiceTest {
    private val logger: Logger = LoggerFactory.getLogger(PostgresLinkedEntityDataQueryServiceTest::class.java)


    @Test
    fun testEntitySetQuery() {
        val entitySetId = UUID.fromString("ed5716db-830b-41b7-9905-24fa82761ace")

        val propertyTypeMap = mapOf(
                Pair(UUID.fromString("c270d705-3616-4abc-b16e-f891e264b784"), "im.PersonNickName"),
                Pair(UUID.fromString("7b038634-a0b4-4ce1-a04f-85d1775937aa"), "nc.PersonSurName"),
                Pair(UUID.fromString("8293b7f3-d89d-44f5-bec2-6397a4c5af8b"), "nc.PersonHairColorText"),
                Pair(UUID.fromString("5260cfbd-bfa4-40c1-ade5-cd83cc9f99b2"), "nc.SubjectIdentification"),
                Pair(UUID.fromString("e9a0b4dc-5298-47c1-8837-20af172379a5"), "nc.PersonGivenName"),
                Pair(UUID.fromString("d0935a7e-efd3-4903-b673-0869ef527dea"), "nc.PersonMiddleName"),
                Pair(UUID.fromString("45aa6695-a7e7-46b6-96bd-782e6aa9ac13"), "publicsafety.mugshot"),
                Pair(UUID.fromString("1e6ff0f0-0545-4368-b878-677823459e57"), "nc.PersonBirthDate"),
                Pair(UUID.fromString("ac37e344-62da-4b50-b608-0618a923a92d"), "nc.PersonEyeColorText"),
                Pair(UUID.fromString("481f59e4-e146-4963-a837-4f4e514df8b7"), "nc.SSN"),
                Pair(UUID.fromString("d9a90e01-9670-46e8-b142-6d0c4871f633"), "j.SentenceRegisterSexOffenderIndicator"),
                Pair(UUID.fromString("d3f3f3de-dc1b-40da-9076-683ddbfeb4d8"), "nc.PersonSuffix"),
                Pair(UUID.fromString("f0a6a588-aee7-49a2-8f8e-e5209731da30"), "nc.PersonHeightMeasure"),
                Pair(UUID.fromString("fa00bfdb-98ec-487a-b62f-f6614e4c921b"), "criminaljustice.persontype"),
                Pair(UUID.fromString("5ea6e8d5-93bb-47cf-b054-9faaeb05fb27"), "person.stateidstate"),
                Pair(UUID.fromString("6ec154f8-a4a1-4df2-8c57-d98cbac1478e"), "nc.PersonSex"),
                Pair(UUID.fromString("cf4598e7-5bbe-49f7-8935-4b1a692f6111"), "nc.PersonBirthPlace"),
                Pair(UUID.fromString("32eba813-7d20-4be1-bc1a-717f99917a5e"), "housing.notes"),
                Pair(UUID.fromString("c7d2c503-651d-483f-8c17-72358bcfc5cc"), "justice.xref"),
                Pair(UUID.fromString("f950d05a-f4f2-451b-8c6d-56e78bba8b42"), "nc.PersonRace"),
                Pair(UUID.fromString("314d2bfd-e50e-4965-b2eb-422742fa265c"), "housing.updatedat"),
                Pair(UUID.fromString("1407ac70-ea63-4879-aca4-6722034f0cda"), "nc.PersonEthnicity")
        )

        val entityKeyIds = sequenceOf(
                "73170000-0000-0000-8000-0000000004a9",
                "4d9b0000-0000-0000-8000-00000000005d"
        )
                .map(UUID::fromString)
                .toSet()
        logger.info(
                "Entity set query: {}",
                selectEntitySetWithPropertyTypes(
                        entitySetId,
                        Optional.of(entityKeyIds),
                        propertyTypeMap,
                        setOf(MetadataOption.LAST_WRITE, MetadataOption.LAST_INDEX),
                        propertyTypeMap.keys.map { it to (it == UUID.fromString("45aa6695-a7e7-46b6-96bd-782e6aa9ac13")) }.toMap()
                )
        )
//        logger.info("Versioned query: {}", selectEntitySetWithPropertyTypes(entitySetId, propertyTypeMap, setOf(MetadataOption.LAST_WRITE, MetadataOption.LAST_INDEX), version))
    }


    @Test
    fun testEntitySetVersionQuery() {
        val entitySetId = UUID.fromString("ed5716db-830b-41b7-9905-24fa82761ace")

        val propertyTypeMap = mapOf(
                Pair(UUID.fromString("c270d705-3616-4abc-b16e-f891e264b784"), "im.PersonNickName"),
                Pair(UUID.fromString("7b038634-a0b4-4ce1-a04f-85d1775937aa"), "nc.PersonSurName"),
                Pair(UUID.fromString("8293b7f3-d89d-44f5-bec2-6397a4c5af8b"), "nc.PersonHairColorText"),
                Pair(UUID.fromString("5260cfbd-bfa4-40c1-ade5-cd83cc9f99b2"), "nc.SubjectIdentification"),
                Pair(UUID.fromString("e9a0b4dc-5298-47c1-8837-20af172379a5"), "nc.PersonGivenName"),
                Pair(UUID.fromString("d0935a7e-efd3-4903-b673-0869ef527dea"), "nc.PersonMiddleName"),
                Pair(UUID.fromString("45aa6695-a7e7-46b6-96bd-782e6aa9ac13"), "publicsafety.mugshot"),
                Pair(UUID.fromString("1e6ff0f0-0545-4368-b878-677823459e57"), "nc.PersonBirthDate"),
                Pair(UUID.fromString("ac37e344-62da-4b50-b608-0618a923a92d"), "nc.PersonEyeColorText"),
                Pair(UUID.fromString("481f59e4-e146-4963-a837-4f4e514df8b7"), "nc.SSN"),
                Pair(UUID.fromString("d9a90e01-9670-46e8-b142-6d0c4871f633"), "j.SentenceRegisterSexOffenderIndicator"),
                Pair(UUID.fromString("d3f3f3de-dc1b-40da-9076-683ddbfeb4d8"), "nc.PersonSuffix"),
                Pair(UUID.fromString("f0a6a588-aee7-49a2-8f8e-e5209731da30"), "nc.PersonHeightMeasure"),
                Pair(UUID.fromString("fa00bfdb-98ec-487a-b62f-f6614e4c921b"), "criminaljustice.persontype"),
                Pair(UUID.fromString("5ea6e8d5-93bb-47cf-b054-9faaeb05fb27"), "person.stateidstate"),
                Pair(UUID.fromString("6ec154f8-a4a1-4df2-8c57-d98cbac1478e"), "nc.PersonSex"),
                Pair(UUID.fromString("cf4598e7-5bbe-49f7-8935-4b1a692f6111"), "nc.PersonBirthPlace"),
                Pair(UUID.fromString("32eba813-7d20-4be1-bc1a-717f99917a5e"), "housing.notes"),
                Pair(UUID.fromString("c7d2c503-651d-483f-8c17-72358bcfc5cc"), "justice.xref"),
                Pair(UUID.fromString("f950d05a-f4f2-451b-8c6d-56e78bba8b42"), "nc.PersonRace"),
                Pair(UUID.fromString("314d2bfd-e50e-4965-b2eb-422742fa265c"), "housing.updatedat"),
                Pair(UUID.fromString("1407ac70-ea63-4879-aca4-6722034f0cda"), "nc.PersonEthnicity")
        );
        val entityKeyIds = sequenceOf(
                "73170000-0000-0000-8000-0000000004a9",
                "4d9b0000-0000-0000-8000-00000000005d"
        )
                .map(UUID::fromString)
                .toSet()
        val version = Instant.now().minusMillis(1382400000).toEpochMilli()
        logger.info(
                "Entity set query: {}",
                selectEntitySetWithPropertyTypesAndVersionSql(
                        mapOf(entitySetId to Optional.of(entityKeyIds)),
                        propertyTypeMap,
                        propertyTypeMap.keys,
                        mapOf(entitySetId to propertyTypeMap.keys),
                        mapOf(),
                        setOf(),
                        version,
                        propertyTypeMap.keys.map { it to (it == UUID.fromString("45aa6695-a7e7-46b6-96bd-782e6aa9ac13")) }.toMap(),
                        false,
                        false
                )
//                selectEntitySetWithPropertyTypesAndVersion(
//                        entitySetId,
//                        Optional.of(entityKeyIds),
//                        propertyTypeMap,
//                        setOf(MetadataOption.LAST_WRITE, MetadataOption.LAST_INDEX),
//                        version,
//                        propertyTypeMap.keys.map { it to (it==UUID.fromString("45aa6695-a7e7-46b6-96bd-782e6aa9ac13")) }.toMap()
//                )
        )
//        logger.info("Versioned query: {}", selectEntitySetWithPropertyTypes(entitySetId, propertyTypeMap, setOf(MetadataOption.LAST_WRITE, MetadataOption.LAST_INDEX), version))
    }

    @Test
    fun testLinkingEntitySetVersionQuery() {
        val entitySetId = UUID.fromString("ed5716db-830b-41b7-9905-24fa82761ace")

        val propertyTypeMap = mapOf(
                Pair(UUID.fromString("c270d705-3616-4abc-b16e-f891e264b784"), DataTables.quote("im.PersonNickName")),
                Pair(UUID.fromString("7b038634-a0b4-4ce1-a04f-85d1775937aa"), DataTables.quote("nc.PersonSurName")),
                Pair(UUID.fromString("8293b7f3-d89d-44f5-bec2-6397a4c5af8b"), DataTables.quote("nc.PersonHairColorText")),
                Pair(UUID.fromString("5260cfbd-bfa4-40c1-ade5-cd83cc9f99b2"), DataTables.quote("nc.SubjectIdentification")),
                Pair(UUID.fromString("e9a0b4dc-5298-47c1-8837-20af172379a5"), DataTables.quote("nc.PersonGivenName")),
                Pair(UUID.fromString("d0935a7e-efd3-4903-b673-0869ef527dea"), DataTables.quote("nc.PersonMiddleName")),
                Pair(UUID.fromString("45aa6695-a7e7-46b6-96bd-782e6aa9ac13"), DataTables.quote("publicsafety.mugshot")),
                Pair(UUID.fromString("1e6ff0f0-0545-4368-b878-677823459e57"), DataTables.quote("nc.PersonBirthDate")),
                Pair(UUID.fromString("ac37e344-62da-4b50-b608-0618a923a92d"), DataTables.quote("nc.PersonEyeColorText")),
                Pair(UUID.fromString("481f59e4-e146-4963-a837-4f4e514df8b7"), DataTables.quote("nc.SSN")),
                Pair(UUID.fromString("d9a90e01-9670-46e8-b142-6d0c4871f633"), DataTables.quote("j.SentenceRegisterSexOffenderIndicator")),
                Pair(UUID.fromString("d3f3f3de-dc1b-40da-9076-683ddbfeb4d8"), DataTables.quote("nc.PersonSuffix")),
                Pair(UUID.fromString("f0a6a588-aee7-49a2-8f8e-e5209731da30"), DataTables.quote("nc.PersonHeightMeasure")),
                Pair(UUID.fromString("fa00bfdb-98ec-487a-b62f-f6614e4c921b"), DataTables.quote("criminaljustice.persontype")),
                Pair(UUID.fromString("5ea6e8d5-93bb-47cf-b054-9faaeb05fb27"), DataTables.quote("person.stateidstate")),
                Pair(UUID.fromString("6ec154f8-a4a1-4df2-8c57-d98cbac1478e"), DataTables.quote("nc.PersonSex")),
                Pair(UUID.fromString("cf4598e7-5bbe-49f7-8935-4b1a692f6111"), DataTables.quote("nc.PersonBirthPlace")),
                Pair(UUID.fromString("32eba813-7d20-4be1-bc1a-717f99917a5e"), DataTables.quote("housing.notes")),
                Pair(UUID.fromString("c7d2c503-651d-483f-8c17-72358bcfc5cc"), DataTables.quote("justice.xref")),
                Pair(UUID.fromString("f950d05a-f4f2-451b-8c6d-56e78bba8b42"), DataTables.quote("nc.PersonRace")),
                Pair(UUID.fromString("314d2bfd-e50e-4965-b2eb-422742fa265c"), DataTables.quote("housing.updatedat")),
                Pair(UUID.fromString("1407ac70-ea63-4879-aca4-6722034f0cda"), DataTables.quote("nc.PersonEthnicity"))
        )
        val entityKeyIds = sequenceOf(
                "73170000-0000-0000-8000-0000000004a9",
                "4d9b0000-0000-0000-8000-00000000005d"
        )
                .map(UUID::fromString)
                .toSet()
        val version = Instant.now().minusMillis(1382400000).toEpochMilli()
        logger.info(
                "Linking entity set query with entity set id: {}",
                selectEntitySetWithPropertyTypesAndVersionSql(
                        mapOf(entitySetId to Optional.of(entityKeyIds)),
                        propertyTypeMap,
                        propertyTypeMap.keys,
                        mapOf(entitySetId to propertyTypeMap.keys),
                        mapOf(),
                        setOf(),
                        version,
                        propertyTypeMap.keys.map { it to (it == UUID.fromString("45aa6695-a7e7-46b6-96bd-782e6aa9ac13")) }.toMap(),
                        true,
                        false
                )
        )

        logger.info(
                "Linking entity set query without entity set id: {}",
                selectEntitySetWithPropertyTypesAndVersionSql(
                        mapOf(entitySetId to Optional.of(entityKeyIds)),
                        propertyTypeMap,
                        propertyTypeMap.keys,
                        mapOf(entitySetId to propertyTypeMap.keys),
                        mapOf(),
                        setOf(),
                        version,
                        propertyTypeMap.keys.map { it to (it == UUID.fromString("45aa6695-a7e7-46b6-96bd-782e6aa9ac13")) }.toMap(),
                        true,
                        true
                )
        )
    }

    @Test
    fun testEmptySelectEntitySets() {
        logger.info(selectEntitySetWithCurrentVersionOfPropertyTypes(UUID.randomUUID(), mapOf(), mapOf(), listOf(), mapOf(), mapOf(), setOf(), mapOf(), false, false))
    }


    @Test
    fun testEntitySetQueryUnbound() {
        val entitySetId = UUID.fromString("ed5716db-830b-41b7-9905-24fa82761ace")

        val propertyTypeMap = mapOf(
                Pair(UUID.fromString("c270d705-3616-4abc-b16e-f891e264b784"), DataTables.quote("im.PersonNickName")),
                Pair(UUID.fromString("7b038634-a0b4-4ce1-a04f-85d1775937aa"), DataTables.quote("nc.PersonSurName")),
                Pair(UUID.fromString("8293b7f3-d89d-44f5-bec2-6397a4c5af8b"), DataTables.quote("nc.PersonHairColorText")),
                Pair(UUID.fromString("5260cfbd-bfa4-40c1-ade5-cd83cc9f99b2"), DataTables.quote("nc.SubjectIdentification")),
                Pair(UUID.fromString("e9a0b4dc-5298-47c1-8837-20af172379a5"), DataTables.quote("nc.PersonGivenName")),
                Pair(UUID.fromString("d0935a7e-efd3-4903-b673-0869ef527dea"), DataTables.quote("nc.PersonMiddleName")),
                Pair(UUID.fromString("45aa6695-a7e7-46b6-96bd-782e6aa9ac13"), DataTables.quote("publicsafety.mugshot")),
                Pair(UUID.fromString("1e6ff0f0-0545-4368-b878-677823459e57"), DataTables.quote("nc.PersonBirthDate")),
                Pair(UUID.fromString("ac37e344-62da-4b50-b608-0618a923a92d"), DataTables.quote("nc.PersonEyeColorText")),
                Pair(UUID.fromString("481f59e4-e146-4963-a837-4f4e514df8b7"), DataTables.quote("nc.SSN")),
                Pair(UUID.fromString("d9a90e01-9670-46e8-b142-6d0c4871f633"), DataTables.quote("j.SentenceRegisterSexOffenderIndicator")),
                Pair(UUID.fromString("d3f3f3de-dc1b-40da-9076-683ddbfeb4d8"), DataTables.quote("nc.PersonSuffix")),
                Pair(UUID.fromString("f0a6a588-aee7-49a2-8f8e-e5209731da30"), DataTables.quote("nc.PersonHeightMeasure")),
                Pair(UUID.fromString("fa00bfdb-98ec-487a-b62f-f6614e4c921b"), DataTables.quote("criminaljustice.persontype")),
                Pair(UUID.fromString("5ea6e8d5-93bb-47cf-b054-9faaeb05fb27"), DataTables.quote("person.stateidstate")),
                Pair(UUID.fromString("6ec154f8-a4a1-4df2-8c57-d98cbac1478e"), DataTables.quote("nc.PersonSex")),
                Pair(UUID.fromString("cf4598e7-5bbe-49f7-8935-4b1a692f6111"), DataTables.quote("nc.PersonBirthPlace")),
                Pair(UUID.fromString("32eba813-7d20-4be1-bc1a-717f99917a5e"), DataTables.quote("housing.notes")),
                Pair(UUID.fromString("c7d2c503-651d-483f-8c17-72358bcfc5cc"), DataTables.quote("justice.xref")),
                Pair(UUID.fromString("f950d05a-f4f2-451b-8c6d-56e78bba8b42"), DataTables.quote("nc.PersonRace")),
                Pair(UUID.fromString("314d2bfd-e50e-4965-b2eb-422742fa265c"), DataTables.quote("housing.updatedat")),
                Pair(UUID.fromString("1407ac70-ea63-4879-aca4-6722034f0cda"), DataTables.quote("nc.PersonEthnicity"))
        )
        val entityKeyIds = sequenceOf(
                "73170000-0000-0000-8000-0000000004a9",
                "4d9b0000-0000-0000-8000-00000000005d"
        )
                .map(UUID::fromString)
                .toSet()
        logger.info(
                "Entity set query:\n {}",
                selectEntitySetWithCurrentVersionOfPropertyTypes(
                        UUID.randomUUID(),
                        mapOf(entitySetId to Optional.empty()),
                        propertyTypeMap,
                        propertyTypeMap.keys,
                        mapOf(entitySetId to propertyTypeMap.keys),
                        mapOf(),
                        setOf(MetadataOption.LAST_WRITE, MetadataOption.LAST_INDEX),
                        propertyTypeMap.keys.map { it to (it == UUID.fromString("45aa6695-a7e7-46b6-96bd-782e6aa9ac13")) }.toMap(),
                        false,
                        false
                )
        )
//        logger.info("Versioned query: {}", selectEntitySetWithPropertyTypes(entitySetId, propertyTypeMap, setOf(MetadataOption.LAST_WRITE, MetadataOption.LAST_INDEX), version))
    }

    @Test
    fun testEntitySetQueryWithNeighbourFilters() {
        val entitySetId = UUID.fromString("ed5716db-830b-41b7-9905-24fa82761ace")

        val propertyTypeMap = mapOf(
                Pair(UUID.fromString("c270d705-3616-4abc-b16e-f891e264b784"), DataTables.quote("im.PersonNickName")),
                Pair(UUID.fromString("7b038634-a0b4-4ce1-a04f-85d1775937aa"), DataTables.quote("nc.PersonSurName")),
                Pair(UUID.fromString("8293b7f3-d89d-44f5-bec2-6397a4c5af8b"), DataTables.quote("nc.PersonHairColorText")),
                Pair(UUID.fromString("5260cfbd-bfa4-40c1-ade5-cd83cc9f99b2"), DataTables.quote("nc.SubjectIdentification")),
                Pair(UUID.fromString("e9a0b4dc-5298-47c1-8837-20af172379a5"), DataTables.quote("nc.PersonGivenName")),
                Pair(UUID.fromString("d0935a7e-efd3-4903-b673-0869ef527dea"), DataTables.quote("nc.PersonMiddleName")),
                Pair(UUID.fromString("45aa6695-a7e7-46b6-96bd-782e6aa9ac13"), DataTables.quote("publicsafety.mugshot")),
                Pair(UUID.fromString("1e6ff0f0-0545-4368-b878-677823459e57"), DataTables.quote("nc.PersonBirthDate")),
                Pair(UUID.fromString("ac37e344-62da-4b50-b608-0618a923a92d"), DataTables.quote("nc.PersonEyeColorText")),
                Pair(UUID.fromString("481f59e4-e146-4963-a837-4f4e514df8b7"), DataTables.quote("nc.SSN")),
                Pair(UUID.fromString("d9a90e01-9670-46e8-b142-6d0c4871f633"), DataTables.quote("j.SentenceRegisterSexOffenderIndicator")),
                Pair(UUID.fromString("d3f3f3de-dc1b-40da-9076-683ddbfeb4d8"), DataTables.quote("nc.PersonSuffix")),
                Pair(UUID.fromString("f0a6a588-aee7-49a2-8f8e-e5209731da30"), DataTables.quote("nc.PersonHeightMeasure")),
                Pair(UUID.fromString("fa00bfdb-98ec-487a-b62f-f6614e4c921b"), DataTables.quote("criminaljustice.persontype")),
                Pair(UUID.fromString("5ea6e8d5-93bb-47cf-b054-9faaeb05fb27"), DataTables.quote("person.stateidstate")),
                Pair(UUID.fromString("6ec154f8-a4a1-4df2-8c57-d98cbac1478e"), DataTables.quote("nc.PersonSex")),
                Pair(UUID.fromString("cf4598e7-5bbe-49f7-8935-4b1a692f6111"), DataTables.quote("nc.PersonBirthPlace")),
                Pair(UUID.fromString("32eba813-7d20-4be1-bc1a-717f99917a5e"), DataTables.quote("housing.notes")),
                Pair(UUID.fromString("c7d2c503-651d-483f-8c17-72358bcfc5cc"), DataTables.quote("justice.xref")),
                Pair(UUID.fromString("f950d05a-f4f2-451b-8c6d-56e78bba8b42"), DataTables.quote("nc.PersonRace")),
                Pair(UUID.fromString("314d2bfd-e50e-4965-b2eb-422742fa265c"), DataTables.quote("housing.updatedat")),
                Pair(UUID.fromString("1407ac70-ea63-4879-aca4-6722034f0cda"), DataTables.quote("nc.PersonEthnicity"))
        )

        val query = selectEntitySetWithCurrentVersionOfPropertyTypes(
                UUID.randomUUID(),
                mapOf(entitySetId to Optional.empty()),
                propertyTypeMap,
                propertyTypeMap.keys,
                mapOf(entitySetId to propertyTypeMap.keys),
                mapOf(propertyTypeMap.keys.iterator().next() to
                        setOf(DateRangeFilter(
                                Optional.of(LocalDate.of(2000, 1, 1)),
                                Optional.of(true),
                                Optional.of(LocalDate.of(2000, 12, 31)),
                                Optional.of(true)))),
                setOf(MetadataOption.LAST_WRITE, MetadataOption.LAST_INDEX),
                propertyTypeMap.keys.map { it to (it == UUID.fromString("45aa6695-a7e7-46b6-96bd-782e6aa9ac13")) }.toMap(),
                false,
                false
        )

        logger.info(query)
    }

    @Test
    fun testPropertyTypeQuery2() {
        val entitySetId = UUID.fromString("ed5716db-830b-41b7-9905-24fa82761ace")
        val propertyTypeId = UUID.fromString("e9a0b4dc-5298-47c1-8837-20af172379a5")
        val fqn = "nc.PersonGivenName"
        val version = 1528102624987
        logger.info(
                "SQL Query: {}",
                selectVersionOfPropertyTypeInEntitySet(
                        entitySetId, " ", propertyTypeId, fqn, version, false
                )
        )
    }


    @Test
    fun testPropertyTypeQuery() {
        val entitySetId = UUID.fromString("2fc1aefc-ceb8-4834-a9fd-b203d382394c")
        val entityKeyId = UUID.randomUUID()
        val propertyTypeId = UUID.fromString("00e11d1a-5bd7-42bd-89a5-a452d4f6337e")
        val fqn = "publicsafety.assignedofficer"
        val version = 1525826581101L
        logger.info(
                "SQL Query: {}",
                selectVersionOfPropertyTypeInEntitySet(
                        entitySetId, entityKeyIdsClause(setOf(entityKeyId)), propertyTypeId, fqn, version, false
                )
        )
    }

    @Test
    fun testLinkingEntitySetQuery() {
        val entitySetId = UUID.fromString("ed5716db-830b-41b7-9905-24fa82761ace")

        val propertyTypeMap = mapOf(
                Pair(UUID.fromString("7b038634-a0b4-4ce1-a04f-85d1775937aa"), DataTables.quote("nc.PersonSurName")),
                Pair(UUID.fromString("e9a0b4dc-5298-47c1-8837-20af172379a5"), DataTables.quote("nc.PersonGivenName"))
        );
        val entityKeyIds = sequenceOf(
                "73170000-0000-0000-8000-0000000004a9",
                "4d9b0000-0000-0000-8000-00000000005d"
        )
                .map(UUID::fromString)
                .toSet()
        logger.info(
                "Linking entity set query with entity set id: \n{}",
                selectEntitySetWithCurrentVersionOfPropertyTypes(
                        UUID.randomUUID(),
                        mapOf(entitySetId to Optional.of(entityKeyIds)),
                        propertyTypeMap,
                        propertyTypeMap.keys,
                        mapOf(entitySetId to propertyTypeMap.keys),
                        mapOf(),
                        setOf(MetadataOption.LAST_WRITE, MetadataOption.LAST_INDEX),
                        propertyTypeMap.map { it.key to false }.toMap(),
                        true,
                        false
                )
        )

        logger.info(
                "Linking entity set query without entity set id: \n{}",
                selectEntitySetWithCurrentVersionOfPropertyTypes(
                        UUID.randomUUID(),
                        mapOf(entitySetId to Optional.of(entityKeyIds)),
                        propertyTypeMap,
                        propertyTypeMap.keys,
                        mapOf(entitySetId to propertyTypeMap.keys),
                        mapOf(),
                        setOf(MetadataOption.LAST_WRITE, MetadataOption.LAST_INDEX),
                        propertyTypeMap.map { it.key to false }.toMap(),
                        true,
                        true
                )
        )
    }

    @Test
    fun testLinkingEntitesClauseQuery() {
        val linkingIds = setOf(
                UUID.fromString("c270d705-3616-4abc-b16e-f891e264b784"),
                UUID.fromString("7b038634-a0b4-4ce1-a04f-85d1775937aa"),
                UUID.fromString("8293b7f3-d89d-44f5-bec2-6397a4c5af8b"),
                UUID.fromString("5260cfbd-bfa4-40c1-ade5-cd83cc9f99b2"),
                UUID.fromString("e9a0b4dc-5298-47c1-8837-20af172379a5"),
                UUID.fromString("d0935a7e-efd3-4903-b673-0869ef527dea"),
                UUID.fromString("45aa6695-a7e7-46b6-96bd-782e6aa9ac13"),
                UUID.fromString("1e6ff0f0-0545-4368-b878-677823459e57"))

        logger.info(
                "SQL Query: \n {}",
                selectEntityKeyIdsByLinkingIds(linkingIds)
        )
    }

    @Test
    fun testBuildEntitiesClause() {
        val entityKeyIds = Optional.of(setOf(
                UUID.fromString("c270d705-3616-4abc-b16e-f891e264b784"),
                UUID.fromString("7b038634-a0b4-4ce1-a04f-85d1775937aa"),
                UUID.fromString("8293b7f3-d89d-44f5-bec2-6397a4c5af8b"),
                UUID.fromString("5260cfbd-bfa4-40c1-ade5-cd83cc9f99b2"),
                UUID.fromString("e9a0b4dc-5298-47c1-8837-20af172379a5"),
                UUID.fromString("d0935a7e-efd3-4903-b673-0869ef527dea"),
                UUID.fromString("45aa6695-a7e7-46b6-96bd-782e6aa9ac13"),
                UUID.fromString("1e6ff0f0-0545-4368-b878-677823459e57")))

        logger.info(
                "SQL Query: \n {}",
                buildEntitiesClause(
                        mapOf(UUID.fromString("1e6ff0f0-0545-4368-b878-677823459e57") to entityKeyIds),
                        false))

        logger.info(
                "SQL Query: \n {}",
                buildEntitiesClause(
                        mapOf(UUID.fromString("1e6ff0f0-0545-4368-b878-677823459e57") to Optional.of(setOf())),
                        false))
    }
}
