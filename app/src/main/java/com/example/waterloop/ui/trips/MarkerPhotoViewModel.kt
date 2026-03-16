package com.example.waterloop.ui.trips

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

    // Upload a photo and create a new MarkerPhoto
    fun uploadAndCreateMarkerPhoto(markerId: String, fileName: String, fileBytes: ByteArray) {
        viewModelScope.launch {
            val newPhoto = repository.createMarkerPhotoWithUpload(markerId, fileName, fileBytes)
            if (newPhoto != null) {
                loadMarkerPhotos(markerId)
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