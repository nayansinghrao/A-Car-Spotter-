package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.Sighting
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SightingRepository(context: Context) {

    private val firestore = FirestoreProvider.get(context)
    private val auth = FirebaseAuth.getInstance()

    private fun requireUserId(): String {
        return auth.currentUser?.uid ?: error("User must be authenticated")
    }

    private fun currentUserName(): String {
        val user = auth.currentUser
        return user?.displayName?.ifBlank { null }
            ?: user?.email?.substringBefore("@")
            ?: "Spotter"
    }

    /**
     * Upserts a sighting record (draft or complete) under the given garage.
     * Preserves the stable ID, original creator, and createdAt timestamp.
     */
    suspend fun upsertSighting(garageId: String, sighting: Sighting): Result<String> {
        val uid = requireUserId()
        return try {
            val sightingId = if (sighting.id.isNotBlank()) sighting.id else UUID.randomUUID().toString()
            val userName = currentUserName()
            val docRef = firestore.collection("garages").document(garageId)
                .collection("sightings").document(sightingId)

            val snapshot = docRef.get().await()

            if (!snapshot.exists()) {
                val data = hashMapOf(
                    "id" to sightingId,
                    "garageId" to garageId,
                    "createdBy" to uid,
                    "creatorName" to userName,
                    "lastUpdatedBy" to userName,
                    "status" to sighting.status,
                    "category" to sighting.category,
                    "brand" to sighting.brand.trim(),
                    "model" to sighting.model.trim(),
                    "numberPlateRaw" to sighting.numberPlateRaw.trim(),
                    "numberPlateNormalized" to sighting.numberPlateNormalized.trim(),
                    "plateOcrStatus" to sighting.plateOcrStatus,
                    "imagePaths" to sighting.imagePaths,
                    "primaryImagePath" to sighting.primaryImagePath,
                    "locationName" to sighting.locationName.trim(),
                    "area" to sighting.area.trim(),
                    "city" to sighting.city.trim(),
                    "latitude" to sighting.latitude,
                    "longitude" to sighting.longitude,
                    "sightingDate" to sighting.sightingDate,
                    "sightingTime" to sighting.sightingTime,
                    "notes" to sighting.notes.trim(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(data).await()
            } else {
                val updateData = hashMapOf(
                    "lastUpdatedBy" to userName,
                    "status" to sighting.status,
                    "category" to sighting.category,
                    "brand" to sighting.brand.trim(),
                    "model" to sighting.model.trim(),
                    "numberPlateRaw" to sighting.numberPlateRaw.trim(),
                    "numberPlateNormalized" to sighting.numberPlateNormalized.trim(),
                    "plateOcrStatus" to sighting.plateOcrStatus,
                    "imagePaths" to sighting.imagePaths,
                    "primaryImagePath" to sighting.primaryImagePath,
                    "locationName" to sighting.locationName.trim(),
                    "area" to sighting.area.trim(),
                    "city" to sighting.city.trim(),
                    "latitude" to sighting.latitude,
                    "longitude" to sighting.longitude,
                    "sightingDate" to sighting.sightingDate,
                    "sightingTime" to sighting.sightingTime,
                    "notes" to sighting.notes.trim(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updateData as Map<String, Any>).await()
            }

            Result.success(sightingId)
        } catch (e: Exception) {
            Log.e("SightingRepository", "Error upserting sighting: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun addSighting(garageId: String, sighting: Sighting): Result<String> {
        return upsertSighting(garageId, sighting)
    }

    suspend fun updateSighting(garageId: String, sighting: Sighting): Result<Unit> {
        return upsertSighting(garageId, sighting).map { }
    }

    suspend fun deleteSighting(garageId: String, sightingId: String): Result<Unit> {
        requireUserId()
        return try {
            firestore.collection("garages").document(garageId)
                .collection("sightings").document(sightingId)
                .delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SightingRepository", "Error deleting sighting: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun observeSightings(garageId: String): Flow<List<Sighting>> = callbackFlow {
        if (garageId.isBlank() || auth.currentUser == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val listener: ListenerRegistration = firestore.collection("garages")
            .document(garageId)
            .collection("sightings")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("SightingRepository", "Error observing sightings: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val sightings = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        Sighting(
                            id = doc.getString("id") ?: doc.id,
                            garageId = doc.getString("garageId") ?: garageId,
                            createdBy = doc.getString("createdBy") ?: "",
                            creatorName = doc.getString("creatorName") ?: "",
                            lastUpdatedBy = doc.getString("lastUpdatedBy") ?: "",
                            status = doc.getString("status") ?: "complete",
                            category = doc.getString("category") ?: "",
                            brand = doc.getString("brand") ?: "",
                            model = doc.getString("model") ?: "",
                            numberPlateRaw = doc.getString("numberPlateRaw") ?: "",
                            numberPlateNormalized = doc.getString("numberPlateNormalized") ?: "",
                            plateOcrStatus = doc.getString("plateOcrStatus") ?: "manual",
                            imagePaths = (doc.get("imagePaths") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                            primaryImagePath = doc.getString("primaryImagePath") ?: "",
                            locationName = doc.getString("locationName") ?: "",
                            area = doc.getString("area") ?: "",
                            city = doc.getString("city") ?: "",
                            latitude = doc.getDouble("latitude") ?: 0.0,
                            longitude = doc.getDouble("longitude") ?: 0.0,
                            sightingDate = doc.getString("sightingDate") ?: "",
                            sightingTime = doc.getString("sightingTime") ?: "",
                            notes = doc.getString("notes") ?: "",
                            createdAt = doc.getTimestamp("createdAt"),
                            updatedAt = doc.getTimestamp("updatedAt")
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                trySend(sightings)
            }

        awaitClose { listener.remove() }
    }
}
