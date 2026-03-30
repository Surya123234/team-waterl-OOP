package com.example.waterloop.data.repository

import android.util.Log
import com.example.waterloop.WaterlOOPApplication
import com.example.waterloop.data.local.entity.CachedRoleEntity
import com.example.waterloop.data.local.entity.TripEntity
import com.example.waterloop.data.local.toModel
import com.example.waterloop.data.model.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.example.waterloop.data.model.TripMember
import com.example.waterloop.data.model.TripMemberWithEmail
import com.example.waterloop.data.model.UserIdResult
import com.example.waterloop.data.remote.SupabaseClient
import com.mapbox.geojson.Point
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID

class TripRepository {

    private val db get() = WaterlOOPApplication.instance.database
    private val syncManager get() = WaterlOOPApplication.instance.syncManager
    private val authRepository = AuthRepository()

    // Remote client kept only for online-only operations (sharing, role lookup)
    private val remoteClient = SupabaseClient.client.postgrest

    // CRUD operations (local-first)

    suspend fun createTrip(title: String, city: String?, startDate: String?, endDate: String?): Trip? {
        val userId = authRepository.getCurrentUserId() ?: return null
        val id = UUID.randomUUID().toString()

        val entity = TripEntity(
            id = id,
            ownerId = userId,
            title = title,
            city = city,
            startDate = startDate,
            endDate = endDate,
            synced = false,
            updatedAt = System.currentTimeMillis()
        )
        db.tripDao().upsert(entity)
        syncManager.requestSync()
        return entity.toModel()
    }

    suspend fun updateTrip(trip: Trip): Trip? {
        val existing = db.tripDao().getTripById(trip.id!!) ?: return null
        val updated = existing.copy(
            title = trip.title,
            city = trip.city,
            startDate = trip.startDate,
            endDate = trip.endDate,
            coverImageUrl = trip.coverImageUrl ?: existing.coverImageUrl,
            routes = trip.routes?.map { Point.fromLngLat(it[0], it[1]) },
            synced = false,
            updatedAt = System.currentTimeMillis()
        )
        db.tripDao().upsert(updated)
        syncManager.requestSync()
        return updated.toModel()
    }

    suspend fun updateTripRoutes(tripId: String, points: List<Point>): Boolean {
        val existing = db.tripDao().getTripById(tripId) ?: return false
        val updated = existing.copy(
            routes = points,
            synced = false,
            updatedAt = System.currentTimeMillis()
        )
        db.tripDao().upsert(updated)
        syncManager.requestSync()
        return true
    }

    suspend fun deleteTrip(tripId: String): Boolean {
        // Cascade locally: photos → markers → trip
        val markers = db.markerDao().getMarkersForTrip(tripId)
        for (marker in markers) {
            val photos = db.markerPhotoDao().getPhotosForMarker(marker.id)
            for (photo in photos) {
                db.markerPhotoDao().markDeleted(photo.id)
            }
            db.markerDao().markDeleted(marker.id)
        }
        db.tripDao().markDeleted(tripId)
        syncManager.requestSync()
        return true
    }

    suspend fun getTrips(): List<Trip> {
        return db.tripDao().getAllTrips().map { it.toModel() }
    }

    fun getTripsFlow(): Flow<List<Trip>> =
        db.tripDao().getAllTripsFlow().map { entities -> entities.map { it.toModel() } }

    suspend fun getTripById(tripId: String): Trip? {
        val entity = db.tripDao().getTripById(tripId) ?: return null
        return if (entity.locallyDeleted) null else entity.toModel()
    }

    fun getTripByIdFlow(tripId: String): Flow<Trip?> =
        db.tripDao().getTripByIdFlow(tripId).map { it?.toModel() }

    // Cover image (saves locally, SyncManager uploads to Supabase Storage later)

    suspend fun uploadTripCoverImage(tripId: String, fileName: String, bytes: ByteArray): String? {
        val filesDir = WaterlOOPApplication.instance.filesDir
        val localFile = File(filesDir, "covers/${tripId}_$fileName")
        localFile.parentFile?.mkdirs()
        localFile.writeBytes(bytes)

        val entity = db.tripDao().getTripById(tripId) ?: return null
        db.tripDao().upsert(
            entity.copy(
                localCoverImagePath = localFile.absolutePath,
                synced = false,
                updatedAt = System.currentTimeMillis()
            )
        )
        syncManager.requestSync()

        // Return a file URI so Coil can render the image immediately
        return "file://${localFile.absolutePath}"
    }

    // Sharing / membership (online-only)

    suspend fun getUserIdByEmail(email: String): String? {
        return try {
            SupabaseClient.client.postgrest
                .rpc("get_user_id_by_email", buildJsonObject { put("email_input", email.trim().lowercase()) })
                .decodeList<UserIdResult>()
                .firstOrNull()?.id
        } catch (e: Exception) {
            Log.w("TripRepository", "getUserIdByEmail failed: ${e.message}")
            null
        }
    }

    suspend fun addTripMember(tripId: String, userId: String, role: String): Boolean {
        return try {
            val member = TripMember(tripId = tripId, userId = userId, role = role)
            remoteClient.from("trip_members").insert(member)
            true
        } catch (e: Exception) {
            Log.w("TripRepository", "addTripMember failed: ${e.message}")
            false
        }
    }

    suspend fun getTripMembersWithEmails(tripId: String): List<TripMemberWithEmail> {
        return try {
            SupabaseClient.client.postgrest
                .rpc("get_trip_members_with_emails", buildJsonObject { put("trip_id_input", tripId) })
                .decodeList<TripMemberWithEmail>()
        } catch (e: Exception) {
            Log.w("TripRepository", "getTripMembersWithEmails failed: ${e.message}")
            emptyList()
        }
    }

    // Returns the current user's role in a trip. Caches in Room for offline access.
    suspend fun getCurrentUserRole(tripId: String): String? {
        if (syncManager.isOnline()) {
            val userId = authRepository.getCurrentUserId() ?: return null
            return try {
                val role = remoteClient.from("trip_members")
                    .select {
                        filter {
                            eq("trip_id", tripId)
                            eq("user_id", userId)
                        }
                    }
                    .decodeSingleOrNull<TripMember>()
                    ?.role

                // cache the role for offline use
                if (role != null) {
                    db.cachedRoleDao().upsert(CachedRoleEntity(tripId = tripId, role = role))
                }
                role
            } catch (e: Exception) {
                // fall back to cache on network error
                db.cachedRoleDao().getRole(tripId)
            }
        }
        // offline — return cached value
        return db.cachedRoleDao().getRole(tripId)
    }
}