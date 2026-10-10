/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miguelrodriguez19.stayleafy.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.miguelrodriguez19.stayleafy.data.local.database.PlantDao
import com.miguelrodriguez19.stayleafy.data.local.database.PlantEntity
import com.miguelrodriguez19.stayleafy.data.local.database.normalizeForPlantSearch
import com.miguelrodriguez19.stayleafy.data.photo.PhotoFileStore
import com.miguelrodriguez19.stayleafy.model.Plant
import com.miguelrodriguez19.stayleafy.model.PlantDraft
import com.miguelrodriguez19.stayleafy.model.PlantListFilter
import javax.inject.Inject
import java.util.UUID

interface PlantRepository {
    fun observePlants(filter: PlantListFilter): Flow<List<Plant>>

    fun observePlant(id: String): Flow<Plant?>

    suspend fun createPlant(draft: PlantDraft): String

    suspend fun updatePlant(id: String, draft: PlantDraft)

    suspend fun setFavorite(id: String, favorite: Boolean)

    suspend fun deletePlant(id: String)
}

class DefaultPlantRepository @Inject constructor(
    private val plantDao: PlantDao,
    private val epochMillisProvider: EpochMillisProvider,
    private val uuidProvider: UuidProvider,
    private val photoFileStore: PhotoFileStore,
) : PlantRepository {

    override fun observePlants(filter: PlantListFilter): Flow<List<Plant>> =
        plantDao.observePlants(
            escapedQuery = escapeLikeQuery(filter.query.trim().normalizeForPlantSearch()),
            favoritesOnly = filter.favoritesOnly,
        ).map { items -> items.map(PlantEntity::toModel) }

    override fun observePlant(id: String): Flow<Plant?> =
        plantDao.observePlant(id).map { item -> item?.toModel() }

    override suspend fun createPlant(draft: PlantDraft): String {
        val normalizedDraft = draft.normalized()
        require(normalizedDraft.displayName.isNotBlank()) { "displayName must not be blank" }

        val id = uuidProvider.newUuid()
        val now = epochMillisProvider.now()
        plantDao.insertPlant(normalizedDraft.toEntity(id = id, createdAt = now, updatedAt = now))
        return id
    }

    override suspend fun updatePlant(id: String, draft: PlantDraft) {
        val normalizedDraft = draft.normalized()
        require(normalizedDraft.displayName.isNotBlank()) { "displayName must not be blank" }

        plantDao.updatePlant(
            id = id,
            displayName = normalizedDraft.displayName,
            displayNameSearch = normalizedDraft.displayName.normalizeForPlantSearch(),
            commonName = normalizedDraft.commonName,
            commonNameSearch = normalizedDraft.commonName.normalizeForPlantSearch(),
            scientificName = normalizedDraft.scientificName,
            scientificNameSearch = normalizedDraft.scientificName.normalizeForPlantSearch(),
            location = normalizedDraft.location,
            acquiredEpochDay = normalizedDraft.acquiredEpochDay,
            description = normalizedDraft.description,
            updatedAt = epochMillisProvider.now(),
        )
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        plantDao.setFavorite(id, favorite, epochMillisProvider.now())
    }

    override suspend fun deletePlant(id: String) {
        val photoPath = plantDao.deletePlantAndReturnPhotoPath(id)
        if (photoPath != null) {
            runCatching { photoFileStore.remove(photoPath) }
        }
    }
}

interface EpochMillisProvider {
    fun now(): Long
}

class SystemEpochMillisProvider @Inject constructor() : EpochMillisProvider {
    override fun now(): Long = System.currentTimeMillis()
}

interface UuidProvider {
    fun newUuid(): String
}

class RandomUuidProvider @Inject constructor() : UuidProvider {
    override fun newUuid(): String = UUID.randomUUID().toString()
}

private fun PlantDraft.normalized(): PlantDraft = copy(
    displayName = displayName.trim(),
    commonName = commonName.normalizeNullable(),
    scientificName = scientificName.normalizeNullable(),
    location = location.normalizeNullable(),
    description = description.normalizeNullable(),
)

private fun String?.normalizeNullable(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun PlantDraft.toEntity(id: String, createdAt: Long, updatedAt: Long): PlantEntity =
    PlantEntity(
        id = id,
        displayName = displayName,
        displayNameSearch = displayName.normalizeForPlantSearch(),
        commonName = commonName,
        commonNameSearch = commonName.normalizeForPlantSearch(),
        scientificName = scientificName,
        scientificNameSearch = scientificName.normalizeForPlantSearch(),
        location = location,
        acquiredEpochDay = acquiredEpochDay,
        description = description,
        mainPhotoPath = null,
        isFavorite = false,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun PlantEntity.toModel(): Plant =
    Plant(
        id = id,
        displayName = displayName,
        commonName = commonName,
        scientificName = scientificName,
        location = location,
        acquiredEpochDay = acquiredEpochDay,
        description = description,
        mainPhotoPath = mainPhotoPath,
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun escapeLikeQuery(query: String): String = buildString(query.length) {
    query.forEach { character ->
        when (character) {
            '\\', '%', '_' -> append('\\')
        }
        append(character)
    }
}
