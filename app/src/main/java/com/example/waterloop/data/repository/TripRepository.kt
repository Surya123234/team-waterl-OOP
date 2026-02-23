package com.example.waterloop.data.repository

import com.example.waterloop.data.model.Trip
import com.example.waterloop.data.model.TripMember
import com.example.waterloop.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

class TripRepository {

    private val client = SupabaseClient.client.postgrest
    private val authRepository = AuthRepository()

    suspend fun createTrip(title: String, city: String?, startDate: String?, endDate: String?): Trip? {
        val userId = "20f5fdd5-97b5-4ced-8d56-1f5d93c8e716"
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
