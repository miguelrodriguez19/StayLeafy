package com.miguelrodriguez19.stayleafy.data

import com.miguelrodriguez19.stayleafy.data.local.database.PlantDao
import com.miguelrodriguez19.stayleafy.data.local.database.PlantEntity
import com.miguelrodriguez19.stayleafy.data.local.database.PlantNoteEntity
import com.miguelrodriguez19.stayleafy.data.photo.PendingPhotoCapture
import com.miguelrodriguez19.stayleafy.data.photo.PhotoFileStore
import com.miguelrodriguez19.stayleafy.data.photo.PhotoInputSource
import com.miguelrodriguez19.stayleafy.data.photo.PhotoOrphanReconciliation
import com.miguelrodriguez19.stayleafy.data.photo.StoredPhoto
import java.io.File
import java.time.ZoneId
import java.time.ZoneOffset
import com.miguelrodriguez19.stayleafy.model.PlantDraft
import com.miguelrodriguez19.stayleafy.model.PlantListFilter
import com.miguelrodriguez19.stayleafy.model.PlantNoteDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPlantRepositoryTest {

    private val fixedNow = 1_725_000_000_000L
    private val fixedUuid = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun createPlant_normalizesFieldsAndUsesFixedTimestamps() = runTest {
        val dao = RecordingPlantDao()
        val repository = createRepository(dao)

        val id = repository.createPlant(
            PlantDraft(
                name = "  Ficus Living Room  ",
                scientificName = "  Ficus elastica  ",
                location = "  South window  ",
                acquiredEpochDay = 20_000L,
                description = "  Easy to care for  ",
            ),
        )

        assertEquals(fixedUuid, id)
        assertEquals(
            PlantEntity(
                id = id,
                name = "Ficus Living Room",
                nameSearch = "ficus living room",
                scientificName = "Ficus elastica",
                scientificNameSearch = "ficus elastica",
                location = "South window",
                acquiredEpochDay = 20_000L,
                description = "Easy to care for",
                mainPhotoPath = null,
                isFavorite = false,
                createdAt = fixedNow,
                updatedAt = fixedNow,
            ),
            dao.insertedPlant,
        )
    }

    @Test
    fun createPlant_normalizesBlankOptionalFieldsToNull() = runTest {
        val dao = RecordingPlantDao()
        val repository = createRepository(dao)

        repository.createPlant(
            PlantDraft(
                name = "Ficus",
                scientificName = "\t",
                location = "",
                description = "  ",
            ),
        )

        assertEquals(null, dao.insertedPlant?.scientificName)
        assertEquals(null, dao.insertedPlant?.location)
        assertEquals(null, dao.insertedPlant?.description)
    }

    @Test
    fun createPlant_persistsFavoritePhotoAndInitialNotesInOneDaoCall() = runTest {
        val dao = RecordingPlantDao()
        val photoStore = RecordingPhotoFileStore().apply {
            stagedPhoto = StoredPhoto("plant_photos/atomic.jpg", File("/tmp/atomic.jpg"))
        }
        val repository = createRepository(
            dao = dao,
            photoFileStore = photoStore,
            uuidProvider = SequenceUuidProvider(listOf("plant-atomic", "note-1")),
        )

        val id = repository.createPlant(
            draft = PlantDraft(name = "  Ficus  "),
            favorite = true,
            stagedPhotoToken = "pending-photo",
            notes = listOf(PlantNoteDraft("  New leaf appeared.  ")),
        )

        assertEquals("plant-atomic", id)
        assertEquals(true, dao.insertedPlant?.isFavorite)
        assertEquals("plant_photos/atomic.jpg", dao.insertedPlant?.mainPhotoPath)
        assertEquals(
            listOf(
                PlantNoteEntity(
                    id = "note-1",
                    plantId = "plant-atomic",
                    body = "New leaf appeared.",
                    createdAt = fixedNow,
                    updatedAt = fixedNow,
                ),
            ),
            dao.insertedNotes,
        )
        assertEquals(listOf("pending-photo"), photoStore.committedTokens)
        assertEquals(listOf("pending-photo"), photoStore.discardedTokens)
    }

    @Test
    fun createPlant_blankName_isRejectedBeforeInsert() = runTest {
        val dao = RecordingPlantDao()
        val repository = createRepository(dao)

        val error = runCatching {
            repository.createPlant(PlantDraft(name = "   "))
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("name must not be blank", error?.message)
        assertEquals(null, dao.insertedPlant)
    }

    @Test
    fun updatePlant_normalizesFieldsAndUsesCurrentTimestamp() = runTest {
        val dao = RecordingPlantDao()
        val repository = createRepository(dao)

        repository.updatePlant(
            id = "plant-1",
            draft = PlantDraft(
                name = "  Updated name ",
                scientificName = null,
                location = "  Desk  ",
                acquiredEpochDay = null,
                description = "  New description  ",
            ),
        )

        assertEquals(
            UpdateCall(
                id = "plant-1",
                name = "Updated name",
                nameSearch = "updated name",
                scientificName = null,
                scientificNameSearch = "",
                location = "Desk",
                acquiredEpochDay = null,
                description = "New description",
                updatedAt = fixedNow,
            ),
            dao.updateCall,
        )
    }

    @Test
    fun updatePlant_blankName_isRejectedBeforeUpdate() = runTest {
        val dao = RecordingPlantDao()
        val repository = createRepository(dao)

        val error = runCatching {
            repository.updatePlant("plant-1", PlantDraft(name = "\n\t"))
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(null, dao.updateCall)
    }

    @Test
    fun observePlants_trimsAndEscapesLiteralSearchAndMapsEntities() = runTest {
        val entity = plantEntity(id = "plant-1", name = "Ficus_%\\")
        val dao = RecordingPlantDao(listOf(entity))
        val repository = createRepository(dao)

        val plants = repository.observePlants(
            PlantListFilter(query = "  ficus_%\\  ", favoritesOnly = true),
        ).first()

        assertEquals("ficus\\_\\%\\\\", dao.lastEscapedQuery)
        assertTrue(dao.lastFavoritesOnly)
        assertEquals(entity.toModelForTest(), plants.single())
    }

    @Test
    fun observePlant_mapsDaoEntity() = runTest {
        val entity = plantEntity(id = "plant-2", name = "Monstera")
        val dao = RecordingPlantDao(listOf(entity))
        val repository = createRepository(dao)

        assertEquals(entity.toModelForTest(), repository.observePlant("plant-2").first())
        assertEquals(null, repository.observePlant("missing").first())
    }

    @Test
    fun setFavorite_passesValueAndFixedTimestamp() = runTest {
        val dao = RecordingPlantDao()
        val repository = createRepository(dao)

        repository.setFavorite("plant-1", favorite = true)

        assertEquals(FavoriteCall("plant-1", true, fixedNow), dao.favoriteCall)
    }

    @Test
    fun deletePlant_deletesRequestedId() = runTest {
        val dao = RecordingPlantDao()
        val repository = createRepository(dao)

        repository.deletePlant("plant-1")

        assertEquals("plant-1", dao.deletedId)
    }

    @Test
    fun deletePlant_removesReferencedPhotoAfterDatabaseDelete() = runTest {
        val dao = RecordingPlantDao(
            listOf(plantEntity("plant-1", "Ficus", mainPhotoPath = "plant_photos/ficus.jpg")),
        )
        val photoStore = RecordingPhotoFileStore()
        val repository = createRepository(dao, photoStore)

        repository.deletePlant("plant-1")

        assertEquals(listOf("plant_photos/ficus.jpg"), photoStore.removedPaths)
        assertEquals("plant-1", dao.deletedId)
    }

    @Test
    fun observePlants_normalizesAccentedSpanishCaseBeforeQueryingRoom() = runTest {
        val dao = RecordingPlantDao()
        val repository = createRepository(dao)

        repository.observePlants(PlantListFilter(query = "  ÁRBOL  ")).first()

        assertEquals("árbol", dao.lastEscapedQuery)
    }

    @Test
    fun observePlants_passesCareFiltersAndInjectedLocalDateToRoom() = runTest {
        val dao = RecordingPlantDao()
        val repository = createRepository(dao)

        repository.observePlants(
            PlantListFilter(upcomingOnly = true, overdueOnly = true),
        ).first()

        val today = epochDayAt(fixedNow, ZoneOffset.UTC)
        assertTrue(dao.lastUpcomingOnly)
        assertTrue(dao.lastOverdueOnly)
        assertEquals(today, dao.lastTodayEpochDay)
        assertEquals(today + 7, dao.lastUpcomingEndEpochDay)
    }

    private fun createRepository(
        dao: PlantDao,
        photoFileStore: PhotoFileStore = NoOpPhotoFileStore,
        uuidProvider: UuidProvider = FixedUuidProvider(fixedUuid),
        zoneIdProvider: ZoneIdProvider = FixedPlantZoneIdProvider(ZoneOffset.UTC),
    ) = DefaultPlantRepository(
        plantDao = dao,
        epochMillisProvider = FixedEpochMillisProvider(fixedNow),
        uuidProvider = uuidProvider,
        photoFileStore = photoFileStore,
        zoneIdProvider = zoneIdProvider,
    )
}

private class FixedPlantZoneIdProvider(private val zoneId: ZoneId) : ZoneIdProvider {
    override fun currentZoneId(): ZoneId = zoneId
}

private class FixedEpochMillisProvider(private val value: Long) : EpochMillisProvider {
    override fun now(): Long = value
}

private class FixedUuidProvider(private val value: String) : UuidProvider {
    override fun newUuid(): String = value
}

private class SequenceUuidProvider(values: List<String>) : UuidProvider {
    private val remaining = values.toMutableList()

    override fun newUuid(): String = remaining.removeAt(0)
}

private class RecordingPlantDao(initialPlants: List<PlantEntity> = emptyList()) : PlantDao {
    private val data = MutableStateFlow(initialPlants)

    var insertedPlant: PlantEntity? = null
        private set
    var insertedNotes: List<PlantNoteEntity> = emptyList()
        private set
    var updateCall: UpdateCall? = null
        private set
    var favoriteCall: FavoriteCall? = null
        private set
    var deletedId: String? = null
        private set
    var lastEscapedQuery: String? = null
        private set
    var lastFavoritesOnly: Boolean = false
        private set
    var lastUpcomingOnly: Boolean = false
        private set
    var lastOverdueOnly: Boolean = false
        private set
    var lastTodayEpochDay: Long? = null
        private set
    var lastUpcomingEndEpochDay: Long? = null
        private set

    override fun observePlants(
        escapedQuery: String,
        favoritesOnly: Boolean,
        upcomingOnly: Boolean,
        overdueOnly: Boolean,
        todayEpochDay: Long,
        upcomingEndEpochDay: Long,
    ): Flow<List<PlantEntity>> {
        lastEscapedQuery = escapedQuery
        lastFavoritesOnly = favoritesOnly
        lastUpcomingOnly = upcomingOnly
        lastOverdueOnly = overdueOnly
        lastTodayEpochDay = todayEpochDay
        lastUpcomingEndEpochDay = upcomingEndEpochDay
        return data.map { it.toList() }
    }

    override fun observePlant(id: String): Flow<PlantEntity?> =
        data.map { plants -> plants.firstOrNull { it.id == id } }

    override suspend fun getPlant(id: String): PlantEntity? =
        data.value.firstOrNull { it.id == id }

    override suspend fun insertPlant(plant: PlantEntity) {
        insertedPlant = plant
        data.value = data.value + plant
    }

    override suspend fun insertInitialNotes(notes: List<PlantNoteEntity>) {
        insertedNotes = notes
    }

    override suspend fun updatePlant(
        id: String,
        name: String,
        nameSearch: String,
        scientificName: String?,
        scientificNameSearch: String,
        location: String?,
        acquiredEpochDay: Long?,
        description: String?,
        updatedAt: Long,
    ) {
        updateCall = UpdateCall(
            id = id,
            name = name,
            nameSearch = nameSearch,
            scientificName = scientificName,
            scientificNameSearch = scientificNameSearch,
            location = location,
            acquiredEpochDay = acquiredEpochDay,
            description = description,
            updatedAt = updatedAt,
        )
        data.value = data.value.map { plant ->
            if (plant.id != id) {
                plant
            } else {
                plant.copy(
                    name = name,
                    nameSearch = nameSearch,
                    scientificName = scientificName,
                    scientificNameSearch = scientificNameSearch,
                    location = location,
                    acquiredEpochDay = acquiredEpochDay,
                    description = description,
                    updatedAt = updatedAt,
                )
            }
        }
    }

    override suspend fun setFavorite(id: String, favorite: Boolean, updatedAt: Long) {
        favoriteCall = FavoriteCall(id, favorite, updatedAt)
        data.value = data.value.map { plant ->
            if (plant.id == id) plant.copy(isFavorite = favorite, updatedAt = updatedAt) else plant
        }
    }

    override suspend fun updateMainPhotoPath(
        id: String,
        relativePath: String?,
        updatedAt: Long,
    ): Int {
        val exists = data.value.any { it.id == id }
        data.value = data.value.map { plant ->
            if (plant.id == id) plant.copy(mainPhotoPath = relativePath, updatedAt = updatedAt) else plant
        }
        return if (exists) 1 else 0
    }

    override suspend fun getReferencedPhotoPaths(): List<String> =
        data.value.mapNotNull(PlantEntity::mainPhotoPath)

    override suspend fun deletePlant(id: String) {
        deletedId = id
        data.value = data.value.filterNot { it.id == id }
    }
}

private object NoOpPhotoFileStore : PhotoFileStore {
    override fun prepareCameraCapture(): PendingPhotoCapture = error("unused")
    override suspend fun completeCameraCapture(token: String): StoredPhoto = error("unused")
    override suspend fun discardCameraCapture(token: String): Boolean = false
    override suspend fun importImage(source: PhotoInputSource): StoredPhoto = error("unused")
    override suspend fun remove(relativePath: String): Boolean = true
    override fun resolve(relativePath: String): File? = null
    override suspend fun reconcileOrphans(referencedPaths: Set<String>) =
        PhotoOrphanReconciliation(emptySet(), emptySet())
}

private class RecordingPhotoFileStore : PhotoFileStore {
    val removedPaths = mutableListOf<String>()
    val committedTokens = mutableListOf<String>()
    val discardedTokens = mutableListOf<String>()
    var stagedPhoto = StoredPhoto("plant_photos/staged.jpg", File("/tmp/staged.jpg"))
    override fun prepareCameraCapture(): PendingPhotoCapture = error("unused")
    override suspend fun completeCameraCapture(token: String): StoredPhoto = error("unused")
    override suspend fun discardCameraCapture(token: String): Boolean = false
    override suspend fun importImage(source: PhotoInputSource): StoredPhoto = error("unused")
    override suspend fun commitStagedPhoto(token: String): StoredPhoto {
        committedTokens += token
        return stagedPhoto
    }
    override suspend fun discardStagedPhoto(token: String): Boolean {
        discardedTokens += token
        return true
    }
    override suspend fun remove(relativePath: String): Boolean {
        removedPaths += relativePath
        return true
    }
    override fun resolve(relativePath: String): File? = null
    override suspend fun reconcileOrphans(referencedPaths: Set<String>) =
        PhotoOrphanReconciliation(emptySet(), emptySet())
}

private data class UpdateCall(
    val id: String,
    val name: String,
    val nameSearch: String,
    val scientificName: String?,
    val scientificNameSearch: String,
    val location: String?,
    val acquiredEpochDay: Long?,
    val description: String?,
    val updatedAt: Long,
)

private data class FavoriteCall(val id: String, val favorite: Boolean, val updatedAt: Long)

private fun plantEntity(
    id: String,
    name: String,
    isFavorite: Boolean = false,
    mainPhotoPath: String? = null,
) = PlantEntity(
    id = id,
    name = name,
    nameSearch = name.lowercase(),
    scientificName = "Ficus elastica",
    scientificNameSearch = "ficus elastica",
    location = "Living room",
    acquiredEpochDay = 20_000L,
    description = "Description",
    mainPhotoPath = mainPhotoPath,
    isFavorite = isFavorite,
    createdAt = 1_700_000_000_000L,
    updatedAt = 1_700_000_100_000L,
)

private fun PlantEntity.toModelForTest() = com.miguelrodriguez19.stayleafy.model.Plant(
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
