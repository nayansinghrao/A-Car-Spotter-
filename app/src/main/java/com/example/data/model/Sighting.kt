package com.example.data.model

import com.google.firebase.Timestamp

data class Sighting(
    val id: String = "",
    val garageId: String = "",
    val createdBy: String = "",
    val creatorName: String = "",
    val lastUpdatedBy: String = "",
    val status: String = "complete", // "draft" or "complete"
    val category: String = VehicleCategory.OTHER_CAR.label,
    val brand: String = "",
    val model: String = "",
    val numberPlateRaw: String = "",
    val numberPlateNormalized: String = "",
    val plateOcrStatus: String = "manual", // "manual", "detected_confirmed", "unreadable", "skipped"
    val imagePaths: List<String> = emptyList(),
    val primaryImagePath: String = "",
    val locationName: String = "",
    val area: String = "",
    val city: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val sightingDate: String = "", // YYYY-MM-DD
    val sightingTime: String = "", // HH:mm
    val notes: String = "",
    val syncStatus: String = "synced", // "synced", "syncing", "failed"
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val isDraft: Boolean
        get() = status == "draft"

    val isComplete: Boolean
        get() = status == "complete"

    val isSynced: Boolean
        get() = syncStatus == "synced"

    val isSyncing: Boolean
        get() = syncStatus == "syncing"

    val isSyncFailed: Boolean
        get() = syncStatus == "failed"

    /**
     * Dynamically derives missing information labels from currently saved fields
     * without relying on a static or stale list.
     */
    val missingFields: List<String>
        get() = buildList {
            if (primaryImagePath.isBlank() && imagePaths.isEmpty()) {
                add("Photo missing")
            }
            if (brand.isBlank()) {
                add("Brand needed")
            }
            if (model.isBlank()) {
                add("Model needed")
            }
            if (numberPlateRaw.isBlank() && numberPlateNormalized.isBlank()) {
                add("Plate unconfirmed")
            }
            if (latitude == 0.0 && longitude == 0.0 && locationName.isBlank() && area.isBlank() && city.isBlank()) {
                add("Location missing")
            }
        }
}
