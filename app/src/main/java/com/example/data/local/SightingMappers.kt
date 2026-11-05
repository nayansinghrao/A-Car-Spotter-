package com.example.data.local

import com.example.data.model.Sighting
import com.google.firebase.Timestamp

object SightingMappers {

    private const val CSV_DELIMITER = "|||"

    fun SightingEntity.toDomainModel(): Sighting {
        val paths = if (imagePathsCsv.isBlank()) {
            emptyList()
        } else {
            imagePathsCsv.split(CSV_DELIMITER).filter { it.isNotBlank() }
        }

        return Sighting(
            id = id,
            garageId = garageId,
            createdBy = createdBy,
            creatorName = creatorName,
            lastUpdatedBy = lastUpdatedBy,
            status = status,
            category = category,
            brand = brand,
            model = model,
            numberPlateRaw = numberPlateRaw,
            numberPlateNormalized = numberPlateNormalized,
            plateOcrStatus = plateOcrStatus,
            imagePaths = paths,
            primaryImagePath = primaryImagePath,
            locationName = locationName,
            area = area,
            city = city,
            latitude = latitude,
            longitude = longitude,
            sightingDate = sightingDate,
            sightingTime = sightingTime,
            notes = notes,
            syncStatus = syncStatus,
            createdAt = if (createdAtMillis > 0) Timestamp(createdAtMillis / 1000, ((createdAtMillis % 1000) * 1_000_000).toInt()) else null,
            updatedAt = if (updatedAtMillis > 0) Timestamp(updatedAtMillis / 1000, ((updatedAtMillis % 1000) * 1_000_000).toInt()) else null
        )
    }

    fun Sighting.toEntity(
        syncStatusOverride: String? = null,
        isPendingDelete: Boolean = false,
        lastError: String? = null
    ): SightingEntity {
        val pathsCsv = imagePaths.filter { it.isNotBlank() }.joinToString(CSV_DELIMITER)
        val createdMillis = createdAt?.toDate()?.time ?: System.currentTimeMillis()
        val updatedMillis = updatedAt?.toDate()?.time ?: System.currentTimeMillis()

        return SightingEntity(
            id = id,
            garageId = garageId,
            createdBy = createdBy,
            creatorName = creatorName,
            lastUpdatedBy = lastUpdatedBy,
            status = status,
            category = category,
            brand = brand,
            model = model,
            numberPlateRaw = numberPlateRaw,
            numberPlateNormalized = numberPlateNormalized,
            plateOcrStatus = plateOcrStatus,
            imagePathsCsv = pathsCsv,
            primaryImagePath = primaryImagePath.ifBlank { imagePaths.firstOrNull().orEmpty() },
            locationName = locationName,
            area = area,
            city = city,
            latitude = latitude,
            longitude = longitude,
            sightingDate = sightingDate,
            sightingTime = sightingTime,
            notes = notes,
            syncStatus = syncStatusOverride ?: syncStatus,
            isPendingDelete = isPendingDelete,
            lastSyncError = lastError,
            createdAtMillis = createdMillis,
            updatedAtMillis = updatedMillis
        )
    }
}
