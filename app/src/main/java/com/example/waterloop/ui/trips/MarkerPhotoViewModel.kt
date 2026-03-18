package com.example.waterloop.ui.trips

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waterloop.data.model.MarkerPhoto
import com.example.waterloop.data.repository.MarkerPhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MarkerPhotoViewModel : ViewModel() {

    private val repository = MarkerPhotoRepository()

    private val _markerPhotos = MutableStateFlow<List<MarkerPhoto>>(emptyList())
    val markerPhotos: StateFlow<List<MarkerPhoto>> = _markerPhotos

    // Load all photos for a specific marker
    fun loadMarkerPhotos(markerId: String) {
        viewModelScope.launch {
            _markerPhotos.value = repository.getMarkerPhotos(markerId)
        }
    }

    // Upload a photo from a Uri and create a new MarkerPhoto
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

    // Update a specific MarkerPhoto
    fun updateMarkerPhoto(photoId: String, markerId: String, newUrl: String) {
        viewModelScope.launch {
            val photo = MarkerPhoto(id = photoId, markerId = markerId, photoUrl = newUrl)
            repository.updateMarkerPhoto(photo)
            loadMarkerPhotos(markerId)
        }
    }

    // Delete a specific MarkerPhoto
    fun deleteMarkerPhoto(photoId: String, markerId: String) {
        viewModelScope.launch {
            repository.deleteMarkerPhoto(photoId)
            loadMarkerPhotos(markerId)
        }
    }
}