package com.example.waterloop.ui.trips
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waterloop.data.model.Marker
import com.example.waterloop.data.repository.MarkerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MarkerViewModel : ViewModel() {

    private val repository = MarkerRepository()

    private val _markers = MutableStateFlow<List<Marker>>(emptyList())
    val markers: StateFlow<List<Marker>> = _markers

    fun loadMarkers(tripId: String) {
        viewModelScope.launch {
            _markers.value = repository.getMarkers(tripId)
        }
    }

    fun createMarker(
        tripId: String,
        title: String,
        latitude: Double,
        longitude: Double,
        description: String? = null,
        category: String? = null,
        notes: String? = null
    ) {
        viewModelScope.launch {
            repository.createMarker(
                tripId = tripId,
                title = title,
                latitude = latitude,
                longitude = longitude,
                description = description,
                category = category,
                notes = notes
            )
            // Reload markers to see the new one
            loadMarkers(tripId)
        }
    }

    fun updateMarker(marker: Marker) {
        viewModelScope.launch {
            repository.updateMarker(marker)
            // Reload markers to see changes
            loadMarkers(marker.tripId)
        }
    }

    fun deleteMarker(markerId: String, tripId: String) {
        viewModelScope.launch {
            repository.deleteMarker(markerId)
            // Reload markers to reflect deletion
            loadMarkers(tripId)
        }
    }
}
