package com.example.waterloop.data.repository

import com.example.waterloop.data.model.Trip
import com.example.waterloop.data.model.TripMember
import com.example.waterloop.data.model.TripMemberWithEmail
import com.example.waterloop.data.model.UserIdResult
import com.example.waterloop.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import android.util.Log
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TripRepository {

    private val client = SupabaseClient.client.postgrest
    private val storage = SupabaseClient.client.storage
    private val authRepository = AuthRepository()

    suspend fun createTrip(title: String, city: String?, startDate: String?, endDate: String?): Trip? {
        // owner_id is non-nullable in the db schema, so if there's no session we bail early
        // rather than hitting a supabase constraint violation. in practice this shouldn't
        // happen since navigation prevents reaching this screen without being logged in.
        val userId = authRepository.getCurrentUserId() ?: return null

        val trip = Trip(
            ownerId = userId,
            title = title,
            city = city,
            startDate = startDate,
            endDate = endDate
        )

        return try {
            val insertedTrip = client.from("trips")
                .insert(trip) {
                    select()
                }
                .decodeSingle<Trip>()

            val insertedId = insertedTrip.id ?: return null

            val member = TripMember(
                tripId = insertedId,
                userId = userId,
            )

            client.from("trip_members")
                .insert(member)

            insertedTrip
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateTrip(trip: Trip): Trip? {
        return try {
            client.from("trips")
                .update(trip) {
                    filter {
                        eq("id", trip.id!!)
                    }
                    select()
                }
                .decodeSingle<Trip>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deleteTrip(tripId: String): Boolean {
        return try {
            client.from("trips")
                .delete {
                    filter {
                        eq("id", tripId)
                    }
                }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getTrips(): List<Trip> {
        // bail early if no session — shouldn't happen in normal flow but safe to guard
        val userId = authRepository.getCurrentUserId() ?: return emptyList()

        // get all trip ids this user is a member of
        val memberTripIds = SupabaseClient.client.postgrest
            .from("trip_members")
            .select {
                filter { eq("user_id", userId) }
            }
            .decodeList<TripMember>()
            .map { it.tripId }

        if (memberTripIds.isEmpty()) return emptyList()

        // fetch only trips the user belongs to
        return client.from("trips")
            .select {
                filter { isIn("id", memberTripIds) }
            }
            .decodeList()
    }

    suspend fun getTripById(tripId: String): Trip? {
        return try {
            client.from("trips")
                .select {
                    filter {
                        eq("id", tripId)
                    }
                }
                .decodeSingleOrNull<Trip>()
        } catch (e: Exception) {
            null
        }
    }

    // --- Sharing / membership ---

    /** Looks up a user's UUID by their email via the get_user_id_by_email RPC. Returns null if not found. */
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

    /** Adds a user to a trip with the given role ("collaborator" or "viewer"). */
    suspend fun addTripMember(tripId: String, userId: String, role: String): Boolean {
        return try {
            val member = TripMember(tripId = tripId, userId = userId, role = role)
            client.from("trip_members").insert(member)
            true
        } catch (e: Exception) {
            Log.w("TripRepository", "addTripMember failed: ${e.message}")
            false
        }
    }

    /** Returns all members of a trip with their emails via the get_trip_members_with_emails RPC. */
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

    /** Returns the current user's role in a trip, or null if not a member. */
    suspend fun getCurrentUserRole(tripId: String): String? {
        val userId = authRepository.getCurrentUserId() ?: return null
        return try {
            client.from("trip_members")
                .select {
                    filter {
                        eq("trip_id", tripId)
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull<TripMember>()
                ?.role
        } catch (e: Exception) {
            null
        }
    }

    suspend fun uploadTripCoverImage(tripId: String, fileName: String, bytes: ByteArray): String? {
        val bucketName = "trip-covers"
        val path = "$tripId/$fileName"
        val bucket = storage.from(bucketName)

        println("Starting upload to bucket '$bucketName' at path '$path'")

        return try {
            bucket.upload(path, bytes) {
                upsert = true
            }

            val publicUrl = bucket.publicUrl(path)
            println("Upload succeeded! URL: $publicUrl")

            // Fetch the current trip, then update it with the new cover URL
            val trip = getTripById(tripId) ?: return null
            val updatedTrip = trip.copy(coverImageUrl = publicUrl)
            updateTrip(updatedTrip)
            publicUrl
        } catch (e: Exception) {
            println("Upload failed: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}