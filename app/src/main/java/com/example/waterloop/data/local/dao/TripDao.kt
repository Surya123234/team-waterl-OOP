package com.example.waterloop.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.waterloop.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Query("SELECT * FROM trips WHERE locallyDeleted = 0")
    suspend fun getAllTrips(): List<TripEntity>

    @Query("SELECT * FROM trips WHERE locallyDeleted = 0")
    fun getAllTripsFlow(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTripById(id: String): TripEntity?

    @Query("SELECT * FROM trips WHERE id = :id AND locallyDeleted = 0")
    fun getTripByIdFlow(id: String): Flow<TripEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(trip: TripEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(trips: List<TripEntity>)

    @Query("UPDATE trips SET locallyDeleted = 1, synced = 0, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM trips WHERE synced = 0 AND locallyDeleted = 0")
    suspend fun getUnsynced(): List<TripEntity>

    @Query("SELECT * FROM trips WHERE locallyDeleted = 1")
    suspend fun getLocallyDeleted(): List<TripEntity>

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT id FROM trips WHERE synced = 1 AND locallyDeleted = 0")
    suspend fun getSyncedIds(): List<String>

    @Query("UPDATE trips SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}