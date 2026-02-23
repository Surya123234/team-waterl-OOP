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

    private val _selectedTrip = MutableStateFlow<Trip?>(null)
    val selectedTrip: StateFlow<Trip?> = _selectedTrip

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

    fun deleteTrip(tripId: String?) {
        if (tripId == null) return
        viewModelScope.launch {
            val success = repository.deleteTrip(tripId)
            if (success) {
                _tripCreationMessage.value = "Trip deleted successfully"
                loadTrips()
            } else {
                _tripCreationMessage.value = "Failed to delete trip"
            }
        }
    }

    fun messageShown() {
        _tripCreationMessage.value = null
    }

    fun setSelectedTrip(trip: Trip) {
        _selectedTrip.value = trip
    }

    fun loadTripById(tripId: String) {
        viewModelScope.launch {
            _selectedTrip.value = repository.getTripById(tripId)
        }
    }
}