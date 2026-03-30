package com.example.waterloop.data.sync

import android.content.Context
import android.util.Log
import com.example.waterloop.data.local.AppDatabase
import com.example.waterloop.data.local.entity.MarkerEntity
import com.example.waterloop.data.local.entity.MarkerPhotoEntity
import com.example.waterloop.data.local.entity.TripEntity
import com.example.waterloop.data.model.Marker
import com.example.waterloop.data.model.MarkerPhoto
import com.example.waterloop.data.model.Trip
import com.example.waterloop.data.model.TripMember
import com.example.waterloop.data.remote.SupabaseClient
import com.example.waterloop.data.repository.AuthRepository
import com.mapbox.geojson.Point
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Coordinates offline-first synchronisation between the local Room database and Supabase.
 *
 * Push order: Trips → Markers → MarkerPhotos → Deletes. This order respects the foreign keys.
 * Pull order: Trips → Markers → MarkerPhotos.
 *
 * Conflict strategy: row-level last-write-wins using the local 'updatedAt' timestamp.
 * Unsynced local rows are never overwritten by remote data during a pull.
 */
class SyncManager private constructor(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val connectivity = ConnectivityObserver(context)
    private val authRepository = AuthRepository()
    private val postgrest = SupabaseClient.client.postgrest
    private val storage = SupabaseClient.client.storage
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    companion object {
        private const val TAG = "SyncManager"

        @Volatile
        private var INSTANCE: SyncManager? = null

        fun getInstance(context: Context): SyncManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SyncManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    // Public API

    // Begins watching connectivity and triggers a full sync whenever the device comes online
    fun startObserving() {
        scope.launch {
            connectivity.observe().collectLatest { online ->
                if (online) {
                    Log.d(TAG, "Network available — triggering sync")
                    sync()
                }
            }
        }
    }

    fun isOnline(): Boolean = connectivity.isOnline()

    /**
     * Run a complete push-then-pull cycle.
     * Safe to call from anywhere as the mutex prevents overlapping syncs.
     */
    suspend fun sync() {
        if (!connectivity.isOnline()) return
        syncMutex.withLock {
            try {
                // push local changes to remote following the push order.
                pushTrips()
                pushMarkers()
                pushMarkerPhotos()
                pushDeletes()

                // pull remote changes to local following the pull order.
                pullTrips()
                pullMarkers()
                pullMarkerPhotos()

                Log.d(TAG, "Sync completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed: ${e.message}", e)
            }
        }
    }

    // Fire-and-forget sync: used by repositories after local writes.
    fun requestSync() {
        scope.launch { sync() }
    }

    // Push from local to remote
    private suspend fun pushTrips() {
        val unsynced = db.tripDao().getUnsynced()
        for (entity in unsynced) {
            try {
                // If there's a local cover image waiting to be uploaded, it is handled first
                var coverUrl = entity.coverImageUrl
                if (entity.localCoverImagePath != null) {
                    val file = File(entity.localCoverImagePath)
                    if (file.exists()) {
                        val bytes = file.readBytes()
                        val fileName = "cover_${System.currentTimeMillis()}.jpg"
                        val bucket = storage.from("trip-covers")
                        bucket.upload("${entity.id}/$fileName", bytes) { upsert = true }
                        coverUrl = bucket.publicUrl("${entity.id}/$fileName")
                        file.delete()
                    }
                }

                val trip = Trip(
                    id = entity.id,
                    ownerId = entity.ownerId,
                    title = entity.title,
                    city = entity.city,
                    startDate = entity.startDate,
                    endDate = entity.endDate,
                    coverImageUrl = coverUrl,
                    status = entity.status,
                    routes = entity.routes?.map { listOf(it.longitude(), it.latitude()) }
                )
                postgrest.from("trips").upsert(trip)

                // Ensure that the owner row in trip_members exists
                val userId = authRepository.getCurrentUserId()
                if (userId != null) {
                    try {
                        val member = TripMember(tripId = entity.id, userId = userId, role = "owner")
                        postgrest.from("trip_members").upsert(member)
                    } catch (_: Exception) { /* row may already exist */ }
                }

                db.tripDao().upsert(
                    entity.copy(
                        synced = true,
                        coverImageUrl = coverUrl,
                        localCoverImagePath = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "Pushed trip ${entity.id} with status ${entity.status}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to push trip ${entity.id}: ${e.message}")
            }
        }
    }

    private suspend fun pushMarkers() {
        val unsynced = db.markerDao().getUnsynced()
        for (entity in unsynced) {
            try {
                val marker = Marker(
                    id = entity.id,
                    tripId = entity.tripId,
                    title = entity.title,
                    description = entity.description,
                    category = entity.category,
                    notes = entity.notes,
                    latitude = entity.latitude,
                    longitude = entity.longitude,
                    visited = entity.visited
                )
                postgrest.from("markers").upsert(marker)
                db.markerDao().markSynced(entity.id)
                Log.d(TAG, "Pushed marker ${entity.id}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to push marker ${entity.id}: ${e.message}")
            }
        }
    }

    private suspend fun pushMarkerPhotos() {
        // Photos whose bytes still need to be uploaded to Supabase Storage
        val pending = db.markerPhotoDao().getPendingUploads()
        for (entity in pending) {
            try {
                val file = File(entity.localFilePath!!)
                if (!file.exists()) {
                    Log.w(TAG, "Local photo file missing: ${entity.localFilePath}")
                    continue
                }

                val bytes = file.readBytes()
                val fileName = "photo_${System.currentTimeMillis()}.jpg"
                val bucket = storage.from("marker-photos")
                bucket.upload("${entity.markerId}/$fileName", bytes) { upsert = true }
                val publicUrl = bucket.publicUrl("${entity.markerId}/$fileName")

                // insert the row into the remote marker_photos table in Supabase
                val photo = MarkerPhoto(id = entity.id, markerId = entity.markerId, photoUrl = publicUrl)
                postgrest.from("marker_photos").upsert(photo)

                // Update local entity: store the public URL from Supabase, clear local file path, mark as synced
                db.markerPhotoDao().upsert(
                    entity.copy(photoUrl = publicUrl, localFilePath = null, synced = true)
                )
                file.delete()
                Log.d(TAG, "Pushed photo ${entity.id}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to push photo ${entity.id}: ${e.message}")
            }
        }

        // Metadata-only changes (e.g. photo has already been uploaded to Supabase Storage but row is yet to be pushed)
        val unsyncedMeta = db.markerPhotoDao().getUnsyncedMetadata()
        for (entity in unsyncedMeta) {
            if (entity.photoUrl == null) continue
            try {
                val photo = MarkerPhoto(id = entity.id, markerId = entity.markerId, photoUrl = entity.photoUrl)
                postgrest.from("marker_photos").upsert(photo)
                db.markerPhotoDao().markSynced(entity.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to push photo metadata ${entity.id}: ${e.message}")
            }
        }
    }

    // Permanently delete the content from the local database after it has been pushed to remote.
    private suspend fun pushDeletes() {
        // Children deleted first to respect foreign keys
        for (entity in db.markerPhotoDao().getLocallyDeleted()) {
            try {
                postgrest.from("marker_photos").delete { filter { eq("id", entity.id) } }
                db.markerPhotoDao().hardDelete(entity.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete remote photo ${entity.id}: ${e.message}")
            }
        }
        for (entity in db.markerDao().getLocallyDeleted()) {
            try {
                postgrest.from("markers").delete { filter { eq("id", entity.id) } }
                db.markerDao().hardDelete(entity.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete remote marker ${entity.id}: ${e.message}")
            }
        }
        for (entity in db.tripDao().getLocallyDeleted()) {
            try {
                postgrest.from("trips").delete { filter { eq("id", entity.id) } }
                db.tripDao().hardDelete(entity.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete remote trip ${entity.id}: ${e.message}")
            }
        }
    }

    // Pull from remote to local

    private suspend fun pullTrips() {
        val userId = authRepository.getCurrentUserId() ?: return

        val memberTripIds = postgrest.from("trip_members")
            .select { filter { eq("user_id", userId) } }
            .decodeList<TripMember>()
            .map { it.tripId }

        if (memberTripIds.isEmpty()) return

        val remoteTrips = postgrest.from("trips")
            .select { filter { isIn("id", memberTripIds) } }
            .decodeList<Trip>()

        val remoteIds = remoteTrips.mapNotNull { it.id }.toSet()

        for (remote in remoteTrips) {
            val rid = remote.id ?: continue
            val local = db.tripDao().getTripById(rid)

            if (local == null) {
                // Brand-new trip from remote
                db.tripDao().upsert(
                    TripEntity(
                        id = rid,
                        ownerId = remote.ownerId,
                        title = remote.title,
                        city = remote.city,
                        startDate = remote.startDate,
                        endDate = remote.endDate,
                        coverImageUrl = remote.coverImageUrl,
                        status = remote.status,
                        routes = remote.routes?.map { Point.fromLngLat(it[0], it[1]) },
                        synced = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else if (local.synced && !local.locallyDeleted) {
                // Guard: Only overwrite if the local version hasn't been updated in the last 10 seconds.
                // This prevents a stale "Pull" from overwriting a very recent "Push" due to remote indexing lag.
                val recentlyUpdatedLocally = System.currentTimeMillis() - local.updatedAt < 10000
                if (!recentlyUpdatedLocally) {
                    db.tripDao().upsert(
                        local.copy(
                            title = remote.title,
                            city = remote.city,
                            startDate = remote.startDate,
                            endDate = remote.endDate,
                            coverImageUrl = remote.coverImageUrl,
                            status = remote.status,
                            routes = remote.routes?.map { Point.fromLngLat(it[0], it[1]) },
                            synced = true,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            // If local is unsynced, keep local. It will be pushed next cycle
        }

        // Remove trips from local that were deleted remotely (only if they are clean locally)
        val syncedLocalIds = db.tripDao().getSyncedIds()
        for (localId in syncedLocalIds) {
            if (localId !in remoteIds) {
                db.tripDao().hardDelete(localId)
            }
        }
    }

    private suspend fun pullMarkers() {
        val userId = authRepository.getCurrentUserId() ?: return
        val memberTripIds = postgrest.from("trip_members")
            .select { filter { eq("user_id", userId) } }
            .decodeList<TripMember>()
            .map { it.tripId }

        for (tripId in memberTripIds) {
            try {
                val remoteMarkers = postgrest.from("markers")
                    .select { filter { eq("trip_id", tripId) } }
                    .decodeList<Marker>()

                val remoteIds = remoteMarkers.mapNotNull { it.id }.toSet()

                for (remote in remoteMarkers) {
                    val rid = remote.id ?: continue
                    val local = db.markerDao().getMarkerById(rid)

                    if (local == null) {
                        db.markerDao().upsert(
                            MarkerEntity(
                                id = rid,
                                tripId = remote.tripId,
                                title = remote.title,
                                description = remote.description,
                                category = remote.category,
                                notes = remote.notes,
                                latitude = remote.latitude,
                                longitude = remote.longitude,
                                visited = remote.visited,
                                synced = true,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    } else if (local.synced && !local.locallyDeleted) {
                        db.markerDao().upsert(
                            local.copy(
                                title = remote.title,
                                description = remote.description,
                                category = remote.category,
                                notes = remote.notes,
                                latitude = remote.latitude,
                                longitude = remote.longitude,
                                visited = remote.visited,
                                synced = true,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

                val syncedLocalIds = db.markerDao().getSyncedIdsForTrip(tripId)
                for (localId in syncedLocalIds) {
                    if (localId !in remoteIds) {
                        db.markerDao().hardDelete(localId)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to pull markers for trip $tripId: ${e.message}")
            }
        }
    }

    private suspend fun pullMarkerPhotos() {
        val userId = authRepository.getCurrentUserId() ?: return
        val memberTripIds = postgrest.from("trip_members")
            .select { filter { eq("user_id", userId) } }
            .decodeList<TripMember>()
            .map { it.tripId }

        for (tripId in memberTripIds) {
            val markers = db.markerDao().getMarkersForTrip(tripId)
            for (marker in markers) {
                try {
                    val remotePhotos = postgrest.from("marker_photos")
                        .select { filter { eq("marker_id", marker.id) } }
                        .decodeList<MarkerPhoto>()

                    val remoteIds = remotePhotos.mapNotNull { it.id }.toSet()

                    for (remote in remotePhotos) {
                        val rid = remote.id ?: continue
                        val local = db.markerPhotoDao().getPhotoById(rid)

                        if (local == null) {
                            db.markerPhotoDao().upsert(
                                MarkerPhotoEntity(
                                    id = rid,
                                    markerId = remote.markerId,
                                    photoUrl = remote.photoUrl,
                                    synced = true,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        } else if (local.synced && !local.locallyDeleted) {
                            db.markerPhotoDao().upsert(
                                local.copy(
                                    photoUrl = remote.photoUrl,
                                    synced = true,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }

                    val syncedLocalIds = db.markerPhotoDao().getSyncedIdsForMarker(marker.id)
                    for (localId in syncedLocalIds) {
                        if (localId !in remoteIds) {
                            db.markerPhotoDao().hardDelete(localId)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to pull photos for marker ${marker.id}: ${e.message}")
                }
            }
        }
    }
}