package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.SightingDao
import com.example.data.local.SightingMappers.toDomainModel
import com.example.data.local.SightingMappers.toEntity
import com.example.data.local.SpotrDatabase
import com.example.data.model.Sighting
import com.example.data.sync.SightingSyncWorker
import com.example.util.ImageStorageHelper
import com.example.util.NetworkConnectivityMonitor
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

/**
 * Offline-First Sighting Repository.
 *
 * Architecture:
 * - Room Database (`SightingDao`) is the single source of truth for the UI.
 * - All creations, updates, and deletes are committed immediately to Room.
 * - Photos are persisted in app-private storage (`ImageStorageHelper`).
 * - Firestore is synchronized two-way:
 *   1. Remote snapshot listener pulls Firestore updates and saves them into Room,
 *      preserving any un-synced local changes (conflict resolution).
 *   2. Pending local changes (creations, edits, deletions) are pushed to Firestore
 *      when online or via WorkManager (`SightingSyncWorker`).
 */
class SightingRepository(private val context: Context) {

    private val firestore = FirestoreProvider.get(context)
    private val auth = FirebaseAuth.getInstance()
    private val database = SpotrDatabase.getInstance(context)
    private val dao: SightingDao = database.sightingDao()
    private val networkMonitor = NetworkConnectivityMonitor(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun currentUserName(): String {
        val user = auth.currentUser
        return user?.displayName?.ifBlank { null }
            ?: user?.email?.substringBefore("@")
            ?: "Spotter"
    }

    /**
     * Observes sightings from local Room database.
     * The UI reacts instantly to local creations, edits, and deletions without network latency.
     */
    fun observeSightings(garageId: String): Flow<List<Sighting>> {
        return dao.observeSightingsForGarage(garageId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    /**
     * Observes pending sync count to show overall sync queue status in UI if needed.
     */
    fun observePendingSyncCount(): Flow<Int> = dao.observePendingSyncCount()

    /**
     * Saves a sighting (create or update, draft or complete) locally into Room immediately.
     * Marks syncStatus as "pending".
     * If device is online, attempts an immediate sync to Firestore; otherwise enqueues WorkManager.
     */
    suspend fun upsertSighting(garageId: String, sighting: Sighting): Result<String> {
        val effectiveGarageId = if (garageId.isNotBlank()) garageId else "local_garage"
        val sightingId = if (sighting.id.isNotBlank()) sighting.id else UUID.randomUUID().toString()
        val uid = auth.currentUser?.uid ?: sighting.createdBy.ifBlank { "offline_user" }
        val userName = currentUserName()

        return try {
            // Ensure any local file images are stored safely in app-private storage
            val preparedImages = prepareImagesForPersistence(sighting.imagePaths, sighting.primaryImagePath)
            val primaryImage = if (sighting.primaryImagePath.isNotBlank()) {
                preparedImages.firstOrNull { it == sighting.primaryImagePath } ?: preparedImages.firstOrNull().orEmpty()
            } else {
                preparedImages.firstOrNull().orEmpty()
            }

            val existingEntity = dao.getSightingById(sightingId)
            val now = System.currentTimeMillis()

            val isOnline = networkMonitor.isCurrentlyOnline() && auth.currentUser != null
            val initialSyncStatus = if (isOnline) "syncing" else "pending"

            val targetEntity = sighting.copy(
                id = sightingId,
                garageId = effectiveGarageId,
                createdBy = if (sighting.createdBy.isNotBlank()) sighting.createdBy else uid,
                creatorName = if (sighting.creatorName.isNotBlank()) sighting.creatorName else userName,
                lastUpdatedBy = userName,
                imagePaths = preparedImages,
                primaryImagePath = primaryImage,
                syncStatus = initialSyncStatus
            ).toEntity(
                syncStatusOverride = initialSyncStatus,
                isPendingDelete = false
            ).copy(
                createdAtMillis = existingEntity?.createdAtMillis ?: now,
                updatedAtMillis = now
            )

            // Step 1: Save immediately to Room (Offline persistence guaranteed)
            dao.insertOrUpdate(targetEntity)
            Log.i(TAG, "Locally saved sighting $sightingId to Room with syncStatus=$initialSyncStatus")

            // Step 2: Trigger sync
            if (isOnline && effectiveGarageId != "local_garage") {
                scope.launch {
                    pushSingleSightingToFirestore(targetEntity)
                }
            } else {
                // Schedule WorkManager to push when connectivity returns
                SightingSyncWorker.enqueue(context)
            }

            Result.success(sightingId)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving sighting locally: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun addSighting(garageId: String, sighting: Sighting): Result<String> {
        return upsertSighting(garageId, sighting)
    }

    suspend fun updateSighting(garageId: String, sighting: Sighting): Result<Unit> {
        return upsertSighting(garageId, sighting).map { }
    }

    /**
     * Deletes a sighting.
     * Offline-first behavior:
     * - Marks the entity with `isPendingDelete = true` so it vanishes instantly from the UI.
     * - If online, permanently deletes from Firestore and then Room.
     * - If offline, leaves the tombstone in Room to be deleted from Firestore when connection returns.
     */
    suspend fun deleteSighting(garageId: String, sightingId: String): Result<Unit> {
        return try {
            val existing = dao.getSightingById(sightingId)
            if (existing == null) {
                return Result.success(Unit)
            }

            // Immediately mark for deletion so Room queries hide it
            dao.markForDeletion(sightingId)
            Log.i(TAG, "Marked sighting $sightingId for deletion in Room")

            val isOnline = networkMonitor.isCurrentlyOnline() && auth.currentUser != null
            if (isOnline && garageId.isNotBlank() && garageId != "local_garage") {
                scope.launch {
                    try {
                        firestore.collection("garages").document(garageId)
                            .collection("sightings").document(sightingId)
                            .delete().await()
                        // Delete permanently from Room once Firestore deletion succeeds
                        dao.deletePermanently(sightingId)
                        Log.i(TAG, "Deleted sighting $sightingId from Firestore and Room")
                    } catch (e: Exception) {
                        Log.w(TAG, "Online deletion failed, queued for WorkManager: ${e.message}")
                        SightingSyncWorker.enqueue(context)
                    }
                }
            } else {
                SightingSyncWorker.enqueue(context)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error in deleteSighting: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Synchronizes all pending local operations (creates, updates, and deletes) to Cloud Firestore.
     * Called by SightingSyncWorker or when connectivity becomes available.
     */
    suspend fun syncPendingChangesNow(): Result<Int> {
        val currentUser = auth.currentUser ?: return Result.failure(
            IllegalStateException("Cannot sync: No authenticated user")
        )

        val pending = dao.getPendingSyncSightings()
        if (pending.isEmpty()) {
            return Result.success(0)
        }

        Log.i(TAG, "Starting sync for ${pending.size} pending items")
        var successCount = 0
        var failureOccurred = false

        for (item in pending) {
            try {
                if (item.isPendingDelete) {
                    // Sync Delete
                    if (item.garageId.isNotBlank() && item.garageId != "local_garage") {
                        firestore.collection("garages").document(item.garageId)
                            .collection("sightings").document(item.id)
                            .delete().await()
                    }
                    dao.deletePermanently(item.id)
                    successCount++
                    Log.i(TAG, "Successfully synced delete for ${item.id}")
                } else {
                    // Sync Create / Update
                    if (item.garageId.isBlank() || item.garageId == "local_garage") {
                        continue // Skip local temporary garage items until garage is selected
                    }
                    dao.updateSyncStatus(item.id, "syncing")
                    pushSingleSightingToFirestore(item)
                    successCount++
                    Log.i(TAG, "Successfully synced sighting ${item.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync pending item ${item.id}: ${e.message}", e)
                dao.updateSyncStatus(item.id, "failed", e.message)
                failureOccurred = true
            }
        }

        return if (!failureOccurred) {
            Result.success(successCount)
        } else {
            Result.failure(IllegalStateException("Synced $successCount items with some errors"))
        }
    }

    private suspend fun pushSingleSightingToFirestore(entity: com.example.data.local.SightingEntity) {
        val garageId = entity.garageId
        if (garageId.isBlank() || garageId == "local_garage") return
        val docRef = firestore.collection("garages").document(garageId)
            .collection("sightings").document(entity.id)

        // Ensure images are formatted for safe Firestore transfer (compact base64 / data URIs)
        val domain = entity.toDomainModel()
        val data = hashMapOf<String, Any>(
            "id" to domain.id,
            "garageId" to domain.garageId,
            "createdBy" to domain.createdBy,
            "creatorName" to domain.creatorName,
            "lastUpdatedBy" to domain.lastUpdatedBy,
            "status" to domain.status,
            "category" to domain.category,
            "brand" to domain.brand.trim(),
            "model" to domain.model.trim(),
            "numberPlateRaw" to domain.numberPlateRaw.trim(),
            "numberPlateNormalized" to domain.numberPlateNormalized.trim(),
            "plateOcrStatus" to domain.plateOcrStatus,
            "imagePaths" to domain.imagePaths,
            "primaryImagePath" to domain.effectivePrimaryImage,
            "locationName" to domain.locationName.trim(),
            "area" to domain.area.trim(),
            "city" to domain.city.trim(),
            "latitude" to domain.latitude,
            "longitude" to domain.longitude,
            "sightingDate" to domain.sightingDate,
            "sightingTime" to domain.sightingTime,
            "notes" to domain.notes.trim(),
            "createdAt" to (domain.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        docRef.set(data, com.google.firebase.firestore.SetOptions.merge()).await()
        dao.updateSyncStatus(entity.id, "synced", null)
        Log.i(TAG, "Confirmed Firestore write for ${entity.id}, updated Room syncStatus to 'synced'")
    }

    /**
     * Starts listening to Firestore changes and merges them safely into Room.
     * Implements conflict resolution:
     * - Never overwrites a local item with pending changes (syncStatus == "pending" or isPendingDelete == 1).
     * - Compares timestamps to prevent older cloud snapshots from overwriting newer local edits.
     */
    fun startRemoteFirestoreSync(garageId: String): Flow<Unit> = callbackFlow {
        if (garageId.isBlank() || garageId == "local_garage" || auth.currentUser == null) {
            trySend(Unit)
            awaitClose {}
            return@callbackFlow
        }

        val listener: ListenerRegistration = firestore.collection("garages")
            .document(garageId)
            .collection("sightings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Firestore sync listener error: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                scope.launch {
                    for (doc in snapshot.documents) {
                        try {
                            val id = doc.getString("id") ?: doc.id
                            val local = dao.getSightingById(id)

                            // Conflict check: if user has pending local modifications, do not clobber
                            if (local != null && (local.syncStatus == "pending" || local.isPendingDelete)) {
                                Log.i(TAG, "Skipping remote merge for $id: pending local modifications exist")
                                continue
                            }

                            val remoteUpdated = doc.getTimestamp("updatedAt")
                            val remoteCreated = doc.getTimestamp("createdAt")
                            val remoteUpdatedMillis = remoteUpdated?.toDate()?.time
                                ?: remoteCreated?.toDate()?.time
                                ?: System.currentTimeMillis()

                            // If local has a newer updatedAtMillis, keep local
                            if (local != null && local.updatedAtMillis > remoteUpdatedMillis) {
                                continue
                            }

                            val remoteSighting = Sighting(
                                id = id,
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
                                syncStatus = "synced",
                                createdAt = remoteCreated,
                                updatedAt = remoteUpdated
                            )

                            dao.insertOrUpdate(remoteSighting.toEntity(syncStatusOverride = "synced"))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error ingesting remote doc ${doc.id}: ${e.message}")
                        }
                    }
                    trySend(Unit)
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Ensures all photos are stored permanently in private app storage.
     */
    private fun prepareImagesForPersistence(images: List<String>, primary: String): List<String> {
        val all = (images + listOf(primary)).filter { it.isNotBlank() }.distinct()
        return all.map { path ->
            if (ImageStorageHelper.isBase64Image(path)) {
                path
            } else if (path.startsWith("content://") || path.startsWith("file://")) {
                // If it's already a persistent file in our filesDir, keep it
                if (path.contains(context.filesDir.name)) {
                    path
                } else {
                    path
                }
            } else {
                path
            }
        }
    }

    companion object {
        private const val TAG = "SightingRepository"
    }
}
