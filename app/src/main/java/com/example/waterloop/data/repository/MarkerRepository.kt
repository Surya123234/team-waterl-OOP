package com.example.waterloop.data.repository

import com.example.waterloop.WaterlOOPApplication
import com.example.waterloop.data.local.entity.MarkerEntity
import com.example.waterloop.data.local.toModel
import com.example.waterloop.data.model.Marker
import java.util.UUID

class MarkerRepository {

    private val db get() = WaterlOOPApplication.instance.database
    private val syncManager get() = WaterlOOPApplication.instance.syncManager

    suspend fun createMarker(
        tripId: String,
        title: String,
        latitude: Double,
        longitude: Double,
        description: String? = null,
        category: String? = null,
        notes: String? = null
    ): Marker {
        val id = UUID.randomUUID().toString()
        val entity = MarkerEntity(
            id = id,
            tripId = tripId,
            title = title,
            latitude = latitude,
            longitude = longitude,
            description = description,
            category = category,
            notes = notes,
            synced = false,
            updatedAt = System.currentTimeMillis()
        )
        db.markerDao().upsert(entity)
        syncManager.requestSync()
        return entity.toModel()
    }

    suspend fun getMarkers(tripId: String): List<Marker> {
        return db.markerDao().getMarkersForTrip(tripId).map { it.toModel() }
    }

    suspend fun updateMarker(marker: Marker) {
        val existing = db.markerDao().getMarkerById(marker.id!!) ?: return
        db.markerDao().upsert(
            existing.copy(
                title = marker.title,
                description = marker.description,
                category = marker.category,
                notes = marker.notes,
                latitude = marker.latitude,
                longitude = marker.longitude,
                visited = marker.visited,
                synced = false,
                updatedAt = System.currentTimeMillis()
            )
        )
        syncManager.requestSync()
    }

    suspend fun deleteMarker(markerId: String) {
        db.markerDao().markDeleted(markerId)
        syncManager.requestSync()
    }
}