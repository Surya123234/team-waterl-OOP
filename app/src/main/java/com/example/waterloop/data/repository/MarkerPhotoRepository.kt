package com.example.waterloop.data.repository

import com.example.waterloop.data.model.MarkerPhoto
import com.example.waterloop.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage

class MarkerPhotoRepository {

    private val client = SupabaseClient.client.postgrest
    private val storage = SupabaseClient.client.storage

    // Upload photo to Supabase Storage and return public URL
    suspend fun uploadPhoto(markerId: String, fileName: String, fileBytes: ByteArray): String? {
        val bucketName = "marker-photos" // must exactly match your bucket
        val path = "$markerId/$fileName"
        val bucket = storage.from(bucketName)

        println("Starting upload to bucket '$bucketName' at path '$path'")

        return try {
            // Upload file (will throw exception if it fails)
            bucket.upload(path, fileBytes) {
                upsert = true
            }

            // If upload succeeds, get public URL
            val publicUrl = bucket.publicUrl(path)
            println("Upload succeeded! URL: $publicUrl")
            publicUrl
        } catch (e: Exception) {
            // Catch any failure
            println("Upload failed: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    // Create a new MarkerPhoto with upload
    suspend fun createMarkerPhotoWithUpload(markerId: String, fileName: String, fileBytes: ByteArray): MarkerPhoto? {
        val photoUrl = uploadPhoto(markerId, fileName, fileBytes) ?: return null
        println("Uploaded")
        val markerPhoto = MarkerPhoto(markerId = markerId, photoUrl = photoUrl)

        return client.from("marker_photos")
            .insert(markerPhoto) { select() }
            .decodeSingle()
    }

    // Get all photos for a specific marker
    suspend fun getMarkerPhotos(markerId: String): List<MarkerPhoto> {
        return client.from("marker_photos")
            .select { filter { eq("marker_id", markerId) } }
            .decodeList()
    }

    // Update a photo (for example, changing URL)
    suspend fun updateMarkerPhoto(markerPhoto: MarkerPhoto) {
        client.from("marker_photos")
            .update(markerPhoto) { filter { eq("id", markerPhoto.id!!) } }
    }

    // Delete a photo by ID
    suspend fun deleteMarkerPhoto(photoId: String) {
        client.from("marker_photos")
            .delete { filter { eq("id", photoId) } }
    }
}