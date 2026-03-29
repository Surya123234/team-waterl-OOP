package com.example.waterloop.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "marker_photos")
data class MarkerPhotoEntity(
    @PrimaryKey val id: String,
    val markerId: String,
    val photoUrl: String? = null,
    val localFilePath: String? = null,

    // New fields added for offline sync
    val synced: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val locallyDeleted: Boolean = false
)