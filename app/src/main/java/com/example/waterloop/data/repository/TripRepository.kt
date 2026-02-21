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

        // Insert trip
        val insertedTrip = client.from("trips")
            .insert(trip) {
                select()
            }
            .decodeSingle<Trip>()

        // Insert owner membership
        val member = TripMember(
            tripId = insertedTrip.id!!,
            userId = userId,
        )

        client.from("trip_members")
            .insert(member)

        return insertedTrip
    }

    suspend fun getTrips(): List<Trip> {
        return client.from("trips")
            .select()
            .decodeList()
    }
}