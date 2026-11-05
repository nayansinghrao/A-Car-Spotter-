package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.repository.SightingRepository
import java.util.concurrent.TimeUnit

/**
 * WorkManager CoroutineWorker that processes pending offline sync operations:
 * - Offline-created sightings
 * - Offline edits and updates
 * - Offline deletions
 *
 * Runs automatically when connectivity returns or periodically via WorkManager.
 */
class SightingSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "SightingSyncWorker started background sync")
        return try {
            val repository = SightingRepository(applicationContext)
            val syncResult = repository.syncPendingChangesNow()

            if (syncResult.isSuccess) {
                Log.i(TAG, "SightingSyncWorker completed successfully: ${syncResult.getOrNull()}")
                Result.success()
            } else {
                Log.w(TAG, "SightingSyncWorker encountered error: ${syncResult.exceptionOrNull()?.message}")
                if (runAttemptCount < 5) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SightingSyncWorker fatal exception: ${e.message}", e)
            if (runAttemptCount < 5) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "SightingSyncWorker"
        const val WORK_NAME = "spotr_sightings_sync_work"

        /**
         * Enqueues an immediate or connected one-time sync with exponential backoff.
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SightingSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
