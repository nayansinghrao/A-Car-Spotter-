package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing a vehicle sighting locally persisted on device.
 * Serves as the single source of truth for offline-first display and local mutations.
 */
@Entity(tableName = "sightings")
data class SightingEntity(
    @PrimaryKey
    val id: String,
    val garageId: String,
    val createdBy: String,
    val creatorName: String,
    val lastUpdatedBy: String,
    val status: String, // "draft" or "complete"
    val category: String,
    val brand: String,
    val model: String,
    val numberPlateRaw: String,
    val numberPlateNormalized: String,
    val plateOcrStatus: String,
    val imagePathsCsv: String, // Comma-delimited or pipe-delimited list of local/remote photo paths
    val primaryImagePath: String,
    val locationName: String,
    val area: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val sightingDate: String, // YYYY-MM-DD
    val sightingTime: String, // HH:mm
    val notes: String,
    val syncStatus: String = "synced", // "pending", "syncing", "synced", "failed"
    val isPendingDelete: Boolean = false,
    val lastSyncError: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)
