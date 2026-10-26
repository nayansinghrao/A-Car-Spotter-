package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.Garage
import com.example.data.model.JoinRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID

class GarageRepository(context: Context) {

    private val firestore = FirestoreProvider.get(context)
    private val auth = FirebaseAuth.getInstance()

    suspend fun createGarage(garageName: String): Result<Garage> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        return try {
            val garageId = UUID.randomUUID().toString()
            val inviteCode = "SPT-" + UUID.randomUUID().toString().take(4).uppercase(Locale.US)

            val garageData = hashMapOf(
                "id" to garageId,
                "name" to garageName.trim().ifBlank { "My Garage" },
                "ownerUid" to user.uid,
                "ownerEmail" to (user.email ?: ""),
                "memberUids" to listOf(user.uid),
                "inviteCode" to inviteCode,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            // 1. Create garage document
            firestore.collection("garages").document(garageId).set(garageData).await()

            // 2. Create invite entry for inviteCode lookup by prospective members
            val inviteData = hashMapOf(
                "id" to garageId,
                "name" to garageName.trim().ifBlank { "My Garage" },
                "ownerUid" to user.uid,
                "inviteCode" to inviteCode,
                "createdAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("garageInvites").document(inviteCode).set(inviteData).await()

            // 3. Update current user's profile with garage ID (safe upsert with SetOptions.merge)
            firestore.collection("users").document(user.uid)
                .set(
                    mapOf(
                        "uid" to user.uid,
                        "email" to (user.email ?: ""),
                        "displayName" to (user.displayName ?: "Spotter"),
                        "currentGarageId" to garageId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()

            val created = Garage(
                id = garageId,
                name = garageName.trim().ifBlank { "My Garage" },
                ownerUid = user.uid,
                ownerEmail = user.email ?: "",
                memberUids = listOf(user.uid),
                inviteCode = inviteCode
            )
            Result.success(created)
        } catch (e: Exception) {
            Log.e("GarageRepository", "Failed to create garage: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun findGarageByInviteCode(code: String): Garage? {
        val cleanCode = code.trim().uppercase(Locale.US)
        if (cleanCode.isBlank()) return null
        return try {
            val inviteDoc = firestore.collection("garageInvites").document(cleanCode).get().await()
            if (inviteDoc.exists()) {
                val garageId = inviteDoc.getString("id") ?: return null
                val name = inviteDoc.getString("name") ?: "Shared Garage"
                val ownerUid = inviteDoc.getString("ownerUid") ?: ""
                Garage(
                    id = garageId,
                    name = name,
                    ownerUid = ownerUid,
                    ownerEmail = "",
                    memberUids = listOf(ownerUid),
                    inviteCode = cleanCode
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("GarageRepository", "Error finding garage by invite code $cleanCode: ${e.message}")
            null
        }
    }

    suspend fun findUserGarage(uid: String): Garage? {
        return try {
            // 1. First check user's saved currentGarageId directly
            val userDoc = firestore.collection("users").document(uid).get().await()
            val currentGarageId = userDoc.getString("currentGarageId")
            if (!currentGarageId.isNullOrBlank()) {
                val garageDoc = firestore.collection("garages").document(currentGarageId).get().await()
                if (garageDoc.exists()) {
                    val members = (garageDoc.get("memberUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    if (members.contains(uid)) {
                        return Garage(
                            id = garageDoc.getString("id") ?: garageDoc.id,
                            name = garageDoc.getString("name") ?: "My Garage",
                            ownerUid = garageDoc.getString("ownerUid") ?: "",
                            ownerEmail = garageDoc.getString("ownerEmail") ?: "",
                            memberUids = members,
                            inviteCode = garageDoc.getString("inviteCode") ?: "",
                            createdAt = garageDoc.getTimestamp("createdAt"),
                            updatedAt = garageDoc.getTimestamp("updatedAt")
                        )
                    }
                }
            }

            // 2. Fall back to whereArrayContains query
            val query = firestore.collection("garages")
                .whereArrayContains("memberUids", uid)
                .limit(1)
                .get()
                .await()

            val doc = query.documents.firstOrNull() ?: return null
            Garage(
                id = doc.getString("id") ?: doc.id,
                name = doc.getString("name") ?: "My Garage",
                ownerUid = doc.getString("ownerUid") ?: "",
                ownerEmail = doc.getString("ownerEmail") ?: "",
                memberUids = (doc.get("memberUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                inviteCode = doc.getString("inviteCode") ?: "",
                createdAt = doc.getTimestamp("createdAt"),
                updatedAt = doc.getTimestamp("updatedAt")
            )
        } catch (e: Exception) {
            Log.w("GarageRepository", "Error finding user garage: ${e.message}")
            null
        }
    }

    suspend fun requestToJoinGarage(garageId: String, inviteCode: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        return try {
            val requestDoc = firestore.collection("garages")
                .document(garageId)
                .collection("joinRequests")
                .document(user.uid)

            val data = hashMapOf(
                "id" to user.uid,
                "garageId" to garageId,
                "uid" to user.uid,
                "email" to (user.email ?: ""),
                "displayName" to (user.displayName ?: "Spotter"),
                "inviteCodeSubmitted" to inviteCode.trim().uppercase(Locale.US),
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp()
            )

            requestDoc.set(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("GarageRepository", "Failed to submit join request: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun observeGarage(garageId: String): Flow<Garage?> = callbackFlow {
        if (garageId.isBlank() || auth.currentUser == null) {
            trySend(null)
            awaitClose {}
            return@callbackFlow
        }

        val listener: ListenerRegistration = firestore.collection("garages")
            .document(garageId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("GarageRepository", "Error observing garage $garageId: ${error.message}")
                    trySend(null)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val garage = Garage(
                        id = snapshot.getString("id") ?: snapshot.id,
                        name = snapshot.getString("name") ?: "",
                        ownerUid = snapshot.getString("ownerUid") ?: "",
                        ownerEmail = snapshot.getString("ownerEmail") ?: "",
                        memberUids = (snapshot.get("memberUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        inviteCode = snapshot.getString("inviteCode") ?: "",
                        createdAt = snapshot.getTimestamp("createdAt"),
                        updatedAt = snapshot.getTimestamp("updatedAt")
                    )
                    trySend(garage)
                } else {
                    trySend(null)
                }
            }

        awaitClose { listener.remove() }
    }

    fun observeJoinRequests(garageId: String): Flow<List<JoinRequest>> = callbackFlow {
        if (garageId.isBlank() || auth.currentUser == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val listener: ListenerRegistration = firestore.collection("garages")
            .document(garageId)
            .collection("joinRequests")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("GarageRepository", "Error observing join requests: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val requests = snapshot?.documents?.mapNotNull { doc ->
                    JoinRequest(
                        id = doc.getString("id") ?: doc.id,
                        garageId = doc.getString("garageId") ?: garageId,
                        uid = doc.getString("uid") ?: "",
                        email = doc.getString("email") ?: "",
                        displayName = doc.getString("displayName") ?: "",
                        inviteCodeSubmitted = doc.getString("inviteCodeSubmitted") ?: "",
                        status = doc.getString("status") ?: "pending",
                        createdAt = doc.getTimestamp("createdAt")
                    )
                } ?: emptyList()

                trySend(requests)
            }

        awaitClose { listener.remove() }
    }

    fun observeMyJoinRequest(garageId: String, uid: String): Flow<JoinRequest?> = callbackFlow {
        if (garageId.isBlank() || uid.isBlank() || auth.currentUser == null) {
            trySend(null)
            awaitClose {}
            return@callbackFlow
        }

        val listener = firestore.collection("garages")
            .document(garageId)
            .collection("joinRequests")
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val req = JoinRequest(
                        id = snapshot.getString("id") ?: snapshot.id,
                        garageId = snapshot.getString("garageId") ?: garageId,
                        uid = snapshot.getString("uid") ?: uid,
                        email = snapshot.getString("email") ?: "",
                        displayName = snapshot.getString("displayName") ?: "",
                        inviteCodeSubmitted = snapshot.getString("inviteCodeSubmitted") ?: "",
                        status = snapshot.getString("status") ?: "pending",
                        createdAt = snapshot.getTimestamp("createdAt")
                    )
                    trySend(req)
                } else {
                    trySend(null)
                }
            }

        awaitClose { listener.remove() }
    }

    suspend fun checkJoinRequestStatus(garageId: String, uid: String): String? {
        return try {
            val doc = firestore.collection("garages")
                .document(garageId)
                .collection("joinRequests")
                .document(uid)
                .get()
                .await()
            if (doc.exists()) doc.getString("status") else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun approveJoinRequest(garage: Garage, request: JoinRequest): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        if (!garage.isOwner(user.uid)) {
            return Result.failure(IllegalAccessException("Only owner can approve join requests"))
        }
        if (garage.memberUids.size >= 2) {
            return Result.failure(IllegalStateException("Garage already has maximum 2 members"))
        }

        return try {
            val updatedMembers = (garage.memberUids + request.uid).distinct().take(2)
            firestore.collection("garages").document(garage.id)
                .update(
                    mapOf(
                        "memberUids" to updatedMembers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            firestore.collection("garages").document(garage.id)
                .collection("joinRequests").document(request.uid)
                .update("status", "approved")
                .await()

            // Update approved member's profile with active garage ID
            firestore.collection("users").document(request.uid)
                .set(
                    mapOf(
                        "currentGarageId" to garage.id,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("GarageRepository", "Error approving join request: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun rejectJoinRequest(garageId: String, requestUid: String): Result<Unit> {
        return try {
            firestore.collection("garages").document(garageId)
                .collection("joinRequests").document(requestUid)
                .update("status", "rejected")
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
