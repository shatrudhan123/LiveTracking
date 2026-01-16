package com.example.livetracking.repository

import android.content.Context
import android.util.Log
import com.example.livetracking.room.dao.LocationDao
import com.example.livetracking.room.entity.LocationEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreLocationRepository(
    private val context: Context,
    private val dao: LocationDao
) {

    private val firestore: FirebaseFirestore by lazy {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        FirebaseFirestore.getInstance()
    }

    suspend fun sync() {
        Log.e("FIRESTORE_SYNC", "Firestore sync started")

        val pending = dao.getUnsynced()
        Log.e("FIRESTORE_SYNC", "Pending count = ${pending.size}")

        for (item in pending) {
            try {
                Log.e("FIRESTORE_SYNC", "Uploading id=${item.id}")

                firestore.collection("location_logs")
                    .add(item)
                    .await()

                dao.markSynced(item.id)
                Log.e("FIRESTORE_SYNC", "Uploaded & marked synced id=${item.id}")

            } catch (e: Exception) {
                Log.e("FIRESTORE_SYNC", "Upload failed id=${item.id}", e)
                break
            }
        }
    }
}
