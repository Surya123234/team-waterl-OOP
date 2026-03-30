package com.example.waterloop.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mapbox.geojson.Point

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val ownerId: String? = null,
    val title: String,
    val city: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val coverImageUrl: String? = null,
    val localCoverImagePath: String? = null,
    val status: String = "active",
    val routes: List<Point>? = null,

    // New fields added for offline sync
    val synced: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val locallyDeleted: Boolean = false
)