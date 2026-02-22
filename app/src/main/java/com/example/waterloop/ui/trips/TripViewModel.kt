package com.example.waterloop.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waterloop.data.model.Trip
import com.example.waterloop.data.repository.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TripViewModel : ViewModel() {

    private val repository = TripRepository()

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips

    private val _tripCreationMessage = MutableStateFlow<String?>(null)
    val tripCreationMessage: StateFlow<String?> = _tripCreationMessage

    fun loadTrips() {
        viewModelScope.launch {
            _trips.value = repository.getTrips()
        }
    }

    fun createTrip(title: String, city: String?) {
        viewModelScope.launch {
            val newTrip = repository.createTrip(title, city)
            if (newTrip != null) {
                _tripCreationMessage.value = "Trip created successfully"
                loadTrips()
            } else {
                _tripCreationMessage.value = "Failed to create trip"
            }
        }
    }

    fun messageShown() {
        _tripCreationMessage.value = null
    }
}