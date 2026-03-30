package com.example.waterloop.data.local

import com.example.waterloop.data.local.entity.MarkerEntity
import com.example.waterloop.data.local.entity.MarkerPhotoEntity
import com.example.waterloop.data.local.entity.TripEntity
import com.example.waterloop.data.model.Marker
import com.example.waterloop.data.model.MarkerPhoto
import com.example.waterloop.data.model.Trip
import com.mapbox.geojson.Point

// Trip

fun TripEntity.toModel(): Trip = Trip(
    id = id,
    ownerId = ownerId,
    title = title,
    city = city,
    startDate = startDate,
    endDate = endDate,
    // prefer the remote URL; fall back to a file:// URI for offline cover images
    coverImageUrl = coverImageUrl ?: localCoverImagePath?.let { "file://$it" },
    status = status,
    routes = routes?.map { listOf(it.longitude(), it.latitude()) }
)

fun Trip.toEntity(
    synced: Boolean = false,
    updatedAt: Long = System.currentTimeMillis(),
    locallyDeleted: Boolean = false,
    localCoverImagePath: String? = null
): TripEntity = TripEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    ownerId = ownerId,
    title = title,
    city = city,
    startDate = startDate,
    endDate = endDate,
    coverImageUrl = coverImageUrl,
    localCoverImagePath = localCoverImagePath,
    status = status,
    routes = routes?.map { Point.fromLngLat(it[0], it[1]) },
    synced = synced,
    updatedAt = updatedAt,
    locallyDeleted = locallyDeleted
)

// ── Marker ──

fun MarkerEntity.toModel(): Marker = Marker(
    id = id,
    tripId = tripId,
    title = title,
    description = description,
    category = category,
    notes = notes,
    latitude = latitude,
    longitude = longitude,
    visited = visited
)

fun Marker.toEntity(
    synced: Boolean = false,
    updatedAt: Long = System.currentTimeMillis(),
    locallyDeleted: Boolean = false
): MarkerEntity = MarkerEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    tripId = tripId,
    title = title,
    description = description,
    category = category,
    notes = notes,
    latitude = latitude,
    longitude = longitude,
    visited = visited,
    synced = synced,
    updatedAt = updatedAt,
    locallyDeleted = locallyDeleted
)

// ── MarkerPhoto ──

fun MarkerPhotoEntity.toModel(): MarkerPhoto = MarkerPhoto(
    id = id,
    markerId = markerId,
    // prefer remote URL; fall back to file:// URI for offline photos
    photoUrl = photoUrl ?: localFilePath?.let { "file://$it" } ?: ""
)

fun MarkerPhoto.toEntity(
    synced: Boolean = false,
    updatedAt: Long = System.currentTimeMillis(),
    locallyDeleted: Boolean = false,
    localFilePath: String? = null
): MarkerPhotoEntity = MarkerPhotoEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    markerId = markerId,
    photoUrl = photoUrl.ifBlank { null },
    localFilePath = localFilePath,
    synced = synced,
    updatedAt = updatedAt,
    locallyDeleted = locallyDeleted
)