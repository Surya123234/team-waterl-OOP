package com.example.waterloop.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "markers")
data class MarkerEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    val notes: String? = null,
    val latitude: Double,
    val longitude: Double,
    val visited: Boolean = false,

    // New fields added for offline sync
    val synced: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val locallyDeleted: Boolean = false
)