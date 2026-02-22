package com.example.waterloop.data.repository

import com.example.waterloop.data.model.Trip
import com.example.waterloop.data.model.TripMember
import com.example.waterloop.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.util.UUID

class TripRepository {

    private val client = SupabaseClient.client.postgrest
    private val authRepository = AuthRepository()

    suspend fun createTrip(title: String, city: String?): Trip? {

//         val userId = authRepository.getCurrentUserId() ?: return null
        val userId = "20f5fdd5-97b5-4ced-8d56-1f5d93c8e716"
        val trip = Trip(
            ownerId = userId,
            title = title,
            city = city
        )

        return try {
            // Insert trip
            val insertedTrip = client.from("trips")
                .insert(trip) {
                    select()
                }
                .decodeSingle<Trip>()

            val insertedId = insertedTrip.id ?: return null

            // Insert owner membership
            val member = TripMember(
                tripId = insertedId,
                userId = userId,
            )

            client.from("trip_members")
                .insert(member)

            insertedTrip
        } catch (e: Exception) {
            // Log exception
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
        return client.from("trips")
            .select()
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
}