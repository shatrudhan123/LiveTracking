package com.example.livetracking.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import androidx.work.WorkerParameters
import com.example.livetracking.foreground_services.LocationForegroundService
import com.example.livetracking.repository.FirestoreLocationRepository
import com.example.livetracking.room.AppDatabase
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await


class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            Log.e("SYNC_WORKER", "Sync started")

            applicationContext.stopService(
                Intent(applicationContext, LocationForegroundService::class.java)
            )

            val db = AppDatabase.getInstance(applicationContext)
            val dao = db.locationDao()

            val pending = dao.getUnsynced()
            Log.e("SYNC_WORKER", "Pending before sync = ${pending.size}")

            for (item in pending) {
                Log.e("SYNC_WORKER", "Uploading id=${item.id}")

                dao.markSynced(item.id)

                Log.e("SYNC_WORKER", "Marked synced id=${item.id}")
            }

            Log.e("SYNC_WORKER", "Sync completed")

            applicationContext.startService(
                Intent(applicationContext, LocationForegroundService::class.java)
            )

            return Result.success()

        } catch (e: Exception) {
            Log.e("SYNC_WORKER", "Sync failed", e)
            return Result.retry()
        }
    }

}
