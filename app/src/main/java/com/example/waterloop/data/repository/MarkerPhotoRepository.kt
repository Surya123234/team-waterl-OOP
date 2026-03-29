package com.example.waterloop.data.repository

import com.example.waterloop.WaterlOOPApplication
import com.example.waterloop.data.local.entity.MarkerPhotoEntity
import com.example.waterloop.data.local.toModel
import com.example.waterloop.data.model.MarkerPhoto
import java.io.File
import java.util.UUID

class MarkerPhotoRepository {

    private val db get() = WaterlOOPApplication.instance.database
    private val syncManager get() = WaterlOOPApplication.instance.syncManager

    /**
     * Saves photo bytes to the app's internal storage and creates a Room row.
     * The SyncManager will upload the file to Supabase Storage and insert the
     * remote marker_photos row when connectivity is available.
     */
    suspend fun createMarkerPhotoWithUpload(
        markerId: String,
        fileName: String,
        fileBytes: ByteArray
    ): MarkerPhoto? {
        val id = UUID.randomUUID().toString()

        // persist bytes to internal storage
        val filesDir = WaterlOOPApplication.instance.filesDir
        val localFile = File(filesDir, "photos/${markerId}_$fileName")
        localFile.parentFile?.mkdirs()
        localFile.writeBytes(fileBytes)

        val entity = MarkerPhotoEntity(
            id = id,
            markerId = markerId,
            photoUrl = null,                       // not yet uploaded
            localFilePath = localFile.absolutePath, // SyncManager reads this
            synced = false,
            updatedAt = System.currentTimeMillis()
        )
        db.markerPhotoDao().upsert(entity)
        syncManager.requestSync()

        return entity.toModel()   // toModel() returns file:// URI as the photoUrl
    }

    suspend fun getMarkerPhotos(markerId: String): List<MarkerPhoto> {
        return db.markerPhotoDao().getPhotosForMarker(markerId).map { it.toModel() }
    }

    suspend fun updateMarkerPhoto(markerPhoto: MarkerPhoto) {
        val existing = db.markerPhotoDao().getPhotoById(markerPhoto.id!!) ?: return
        db.markerPhotoDao().upsert(
            existing.copy(
                photoUrl = markerPhoto.photoUrl,
                synced = false,
                updatedAt = System.currentTimeMillis()
            )
        )
        syncManager.requestSync()
    }

    suspend fun deleteMarkerPhoto(photoId: String) {
        // clean up local file if it exists
        val entity = db.markerPhotoDao().getPhotoById(photoId)
        entity?.localFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) file.delete()
        }

        db.markerPhotoDao().markDeleted(photoId)
        syncManager.requestSync()
    }
}