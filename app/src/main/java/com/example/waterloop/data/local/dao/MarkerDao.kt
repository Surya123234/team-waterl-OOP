package com.example.waterloop.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.waterloop.data.local.entity.MarkerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MarkerDao {

    @Query("SELECT * FROM markers WHERE tripId = :tripId AND locallyDeleted = 0")
    suspend fun getMarkersForTrip(tripId: String): List<MarkerEntity>

    @Query("SELECT * FROM markers WHERE tripId = :tripId AND locallyDeleted = 0")
    fun getMarkersForTripFlow(tripId: String): Flow<List<MarkerEntity>>

    @Query("SELECT * FROM markers WHERE id = :id")
    suspend fun getMarkerById(id: String): MarkerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(marker: MarkerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(markers: List<MarkerEntity>)

    @Query("UPDATE markers SET locallyDeleted = 1, synced = 0, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM markers WHERE synced = 0 AND locallyDeleted = 0")
    suspend fun getUnsynced(): List<MarkerEntity>

    @Query("SELECT * FROM markers WHERE locallyDeleted = 1")
    suspend fun getLocallyDeleted(): List<MarkerEntity>

    @Query("DELETE FROM markers WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT id FROM markers WHERE tripId = :tripId AND synced = 1 AND locallyDeleted = 0")
    suspend fun getSyncedIdsForTrip(tripId: String): List<String>

    @Query("UPDATE markers SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}