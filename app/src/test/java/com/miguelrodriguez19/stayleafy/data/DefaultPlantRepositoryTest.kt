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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import com.miguelrodriguez19.stayleafy.data.local.database.Plant
import com.miguelrodriguez19.stayleafy.data.local.database.PlantDao

/**
 * Unit tests for [DefaultPlantRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class) // TODO: Remove when stable
class DefaultPlantRepositoryTest {

    @Test
    fun plants_newItemSaved_itemIsReturned() = runTest {
        val repository = DefaultPlantRepository(FakePlantDao())

        repository.add("Repository")

        assertEquals(repository.plants.first().size, 1)
    }

}

private class FakePlantDao : PlantDao {

    private val data = mutableListOf<Plant>()

    override fun getPlants(): Flow<List<Plant>> = flow {
        emit(data)
    }

    override suspend fun insertPlant(item: Plant) {
        data.add(0, item)
    }
}
