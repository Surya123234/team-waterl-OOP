package com.example.waterloop.ui.trips

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waterloop.WaterlOOPApplication
import com.example.waterloop.data.model.MarkerPhoto
import com.example.waterloop.data.repository.MarkerPhotoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MarkerPhotoViewModel : ViewModel() {

    private val repository = MarkerPhotoRepository()
    private val syncManager get() = WaterlOOPApplication.instance.syncManager

    private val _markerPhotos = MutableStateFlow<List<MarkerPhoto>>(emptyList())
    val markerPhotos: StateFlow<List<MarkerPhoto>> = _markerPhotos

    private var photosObserverJob: Job? = null

    fun loadMarkerPhotos(markerId: String) {
        // Observe Room — auto-updates when realtime writes a new photo or sync completes
        photosObserverJob?.cancel()
        photosObserverJob = viewModelScope.launch {
            repository.getMarkerPhotosFlow(markerId).collect { _markerPhotos.value = it }
        }

        // Pull remote changes (also uploads any pending local photos)
        viewModelScope.launch {
            try { syncManager.sync() } catch (_: Exception) { /* offline — local data is fine */ }
        }
    }

    fun uploadMarkerPhoto(markerId: String, uri: android.net.Uri, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: return@launch
                val fileName = "photo_${System.currentTimeMillis()}.jpg"
                repository.createMarkerPhotoWithUpload(markerId, fileName, bytes)
                // Room Flow in photosObserverJob will emit the updated list automatically
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateMarkerPhoto(photoId: String, markerId: String, newUrl: String) {
        viewModelScope.launch {
            repository.updateMarkerPhoto(MarkerPhoto(id = photoId, markerId = markerId, photoUrl = newUrl))
        }
    }

    fun deleteMarkerPhoto(photoId: String, markerId: String) {
        viewModelScope.launch {
            repository.deleteMarkerPhoto(photoId)
        }
    }
}