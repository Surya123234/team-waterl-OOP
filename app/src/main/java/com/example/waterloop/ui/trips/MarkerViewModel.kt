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

        fun createMarker(tripId: String) {
            viewModelScope.launch {

                repository.createMarker(
                    tripId = tripId,
                    title = "Test Marker",
                    latitude = 43.45,
                    longitude = -80.49,
                )
            }
        }
        //hardcoded to udpate the first marker in the database just to see if it works
        //User input based field updates will be added when the UI is added
        fun updateFirstMarker(newTitle: String) {
            viewModelScope.launch {
                if (_markers.value.isNotEmpty()) {
                    val marker = _markers.value.first().copy(title = newTitle)
                    repository.updateMarker(marker)
                    // Reload markers to see changes
                    loadMarkers(marker.tripId)
                }
            }
        }
        //same as above, hardcoded to test deleting
        fun deleteFirstMarker() {
            viewModelScope.launch {
                if (_markers.value.isNotEmpty()) {
                    val markerId = _markers.value.first().id!!
                    val tripId = _markers.value.first().tripId
                    repository.deleteMarker(markerId)
                    // Reload markers to reflect deletion
                    loadMarkers(tripId)
                }
            }
        }
    }
