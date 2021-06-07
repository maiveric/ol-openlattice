package com.openlattice.datasets

import com.openlattice.authorization.AclKey
import com.openlattice.authorization.Permission
import retrofit2.http.*
import java.util.*

interface DataSetMetadataApi {

    companion object {

        const val SERVICE = "/datastore"
        const val CONTROLLER = "/metadata"
        const val BASE = SERVICE + CONTROLLER

        const val COLUMNS_PATH = "/columns"
        const val DATA_SETS_PATH = "/datasets"
        const val UPDATE_PATH = "/update"

        const val COLUMN_ID_PARAM = "columnId"
        const val COLUMN_ID_PATH = "/{$COLUMN_ID_PARAM}"
        const val DATA_SET_ID_PARAM = "dataSetId"
        const val DATA_SET_ID_PATH = "/{$DATA_SET_ID_PARAM}"
    }

    /**
     * Gets the [DataSet] metadata objects with the given data set ids that the caller has [Permission.READ] on.
     *
     * @param dataSetIds a set of data set ids
     * @return Map<K, V> where K is a data set id and V is a [DataSet] object
     */
    @POST(BASE + DATA_SETS_PATH)
    fun getDataSets(@Body dataSetIds: Set<UUID>): Map<UUID, DataSet>

    /**
     * Gets a dataset using its id
     *
     * @param datasetId The id of the dataset
     *
     * @return The [DataSet] with the specified id
     */
    @GET(BASE + DATA_SETS_PATH + DATA_SET_ID_PATH)
    fun getDataSet(@Path(DATA_SET_ID_PARAM) dataSetId: UUID): DataSet

    /**
     * Gets a dataset column using its id
     *
     * @param datasetId The id of the dataset the column belongs to
     * @param datasetColumnId The id of the column
     *
     * @return The [DataSetColumn] with the aclKey of [datasetId, datasetColumnId]
     */
    @GET(BASE + COLUMNS_PATH + DATA_SET_ID_PATH + COLUMN_ID_PATH)
    fun getDataSetColumn(
        @Path(DATA_SET_ID_PARAM) dataSetId: UUID,
        @Path(COLUMN_ID_PARAM) dataSetColumnId: UUID
    ): DataSetColumn

    /**
     * Gets dataset columns as a map using their aclKeys
     *
     * @param datasetColumnAclKeys The aclKeys of the dataset columns to load
     *
     * @return A map from dataset column [AclKey] to [DataSetColumn]
     */
    @POST(BASE + COLUMNS_PATH)
    fun getDataSetColumns(@Body dataSetColumnAclKeys: Set<AclKey>): Map<AclKey, DataSetColumn>

    /**
     * Gets all columns in the specified set of dataset ids
     *
     * @param datasetIds The ids of the datasets to load columns in
     *
     * @return A map from dataset id to an iterable of all the [DataSetColumn]s in that dataset
     */
    @POST(BASE + DATA_SETS_PATH + COLUMNS_PATH)
    fun getColumnsInDatasets(@Body dataSetIds: Set<UUID>): Map<UUID, Iterable<DataSetColumn>>


    /**
     * Updates metadata for the dataset with id [id]
     *
     * @param id The id of the dataset to update metadata for
     *
     */
    @PATCH(BASE + UPDATE_PATH + DATA_SET_ID_PATH)
    fun updateDataSetMetadata(@Path(DATA_SET_ID_PARAM) dataSetId: UUID, @Body update: SecurableObjectMetadataUpdate)

    /**
     * Updates metadata for the dataset column with aclKey [datasetId, id]
     *
     * @param datasetId The id of the dataset to update metadata for
     * @param id The id of the column in the dataset to update metadata for
     *
     */
    @PATCH(BASE + UPDATE_PATH + DATA_SET_ID_PATH + COLUMN_ID_PATH)
    fun updateDataSetColumnMetadata(
        @Path(DATA_SET_ID_PARAM) dataSetId: UUID,
        @Path(COLUMN_ID_PARAM) columnId: UUID,
        @Body update: SecurableObjectMetadataUpdate
    )
}
