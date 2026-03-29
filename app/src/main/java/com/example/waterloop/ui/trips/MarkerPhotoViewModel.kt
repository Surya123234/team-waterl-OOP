package com.example.waterloop.ui.trips

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waterloop.WaterlOOPApplication
import com.example.waterloop.data.model.MarkerPhoto
import com.example.waterloop.data.repository.MarkerPhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MarkerPhotoViewModel : ViewModel() {

    private val repository = MarkerPhotoRepository()
    private val syncManager get() = WaterlOOPApplication.instance.syncManager

    private val _markerPhotos = MutableStateFlow<List<MarkerPhoto>>(emptyList())
    val markerPhotos: StateFlow<List<MarkerPhoto>> = _markerPhotos

    fun loadMarkerPhotos(markerId: String) {
        viewModelScope.launch {
            // show cached photos immediately (includes file:// URIs for offline photos)
            _markerPhotos.value = repository.getMarkerPhotos(markerId)

            // sync and refresh — photos with pending uploads will get their public URLs
            launch {
                try {
                    syncManager.sync()
                    _markerPhotos.value = repository.getMarkerPhotos(markerId)
                } catch (_: Exception) { /* offline — local data is fine */ }
            }
        }
    }

    fun uploadMarkerPhoto(markerId: String, uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: return@launch
                val fileName = "photo_${System.currentTimeMillis()}.jpg"
                val newPhoto = repository.createMarkerPhotoWithUpload(markerId, fileName, bytes)
                if (newPhoto != null) {
                    loadMarkerPhotos(markerId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateMarkerPhoto(photoId: String, markerId: String, newUrl: String) {
        viewModelScope.launch {
            val photo = MarkerPhoto(id = photoId, markerId = markerId, photoUrl = newUrl)
            repository.updateMarkerPhoto(photo)
            loadMarkerPhotos(markerId)
        }
    }

    fun deleteMarkerPhoto(photoId: String, markerId: String) {
        viewModelScope.launch {
            repository.deleteMarkerPhoto(photoId)
            loadMarkerPhotos(markerId)
        }
    }
}