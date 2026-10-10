package com.miguelrodriguez19.stayleafy.data.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.data.local.database.AppDatabase
import com.miguelrodriguez19.stayleafy.data.local.database.PlantDao
import com.miguelrodriguez19.stayleafy.data.local.database.PlantEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class PlantDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: PlantDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.plantDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun crud_insertsReadsUpdatesFavoritesAndDeletesPlant() = runBlocking {
        val inserted = testPlant(
            id = "plant-1",
            displayName = "Monstera",
            commonName = "Swiss cheese plant",
            scientificName = "Monstera deliciosa",
        )

        dao.insertPlant(inserted)
        assertEquals(inserted, dao.observePlant(inserted.id).first())

        dao.updatePlant(
            id = inserted.id,
            displayName = "Updated Monstera",
            displayNameSearch = "updated monstera",
            commonName = "Updated common name",
            commonNameSearch = "updated common name",
            scientificName = "Monstera adansonii",
            scientificNameSearch = "monstera adansonii",
            location = "Kitchen",
            acquiredEpochDay = 20_100L,
            description = "Updated description",
            updatedAt = 3_000L,
        )
        val updated = inserted.copy(
            displayName = "Updated Monstera",
            displayNameSearch = "updated monstera",
            commonName = "Updated common name",
            commonNameSearch = "updated common name",
            scientificName = "Monstera adansonii",
            scientificNameSearch = "monstera adansonii",
            location = "Kitchen",
            acquiredEpochDay = 20_100L,
            description = "Updated description",
            updatedAt = 3_000L,
        )
        assertEquals(updated, dao.observePlant(inserted.id).first())

        dao.setFavorite(inserted.id, favorite = true, updatedAt = 4_000L)
        assertEquals(
            updated.copy(isFavorite = true, updatedAt = 4_000L),
            dao.observePlant(inserted.id).first(),
        )

        dao.deletePlant(inserted.id)
        assertNull(dao.observePlant(inserted.id).first())
    }

    @Test
    fun observePlants_returnsAllRowsInStableCaseInsensitiveDisplayNameOrder() = runBlocking {
        val plants = buildList {
            add(testPlant(id = "z-id", displayName = "fern"))
            add(testPlant(id = "a-id", displayName = "Fern"))
            add(testPlant(id = "banana", displayName = "banana"))
            add(testPlant(id = "aloe", displayName = "Aloe"))
            repeat(128) { index ->
                add(testPlant(id = "bulk-$index", displayName = "Bulk ${index.toString().padStart(3, '0')}"))
            }
        }
        plants.forEach { dao.insertPlant(it) }

        val actual = dao.observePlants(escapedQuery = "", favoritesOnly = false).first()
        val expected = plants.sortedWith(
            compareBy<PlantEntity> { it.displayName.lowercase(Locale.ROOT) }
                .thenBy { it.id },
        )

        assertEquals(plants.size, actual.size)
        assertEquals(expected, actual)
    }

    @Test
    fun observePlants_filtersFavoritesOnly() = runBlocking {
        val favorite = testPlant(id = "favorite", displayName = "Favorite", isFavorite = true)
        val regular = testPlant(id = "regular", displayName = "Regular")
        dao.insertPlant(favorite)
        dao.insertPlant(regular)

        assertEquals(
            listOf(favorite),
            dao.observePlants(escapedQuery = "", favoritesOnly = true).first(),
        )
        assertEquals(
            listOf(favorite, regular),
            dao.observePlants(escapedQuery = "", favoritesOnly = false).first(),
        )
    }

    @Test
    fun observePlants_searchesNamesCaseInsensitively() = runBlocking {
        val displayNameMatch = testPlant(id = "display", displayName = "Monstera Deliciosa")
        val commonNameMatch = testPlant(
            id = "common",
            displayName = "Office plant",
            commonName = "Snake Plant",
        )
        val scientificNameMatch = testPlant(
            id = "scientific",
            displayName = "Rare plant",
            scientificName = "Calathea Orbifolia",
        )
        val noMatch = testPlant(id = "other", displayName = "Cactus")
        listOf(displayNameMatch, commonNameMatch, scientificNameMatch, noMatch)
            .forEach { dao.insertPlant(it) }

        assertEquals(
            listOf(displayNameMatch),
            dao.observePlants(escapedQuery = "mOnStErA", favoritesOnly = false).first(),
        )
        assertEquals(
            listOf(commonNameMatch),
            dao.observePlants(escapedQuery = "sNaKe pLaNt", favoritesOnly = false).first(),
        )
        assertEquals(
            listOf(scientificNameMatch),
            dao.observePlants(escapedQuery = "oRbIfOlIa", favoritesOnly = false).first(),
        )
    }

    @Test
    fun observePlants_searchesAccentedSpanishNamesCaseInsensitively() = runBlocking {
        val displayNameMatch = testPlant(id = "tree", displayName = "ÁRBOL del salón")
        val commonNameMatch = testPlant(
            id = "coleus",
            displayName = "Maceta roja",
            commonName = "CÓLEO",
        )
        val scientificNameMatch = testPlant(
            id = "anthurium",
            displayName = "Planta tropical",
            scientificName = "ANTHÚRIUM andraeanum",
        )
        listOf(displayNameMatch, commonNameMatch, scientificNameMatch)
            .forEach { dao.insertPlant(it) }

        assertEquals(
            listOf(displayNameMatch),
            dao.observePlants(escapedQuery = "árbol", favoritesOnly = false).first(),
        )
        assertEquals(
            listOf(commonNameMatch),
            dao.observePlants(escapedQuery = "cóleo", favoritesOnly = false).first(),
        )
        assertEquals(
            listOf(scientificNameMatch),
            dao.observePlants(escapedQuery = "anthúrium", favoritesOnly = false).first(),
        )
    }

    @Test
    fun observePlants_treatsPercentUnderscoreAndBackslashAsLiteralSearchText() = runBlocking {
        val percent = testPlant(id = "percent", displayName = "Ficus 100% Green")
        val underscore = testPlant(id = "underscore", displayName = "Ficus _Corner")
        val backslash = testPlant(id = "backslash", displayName = "Ficus \\ Shelf")
        val unrelated = testPlant(id = "unrelated", displayName = "Ficus Greenhouse")
        listOf(percent, underscore, backslash, unrelated).forEach { dao.insertPlant(it) }

        assertEquals(
            listOf(percent),
            dao.observePlants(escapedQuery = "\\%", favoritesOnly = false).first(),
        )
        assertEquals(
            listOf(underscore),
            dao.observePlants(escapedQuery = "\\_", favoritesOnly = false).first(),
        )
        assertEquals(
            listOf(backslash),
            dao.observePlants(escapedQuery = "\\\\", favoritesOnly = false).first(),
        )
    }

    @Test
    fun observePlants_blankQuery_returnsEveryPlant() = runBlocking {
        val plants = listOf(
            testPlant(id = "one", displayName = "One"),
            testPlant(id = "two", displayName = "Two"),
        )
        plants.forEach { dao.insertPlant(it) }

        val actual = dao.observePlants(escapedQuery = "", favoritesOnly = false).first()

        assertEquals(plants, actual)
        assertTrue(actual.isNotEmpty())
    }

    @Test
    fun deletePlant_removesOnlyTheRequestedRow() = runBlocking {
        val deleted = testPlant(id = "deleted", displayName = "Deleted")
        val retained = testPlant(id = "retained", displayName = "Retained")
        dao.insertPlant(deleted)
        dao.insertPlant(retained)

        dao.deletePlant(deleted.id)

        assertNull(dao.observePlant(deleted.id).first())
        assertEquals(retained, dao.observePlant(retained.id).first())
    }
}
