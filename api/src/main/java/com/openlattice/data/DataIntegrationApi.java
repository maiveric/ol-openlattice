/*
 * Copyright (C) 2018. OpenLattice, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You can contact the owner of the copyright at support@openlattice.com
 */

package com.openlattice.data;

import com.openlattice.data.integration.*;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.openlattice.edm.type.PropertyType;
import retrofit2.http.*;

public interface DataIntegrationApi {
    String ASSOCIATION        = "association";
    String CONTROLLER = "/integration";
    String COUNT            = "count";
    String DETAILED_RESULTS = "detailedResults";
    String EDGES              = "edges";
    String ENTITY_KEY_ID    = "entityKeyId";
    String ENTITY_KEY_IDS     = "entityKeyIds";
    String ENTITY_KEY_ID_PATH    = "{" + ENTITY_KEY_ID + "}";
    /**
     * To discuss paths later; perhaps batch this with EdmApi paths
     */

    String ENTITY_SET         = "set";
    String ENTITY_SET_ID    = "setId";
    String POSTGRES_DATA_SINK = "postgresDataSink";
    String PROPERTY_TYPES     = "propertyTypes";
    String PROPERTY_TYPE_ID = "propertyTypeId";
    String PROPERTY_TYPE_ID_PATH = "{" + PROPERTY_TYPE_ID + "}";
    String S3_DATA_SINK       = "s3DataSink";
    /*
     * These determine the service routing for the LB
     */
    String SERVICE    = "/datastore";
    String BASE       = SERVICE + CONTROLLER;
    String SET_ID_PATH           = "{" + ENTITY_SET_ID + "}";
    String UPDATE           = "update";

    @POST( BASE + "/" + ENTITY_SET + "/" + SET_ID_PATH )
    IntegrationResults integrateEntities(
            @Path( ENTITY_SET_ID ) UUID entitySetId,
            @Query( DETAILED_RESULTS ) boolean detailedResults,
            @Body Map<String, Map<UUID, Set<Object>>> entities );

    @POST( BASE )
    IntegrationResults integrateEntities( @Body Set<EntityData> data );

    /**
     * Creates a new set of associations.
     *
     * @param associations Set of associations to create. An association is the usual (String entityId, SetMultimap &lt;
     * UUID, Object &gt; details of entity) pairing enriched with source/destination EntityKeys
     */
    @POST( BASE + "/" + ASSOCIATION + "/" + SET_ID_PATH )
    IntegrationResults integrateAssociations(
            @Body Set<Association> associations,
            @Query( DETAILED_RESULTS ) boolean detailedResults );

    @POST( BASE )
    IntegrationResults integrateEntityAndAssociationData(
            @Body BulkDataCreation data,
            @Query( DETAILED_RESULTS ) boolean detailedResults );



    @POST( BASE + "/" + S3_DATA_SINK )
    List<String> generatePresignedUrls(
            @Body List<S3EntityData> data
    );

    @POST( BASE + "/" + ENTITY_KEY_IDS )
    Map<UUID, Map<String, UUID>> getEntityKeyIds(
            @Body Set<EntityKey> entityKeys
    );

    @PUT( BASE + "/" + EDGES )
    int createEdges(
            @Body Set<DataEdgeKey> edges
    );


}
