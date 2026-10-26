package com.example.data.model

import com.google.firebase.Timestamp

data class Garage(
    val id: String = "",
    val name: String = "",
    val ownerUid: String = "",
    val ownerEmail: String = "",
    val memberUids: List<String> = emptyList(),
    val inviteCode: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val isFull: Boolean
        get() = memberUids.size >= 2

    val memberCount: Int
        get() = memberUids.size

    fun isOwner(uid: String): Boolean = ownerUid == uid
    fun isMember(uid: String): Boolean = memberUids.contains(uid)
}

data class JoinRequest(
    val id: String = "",
    val garageId: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val inviteCodeSubmitted: String = "",
    val status: String = "pending", // "pending", "approved", "rejected"
    val createdAt: Timestamp? = null
)
