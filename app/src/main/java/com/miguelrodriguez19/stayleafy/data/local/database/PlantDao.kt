package com.miguelrodriguez19.stayleafy.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlantDao {
    @Query(
        """
        SELECT * FROM plants
        WHERE (:favoritesOnly = 0 OR isFavorite = 1)
          AND (
            :escapedQuery = ''
            OR displayNameSearch LIKE '%' || :escapedQuery || '%' ESCAPE '\'
            OR commonNameSearch LIKE '%' || :escapedQuery || '%' ESCAPE '\'
            OR scientificNameSearch LIKE '%' || :escapedQuery || '%' ESCAPE '\'
          )
        ORDER BY displayName COLLATE NOCASE ASC, id ASC
        """,
    )
    fun observePlants(escapedQuery: String, favoritesOnly: Boolean): Flow<List<PlantEntity>>

    @Query("SELECT * FROM plants WHERE id = :id")
    fun observePlant(id: String): Flow<PlantEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlant(plant: PlantEntity)

    @Query(
        """
        UPDATE plants
        SET displayName = :displayName,
            displayNameSearch = :displayNameSearch,
            commonName = :commonName,
            commonNameSearch = :commonNameSearch,
            scientificName = :scientificName,
            scientificNameSearch = :scientificNameSearch,
            location = :location,
            acquiredEpochDay = :acquiredEpochDay,
            description = :description,
            updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updatePlant(
        id: String,
        displayName: String,
        displayNameSearch: String,
        commonName: String?,
        commonNameSearch: String,
        scientificName: String?,
        scientificNameSearch: String,
        location: String?,
        acquiredEpochDay: Long?,
        description: String?,
        updatedAt: Long,
    )

    @Query("UPDATE plants SET isFavorite = :favorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, updatedAt: Long)

    @Query("DELETE FROM plants WHERE id = :id")
    suspend fun deletePlant(id: String)
}
