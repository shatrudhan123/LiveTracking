package com.example.livetracking.room.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.livetracking.room.entity.LocationEntity

@Dao
interface LocationDao {

    @Insert
    suspend fun insert(location: LocationEntity)

    @Query("""
        SELECT * FROM location_logs
        WHERE isSynced = 0
        ORDER BY timestamp ASC
    """)
    suspend fun getUnsynced(): List<LocationEntity>

    @Query("UPDATE location_logs SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: Int)

    @Query("SELECT COUNT(*) FROM location_logs WHERE isSynced = 0")
    fun getPendingCount(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM location_logs WHERE isSynced = 1")
    fun getSyncedCount(): LiveData<Int>

    @Query("""
        SELECT id FROM location_logs
        WHERE isSynced = 1
        ORDER BY id DESC
        LIMIT 1
    """)
    fun getLastSyncedId(): LiveData<Int?>
}
