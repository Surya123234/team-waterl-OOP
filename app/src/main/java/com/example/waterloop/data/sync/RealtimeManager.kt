package com.example.waterloop.data.sync

import android.util.Log
import com.example.waterloop.data.local.AppDatabase
import com.example.waterloop.data.local.entity.CachedRoleEntity
import com.example.waterloop.data.local.entity.MarkerEntity
import com.example.waterloop.data.local.entity.MarkerPhotoEntity
import com.example.waterloop.data.local.entity.TripEntity
import com.example.waterloop.data.model.Marker
import com.example.waterloop.data.model.MarkerPhoto
import com.example.waterloop.data.model.Trip
import com.example.waterloop.data.model.TripMember
import com.example.waterloop.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

/**
 * Manages Supabase Realtime WebSocket subscriptions for a single trip at a time.
 *
 * When subscribed to a trip, changes made by other users (INSERT/UPDATE/DELETE on markers,
 * and UPDATE on trips) are written directly into the local Room database. Because the
 * ViewModels observe Room via Flow, the UI updates automatically without any polling.
 *
 * Conflict rule: incoming realtime events are ignored for rows that have pending local
 * changes (synced = false) to avoid overwriting the current user's unsent edits.
 *
 * Note: DELETE events for markers require REPLICA IDENTITY FULL on the markers table
 * so Supabase includes the old row's id in the event payload. Run this once in Supabase:
 *   ALTER TABLE markers REPLICA IDENTITY FULL;
 *   ALTER TABLE trips   REPLICA IDENTITY FULL;
 */
class RealtimeManager(private val db: AppDatabase) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeChannel: RealtimeChannel? = null
    private var currentTripId: String? = null
    private var userChannel: RealtimeChannel? = null

    companion object {
        private const val TAG = "RealtimeManager"
    }

    /**
     * Subscribe to realtime changes for [tripId]. Replaces any existing subscription.
     * Must be called after the user has authenticated.
     */
    fun subscribeToTrip(tripId: String) {
        unsubscribeFromTrip()

        val channel = SupabaseClient.client.channel("trip_$tripId")
        activeChannel = channel
        this.currentTripId = tripId

        // ── Markers ──────────────────────────────────────────────────────────────

        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "markers"
        }.onEach { action ->
            runCatching {
                upsertMarkerToRoom(action.decodeRecord())
            }.onFailure { Log.w(TAG, "marker insert event error: ${it.message}") }
        }.launchIn(scope)

        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "markers"
        }.onEach { action ->
            runCatching {
                upsertMarkerToRoom(action.decodeRecord())
            }.onFailure { Log.w(TAG, "marker update event error: ${it.message}") }
        }.launchIn(scope)

        // No trip_id filter on DELETE — requires REPLICA IDENTITY FULL for row filtering.
        // RLS ensures we only receive deletes for trips we're a member of.
        channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
            table = "markers"
        }.onEach { action ->
            runCatching {
                val id = action.oldRecord["id"]?.jsonPrimitive?.content ?: return@onEach
                db.markerDao().hardDelete(id)
                Log.d(TAG, "Realtime deleted marker $id")
            }.onFailure { Log.w(TAG, "marker delete event error: ${it.message}") }
        }.launchIn(scope)

        // ── Trip metadata ─────────────────────────────────────────────────────────

        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "trips"
        }.onEach { action ->
            runCatching {
                upsertTripToRoom(action.decodeRecord())
            }.onFailure { Log.w(TAG, "trip update event error: ${it.message}") }
        }.launchIn(scope)

        // ── Marker photos ─────────────────────────────────────────────────────────
        // marker_photos has no trip_id column, so we subscribe without a column filter.
        // RLS limits events to trips the user is a member of. We additionally guard
        // against writing photos for markers not in the current trip via a Room lookup.

        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "marker_photos"
        }.onEach { action ->
            runCatching {
                upsertMarkerPhotoToRoom(action.decodeRecord())
            }.onFailure { Log.w(TAG, "marker_photo insert event error: ${it.message}") }
        }.launchIn(scope)

        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "marker_photos"
        }.onEach { action ->
            runCatching {
                upsertMarkerPhotoToRoom(action.decodeRecord())
            }.onFailure { Log.w(TAG, "marker_photo update event error: ${it.message}") }
        }.launchIn(scope)

        channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
            table = "marker_photos"
        }.onEach { action ->
            runCatching {
                val id = action.oldRecord["id"]?.jsonPrimitive?.content ?: return@onEach
                db.markerPhotoDao().hardDelete(id)
                Log.d(TAG, "Realtime deleted marker_photo $id")
            }.onFailure { Log.w(TAG, "marker_photo delete event error: ${it.message}") }
        }.launchIn(scope)

        scope.launch {
            runCatching {
                channel.subscribe()
                Log.d(TAG, "Subscribed to realtime for trip $tripId")
            }.onFailure { Log.e(TAG, "Realtime subscribe failed: ${it.message}") }
        }
    }

    /**
     * Subscribe to trip_members INSERT events for [userId] so that when another user shares a
     * trip, it appears immediately without waiting for the next connectivity-triggered sync.
     * The trip row is fetched from Supabase and written to Room; [getAllTripsFlow] emits automatically.
     */
    fun subscribeToUserTrips(userId: String) {
        unsubscribeFromUserTrips()

        val channel = SupabaseClient.client.channel("user_trips_$userId")
        userChannel = channel

        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "trip_members"
        }.onEach { action ->
            runCatching {
                val member = action.decodeRecord<TripMember>()
                if (member.userId != userId) return@onEach  // guard — RLS should already limit this

                val trip = SupabaseClient.client.postgrest
                    .from("trips")
                    .select { filter { eq("id", member.tripId) } }
                    .decodeSingleOrNull<Trip>() ?: return@onEach

                val tripId = trip.id ?: return@onEach
                db.tripDao().upsert(
                    TripEntity(
                        id = tripId,
                        ownerId = trip.ownerId,
                        title = trip.title,
                        city = trip.city,
                        startDate = trip.startDate,
                        endDate = trip.endDate,
                        coverImageUrl = trip.coverImageUrl,
                        status = trip.status,
                        synced = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                db.cachedRoleDao().upsert(CachedRoleEntity(tripId = tripId, role = member.role))
                Log.d(TAG, "Realtime: trip $tripId shared with user")
            }.onFailure { Log.w(TAG, "trip_members insert event error: ${it.message}") }
        }.launchIn(scope)

        scope.launch {
            runCatching {
                channel.subscribe()
                Log.d(TAG, "Subscribed to user trips realtime for $userId")
            }.onFailure { Log.e(TAG, "User trips realtime subscribe failed: ${it.message}") }
        }
    }

    fun unsubscribeFromUserTrips() {
        val channel = userChannel ?: return
        userChannel = null
        scope.launch {
            runCatching {
                SupabaseClient.client.realtime.removeChannel(channel)
            }.onFailure { Log.w(TAG, "User trips realtime unsubscribe error: ${it.message}") }
        }
    }

    /**
     * Unsubscribe from the current trip channel. Safe to call when not subscribed.
     */
    fun unsubscribeFromTrip() {
        val channel = activeChannel ?: return
        activeChannel = null
        currentTripId = null
        scope.launch {
            runCatching {
                SupabaseClient.client.realtime.removeChannel(channel)
                Log.d(TAG, "Unsubscribed from realtime")
            }.onFailure { Log.w(TAG, "Realtime unsubscribe error: ${it.message}") }
        }
    }

    // ── Room helpers ──────────────────────────────────────────────────────────────

    private suspend fun upsertMarkerToRoom(marker: Marker) {
        val id = marker.id ?: return
        if (marker.tripId != currentTripId) return  // ignore events from other trips
        val local = db.markerDao().getMarkerById(id)
        // Don't clobber rows the user has edited but not yet synced
        if (local != null && !local.synced) return
        db.markerDao().upsert(
            MarkerEntity(
                id = id,
                tripId = marker.tripId,
                title = marker.title,
                description = marker.description,
                category = marker.category,
                notes = marker.notes,
                latitude = marker.latitude,
                longitude = marker.longitude,
                visited = marker.visited,
                synced = true,
                updatedAt = System.currentTimeMillis()
            )
        )
        Log.d(TAG, "Realtime upserted marker $id")
    }

    private suspend fun upsertMarkerPhotoToRoom(photo: MarkerPhoto) {
        val id = photo.id ?: return
        // Only process if the photo's marker belongs to the current trip
        val marker = db.markerDao().getMarkerById(photo.markerId) ?: return
        if (marker.tripId != currentTripId) return
        val local = db.markerPhotoDao().getPhotoById(id)
        if (local != null && !local.synced) return
        db.markerPhotoDao().upsert(
            MarkerPhotoEntity(
                id = id,
                markerId = photo.markerId,
                photoUrl = photo.photoUrl,
                synced = true,
                updatedAt = System.currentTimeMillis()
            )
        )
        Log.d(TAG, "Realtime upserted marker_photo $id")
    }

    private suspend fun upsertTripToRoom(trip: Trip) {
        val id = trip.id ?: return
        if (id != currentTripId) return  // ignore events from other trips
        val local = db.tripDao().getTripById(id)
        if (local != null && !local.synced) return
        db.tripDao().upsert(
            (local ?: TripEntity(
                id = id,
                ownerId = trip.ownerId,
                title = trip.title,
                city = trip.city,
                startDate = trip.startDate,
                endDate = trip.endDate,
                coverImageUrl = trip.coverImageUrl,
                status = trip.status,
                synced = true,
                updatedAt = System.currentTimeMillis()
            )).copy(
                title = trip.title,
                city = trip.city,
                startDate = trip.startDate,
                endDate = trip.endDate,
                coverImageUrl = trip.coverImageUrl ?: local?.coverImageUrl,
                status = trip.status,
                synced = true,
                updatedAt = System.currentTimeMillis()
            )
        )
        Log.d(TAG, "Realtime upserted trip $id")
    }
}
