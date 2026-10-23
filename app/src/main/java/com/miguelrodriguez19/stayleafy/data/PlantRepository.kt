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
import com.miguelrodriguez19.stayleafy.data.backup.DataOperationCoordinator
import kotlinx.coroutines.flow.map
import com.miguelrodriguez19.stayleafy.data.local.database.PlantDao
import com.miguelrodriguez19.stayleafy.data.local.database.PlantEntity
import com.miguelrodriguez19.stayleafy.data.local.database.PlantNoteEntity
import com.miguelrodriguez19.stayleafy.data.local.database.normalizeForPlantSearch
import com.miguelrodriguez19.stayleafy.data.photo.PhotoFileStore
import com.miguelrodriguez19.stayleafy.model.Plant
import com.miguelrodriguez19.stayleafy.model.PlantDraft
import com.miguelrodriguez19.stayleafy.model.PlantListFilter
import com.miguelrodriguez19.stayleafy.model.PlantNoteDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.util.UUID

interface PlantRepository {
    fun observePlants(filter: PlantListFilter): Flow<List<Plant>>

    fun observePlant(id: String): Flow<Plant?>

    suspend fun createPlant(draft: PlantDraft): String

    suspend fun createPlant(
        draft: PlantDraft,
        favorite: Boolean,
        stagedPhotoToken: String?,
        notes: List<PlantNoteDraft>,
    ): String {
        require(stagedPhotoToken == null) { "This repository does not support staged photos" }
        require(notes.isEmpty()) { "This repository does not support initial notes" }
        return createPlant(draft).also { id -> if (favorite) setFavorite(id, true) }
    }

    suspend fun updatePlant(id: String, draft: PlantDraft)

    suspend fun setFavorite(id: String, favorite: Boolean)

    suspend fun deletePlant(id: String)
}

class DefaultPlantRepository @Inject constructor(
    private val plantDao: PlantDao,
    private val epochMillisProvider: EpochMillisProvider,
    private val uuidProvider: UuidProvider,
    private val photoFileStore: PhotoFileStore,
    private val zoneIdProvider: ZoneIdProvider,
    private val operations: DataOperationCoordinator = DataOperationCoordinator(),
) : PlantRepository {

    override fun observePlants(filter: PlantListFilter): Flow<List<Plant>> {
        val todayEpochDay = epochDayAt(
            epochMillis = epochMillisProvider.now(),
            zoneId = zoneIdProvider.currentZoneId(),
        )
        return plantDao.observePlants(
            escapedQuery = escapeLikeQuery(filter.query.trim().normalizeForPlantSearch()),
            favoritesOnly = filter.favoritesOnly,
            upcomingOnly = filter.upcomingOnly,
            overdueOnly = filter.overdueOnly,
            todayEpochDay = todayEpochDay,
            upcomingEndEpochDay = Math.addExact(todayEpochDay, 7L),
        ).map { items -> items.map(PlantEntity::toModel) }
    }

    override fun observePlant(id: String): Flow<Plant?> =
        plantDao.observePlant(id).map { item -> item?.toModel() }

    override suspend fun createPlant(draft: PlantDraft): String =
        createPlant(draft, favorite = false, stagedPhotoToken = null, notes = emptyList())

    override suspend fun createPlant(
        draft: PlantDraft,
        favorite: Boolean,
        stagedPhotoToken: String?,
        notes: List<PlantNoteDraft>,
    ): String = operations.withOperation {
        val normalizedDraft = draft.normalized()
        require(normalizedDraft.name.isNotBlank()) { "name must not be blank" }
        val normalizedNotes = notes.map { note ->
            PlantNoteDraft(note.body.trim().also { require(it.isNotBlank()) { "body must not be blank" } })
        }

        val id = uuidProvider.newUuid()
        val now = epochMillisProvider.now()
        var storedPhotoPath: String? = null
        try {
            storedPhotoPath = stagedPhotoToken?.let { photoFileStore.commitStagedPhoto(it).relativePath }
            val plant = normalizedDraft.toEntity(
                id = id,
                favorite = favorite,
                mainPhotoPath = storedPhotoPath,
                createdAt = now,
                updatedAt = now,
            )
            val noteEntities = normalizedNotes.map { note ->
                val noteNow = epochMillisProvider.now()
                PlantNoteEntity(
                    id = uuidProvider.newUuid(),
                    plantId = id,
                    body = note.body,
                    createdAt = noteNow,
                    updatedAt = noteNow,
                )
            }
            plantDao.insertPlantWithNotes(plant, noteEntities)
            if (stagedPhotoToken != null) {
                withContext(NonCancellable) {
                    runCatching { photoFileStore.discardStagedPhoto(stagedPhotoToken) }
                }
            }
            id
        } catch (error: CancellationException) {
            storedPhotoPath?.let { path ->
                withContext(NonCancellable) { runCatching { photoFileStore.remove(path) } }
            }
            throw error
        } catch (error: Throwable) {
            storedPhotoPath?.let { path ->
                withContext(NonCancellable) { runCatching { photoFileStore.remove(path) } }
            }
            throw error
        }
    }

    override suspend fun updatePlant(id: String, draft: PlantDraft): Unit = operations.withOperation {
        val normalizedDraft = draft.normalized()
        require(normalizedDraft.name.isNotBlank()) { "name must not be blank" }

        plantDao.updatePlant(
            id = id,
            name = normalizedDraft.name,
            nameSearch = normalizedDraft.name.normalizeForPlantSearch(),
            scientificName = normalizedDraft.scientificName,
            scientificNameSearch = normalizedDraft.scientificName.normalizeForPlantSearch(),
            location = normalizedDraft.location,
            acquiredEpochDay = normalizedDraft.acquiredEpochDay,
            description = normalizedDraft.description,
            updatedAt = epochMillisProvider.now(),
        )
    }

    override suspend fun setFavorite(id: String, favorite: Boolean): Unit = operations.withOperation {
        plantDao.setFavorite(id, favorite, epochMillisProvider.now())
    }

    override suspend fun deletePlant(id: String): Unit = operations.withOperation {
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
    name = name.trim(),
    scientificName = scientificName.normalizeNullable(),
    location = location.normalizeNullable(),
    description = description.normalizeNullable(),
)

private fun String?.normalizeNullable(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun PlantDraft.toEntity(
    id: String,
    favorite: Boolean,
    mainPhotoPath: String?,
    createdAt: Long,
    updatedAt: Long,
): PlantEntity =
    PlantEntity(
        id = id,
        name = name,
        nameSearch = name.normalizeForPlantSearch(),
        scientificName = scientificName,
        scientificNameSearch = scientificName.normalizeForPlantSearch(),
        location = location,
        acquiredEpochDay = acquiredEpochDay,
        description = description,
        mainPhotoPath = mainPhotoPath,
        isFavorite = favorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun PlantEntity.toModel(): Plant =
    Plant(
        id = id,
        name = name,
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
