package com.miguelrodriguez19.stayleafy.testdi

import com.miguelrodriguez19.stayleafy.data.PlantRepository
import com.miguelrodriguez19.stayleafy.model.Plant
import com.miguelrodriguez19.stayleafy.model.PlantDraft
import com.miguelrodriguez19.stayleafy.model.PlantListFilter
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Small in-memory repository used by instrumented tests.
 *
 * It deliberately exposes failure switches so UI tests can exercise retry and mutation error
 * states without depending on Room or the device's persisted data.
 */
@Singleton
class FakePlantRepository @Inject constructor() : PlantRepository {

    private val plants = MutableStateFlow<List<Plant>>(emptyList())
    private val nextId = AtomicInteger(1)

    @Volatile
    var failPlantListObservation: Boolean = false

    @Volatile
    var failPlantObservation: Boolean = false

    @Volatile
    var failSave: Boolean = false

    @Volatile
    var failFavorite: Boolean = false

    @Volatile
    var failDelete: Boolean = false

    val currentPlants: List<Plant>
        get() = plants.value

    fun replacePlants(items: List<Plant>) {
        plants.value = items
    }

    fun reset() {
        failPlantListObservation = false
        failPlantObservation = false
        failSave = false
        failFavorite = false
        failDelete = false
        plants.value = emptyList()
        nextId.set(1)
    }

    override fun observePlants(filter: PlantListFilter): Flow<List<Plant>> =
        plants.map { items ->
            check(!failPlantListObservation) { "plant list observation failed" }
            val query = filter.query.trim().lowercase(Locale.ROOT)
            items
                .asSequence()
                .filter { item -> !filter.favoritesOnly || item.isFavorite }
                .filter { item ->
                    query.isBlank() || listOf(
                        item.name,
                        item.scientificName,
                    ).any { value -> value?.lowercase(Locale.ROOT)?.contains(query) == true }
                }
                .sortedWith(compareBy<Plant> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })
                .toList()
        }

    override fun observePlant(id: String): Flow<Plant?> =
        plants.map { items ->
            check(!failPlantObservation) { "plant observation failed" }
            items.firstOrNull { it.id == id }
        }

    override suspend fun createPlant(draft: PlantDraft): String {
        check(!failSave) { "save failed" }
        val id = "fake-${nextId.getAndIncrement()}"
        val now = nextId.get().toLong()
        plants.update { items ->
            items + Plant(
                id = id,
                name = draft.name,
                scientificName = draft.scientificName,
                location = draft.location,
                acquiredEpochDay = draft.acquiredEpochDay,
                description = draft.description,
                mainPhotoPath = null,
                isFavorite = false,
                createdAt = now,
                updatedAt = now,
            )
        }
        return id
    }

    override suspend fun updatePlant(id: String, draft: PlantDraft) {
        check(!failSave) { "save failed" }
        plants.update { items ->
            items.map { item ->
                if (item.id != id) {
                    item
                } else {
                    item.copy(
                        name = draft.name,
                        scientificName = draft.scientificName,
                        location = draft.location,
                        acquiredEpochDay = draft.acquiredEpochDay,
                        description = draft.description,
                        updatedAt = item.updatedAt + 1,
                    )
                }
            }
        }
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        check(!failFavorite) { "favorite update failed" }
        plants.update { items ->
            items.map { item -> if (item.id == id) item.copy(isFavorite = favorite) else item }
        }
    }

    override suspend fun deletePlant(id: String) {
        check(!failDelete) { "delete failed" }
        plants.update { items -> items.filterNot { it.id == id } }
    }
}

fun fakePlant(
    id: String,
    name: String,
    scientificName: String? = null,
    location: String? = null,
    acquiredEpochDay: Long? = null,
    description: String? = null,
    isFavorite: Boolean = false,
): Plant = Plant(
    id = id,
    name = name,
    scientificName = scientificName,
    location = location,
    acquiredEpochDay = acquiredEpochDay,
    description = description,
    mainPhotoPath = null,
    isFavorite = isFavorite,
    createdAt = 1L,
    updatedAt = 1L,
)
