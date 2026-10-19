package com.example.data.model

import com.google.firebase.Timestamp

data class Sighting(
    val id: String = "",
    val garageId: String = "",
    val createdBy: String = "",
    val creatorName: String = "",
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
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
