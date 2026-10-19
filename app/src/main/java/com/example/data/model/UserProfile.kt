package com.example.data.model

import com.google.firebase.Timestamp

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val currentGarageId: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
