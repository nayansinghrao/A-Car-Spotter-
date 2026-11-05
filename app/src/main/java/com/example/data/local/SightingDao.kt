package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SightingDao {

    /**
     * Observes visible sightings for a garage (excluding items queued for pending deletion).
     * Ordered by createdAt / date descending so newest sightings appear first.
     */
    @Query("""
        SELECT * FROM sightings 
        WHERE garageId = :garageId AND isPendingDelete = 0 
        ORDER BY createdAtMillis DESC, sightingDate DESC, sightingTime DESC
    """)
    fun observeSightingsForGarage(garageId: String): Flow<List<SightingEntity>>

    @Query("SELECT * FROM sightings WHERE id = :id LIMIT 1")
    suspend fun getSightingById(id: String): SightingEntity?

    @Query("SELECT * FROM sightings WHERE garageId = :garageId AND isPendingDelete = 0")
    suspend fun getSightingsListForGarage(garageId: String): List<SightingEntity>

    /**
     * Retrieves all sightings currently needing synchronization to Firestore:
     * - Pending create/update (syncStatus == 'pending' or 'failed' and not pending delete)
     * - Pending delete (isPendingDelete == 1)
     */
    @Query("""
        SELECT * FROM sightings 
        WHERE syncStatus IN ('pending', 'failed') OR isPendingDelete = 1
        ORDER BY updatedAtMillis ASC
    """)
    suspend fun getPendingSyncSightings(): List<SightingEntity>

    @Query("""
        SELECT COUNT(*) FROM sightings 
        WHERE (syncStatus IN ('pending', 'failed') OR isPendingDelete = 1)
    """)
    fun observePendingSyncCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(sighting: SightingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(sightings: List<SightingEntity>)

    @Update
    suspend fun update(sighting: SightingEntity)

    @Query("UPDATE sightings SET syncStatus = :status, lastSyncError = :error WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String, error: String? = null)

    @Query("UPDATE sightings SET isPendingDelete = 1, syncStatus = 'pending', updatedAtMillis = :now WHERE id = :id")
    suspend fun markForDeletion(id: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM sightings WHERE id = :id")
    suspend fun deletePermanently(id: String)

    @Query("DELETE FROM sightings WHERE garageId = :garageId AND isPendingDelete = 0")
    suspend fun deleteNonPendingForGarage(garageId: String)
}
