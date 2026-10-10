package com.miguelrodriguez19.stayleafy.data.photo

import com.miguelrodriguez19.stayleafy.data.EpochMillisProvider
import com.miguelrodriguez19.stayleafy.data.local.database.PlantDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomPlantPhotoOwnerPort @Inject constructor(
    private val plantDao: PlantDao,
    private val epochMillisProvider: EpochMillisProvider,
) : PlantPhotoOwnerPort {
    override suspend fun read(plantId: String): PlantPhotoOwner {
        val plant = plantDao.getPlant(plantId)
        return PlantPhotoOwner(
            exists = plant != null,
            relativePath = plant?.mainPhotoPath,
        )
    }

    override suspend fun updateMainPhotoPath(plantId: String, relativePath: String?): Boolean =
        plantDao.updateMainPhotoPath(
            id = plantId,
            relativePath = relativePath,
            updatedAt = epochMillisProvider.now(),
        ) > 0

    override suspend fun referencedPhotoPaths(): Set<String> =
        plantDao.getReferencedPhotoPaths().toSet()
}
