package com.example.waterloop.data.repository

import com.example.waterloop.data.model.Marker
import com.example.waterloop.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

class MarkerRepository {

    private val client = SupabaseClient.client.postgrest
    suspend fun createMarker(
        tripId: String,
        title: String,
        latitude: Double,
        longitude: Double,
//others included in the data class
    ): Marker {

        val marker = Marker(
            tripId = tripId,
            title = title,
            latitude = latitude,
            longitude = longitude,
        )
        return client.from("markers")
            .insert(marker) {
                select()
            }
            .decodeSingle()
    }
    //read markers for a specific trip
    suspend fun getMarkers(tripId: String): List<Marker> {

        return client.from("markers")
            .select {
                filter {
                    eq("trip_id", tripId)
                }
            }
            .decodeList()
    }
    // update marker stuff
    suspend fun updateMarker(marker: Marker) {

        client.from("markers")
            .update(marker) {
                filter {
                    eq("id", marker.id!!)
                }
            }
    }
    //delete the marker
    suspend fun deleteMarker(markerId: String) {
        client.from("markers")
            .delete {
                filter {
                    eq("id", markerId)
                }
            }
    }
}
