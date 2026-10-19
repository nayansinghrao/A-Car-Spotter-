package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.Garage
import com.example.data.model.JoinRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID

class GarageRepository(context: Context) {

    private val firestore = FirestoreProvider.get(context)
    private val auth = FirebaseAuth.getInstance()

    private fun requireUserId(): String {
        return auth.currentUser?.uid ?: error("User must be authenticated")
    }

    suspend fun createGarage(garageName: String): Result<Garage> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        return try {
            val garageId = UUID.randomUUID().toString()
            val inviteCode = "SPT-" + UUID.randomUUID().toString().take(4).uppercase(Locale.US)

            val garageData = hashMapOf(
                "id" to garageId,
                "name" to garageName.trim(),
                "ownerUid" to user.uid,
                "ownerEmail" to (user.email ?: ""),
                "memberUids" to listOf(user.uid),
                "inviteCode" to inviteCode,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("garages").document(garageId).set(garageData).await()

            // Update user profile with current garage
            firestore.collection("users").document(user.uid)
                .update(
                    mapOf(
                        "currentGarageId" to garageId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            val created = Garage(
                id = garageId,
                name = garageName.trim(),
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
        return try {
            val query = firestore.collection("garages")
                .whereEqualTo("inviteCode", code.trim().uppercase(Locale.US))
                .limit(1)
                .get()
                .await()

            val doc = query.documents.firstOrNull() ?: return null
            Garage(
                id = doc.getString("id") ?: doc.id,
                name = doc.getString("name") ?: "",
                ownerUid = doc.getString("ownerUid") ?: "",
                ownerEmail = doc.getString("ownerEmail") ?: "",
                memberUids = (doc.get("memberUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                inviteCode = doc.getString("inviteCode") ?: "",
                createdAt = doc.getTimestamp("createdAt"),
                updatedAt = doc.getTimestamp("updatedAt")
            )
        } catch (e: Exception) {
            Log.e("GarageRepository", "Error finding garage by code: ${e.message}")
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
        if (garageId.isBlank()) {
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
        if (garageId.isBlank()) {
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

    suspend fun approveJoinRequest(garage: Garage, request: JoinRequest): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        if (!garage.isOwner(user.uid)) {
            return Result.failure(IllegalAccessException("Only owner can approve join requests"))
        }
        if (garage.memberUids.size >= 2) {
            return Result.failure(IllegalStateException("Garage already has 2 members"))
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

            // Update new member's current garage
            firestore.collection("users").document(request.uid)
                .update(
                    mapOf(
                        "currentGarageId" to garage.id,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
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
