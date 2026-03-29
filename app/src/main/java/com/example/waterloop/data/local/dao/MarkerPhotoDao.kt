package com.example.waterloop.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.waterloop.data.local.entity.MarkerPhotoEntity

@Dao
interface MarkerPhotoDao {

    @Query("SELECT * FROM marker_photos WHERE markerId = :markerId AND locallyDeleted = 0")
    suspend fun getPhotosForMarker(markerId: String): List<MarkerPhotoEntity>

    @Query("SELECT * FROM marker_photos WHERE id = :id")
    suspend fun getPhotoById(id: String): MarkerPhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(photo: MarkerPhotoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(photos: List<MarkerPhotoEntity>)

    @Query("UPDATE marker_photos SET locallyDeleted = 1, synced = 0, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM marker_photos WHERE synced = 0 AND locallyDeleted = 0 AND localFilePath IS NULL")
    suspend fun getUnsyncedMetadata(): List<MarkerPhotoEntity>

    @Query("SELECT * FROM marker_photos WHERE localFilePath IS NOT NULL AND synced = 0 AND locallyDeleted = 0")
    suspend fun getPendingUploads(): List<MarkerPhotoEntity>

    @Query("SELECT * FROM marker_photos WHERE locallyDeleted = 1")
    suspend fun getLocallyDeleted(): List<MarkerPhotoEntity>

    @Query("DELETE FROM marker_photos WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT id FROM marker_photos WHERE markerId = :markerId AND synced = 1 AND locallyDeleted = 0")
    suspend fun getSyncedIdsForMarker(markerId: String): List<String>

    @Query("UPDATE marker_photos SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}