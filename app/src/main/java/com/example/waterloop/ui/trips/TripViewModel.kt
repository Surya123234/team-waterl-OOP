package com.example.waterloop.ui.trips

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waterloop.WaterlOOPApplication
import com.example.waterloop.data.model.Trip
import com.example.waterloop.data.repository.TripRepository
import com.mapbox.geojson.Point
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// UI model for displaying a trip member with their email and role.
data class TripMemberDisplay(val userId: String, val email: String?, val role: String)

class TripViewModel : ViewModel() {

    private val repository = TripRepository()
    private val syncManager get() = WaterlOOPApplication.instance.syncManager

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips

    private val _tripCreationMessage = MutableStateFlow<String?>(null)
    val tripCreationMessage: StateFlow<String?> = _tripCreationMessage

    private val _selectedTrip = MutableStateFlow<Trip?>(null)
    val selectedTrip: StateFlow<Trip?> = _selectedTrip

    private val _currentUserRole = MutableStateFlow<String?>(null)
    val currentUserRole: StateFlow<String?> = _currentUserRole

    private val _tripMembersDisplay = MutableStateFlow<List<TripMemberDisplay>>(emptyList())
    val tripMembersDisplay: StateFlow<List<TripMemberDisplay>> = _tripMembersDisplay

    private val _shareMessage = MutableStateFlow<String?>(null)
    val shareMessage: StateFlow<String?> = _shareMessage

    fun loadTrips() {
        viewModelScope.launch {
            // show cached data immediately
            _trips.value = repository.getTrips()

            // then sync with remote and refresh
            launch {
                try {
                    syncManager.sync()
                    _trips.value = repository.getTrips()
                } catch (_: Exception) { /* offline or sync error — cached data is fine */ }
            }
        }
    }

    fun createTrip(title: String, city: String?, startDate: String?, endDate: String?) {
        viewModelScope.launch {
            val newTrip = repository.createTrip(title, city, startDate, endDate)
            if (newTrip != null) {
                _tripCreationMessage.value = "Trip created successfully"
                loadTrips()
            } else {
                _tripCreationMessage.value = "Failed to create trip"
            }
        }
    }

    suspend fun createTripAndReturn(title: String, city: String?, startDate: String?, endDate: String?): Trip? {
        val newTrip = repository.createTrip(title, city, startDate, endDate)
        if (newTrip != null) {
            _tripCreationMessage.value = "Trip created successfully"
            loadTrips()
        } else {
            _tripCreationMessage.value = "Failed to create trip"
        }
        return newTrip
    }

    fun updateTrip(trip: Trip) {
        viewModelScope.launch {
            val updated = repository.updateTrip(trip)
            if (updated != null) {
                _tripCreationMessage.value = "Trip updated successfully"
                loadTrips()
            } else {
                _tripCreationMessage.value = "Failed to update trip"
            }
        }
    }

    fun updateTripRoutes(tripId: String, points: List<Point>) {
        viewModelScope.launch {
            repository.updateTripRoutes(tripId, points)
            loadTripById(tripId) // Refresh selected trip to reflect changes
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

    fun loadCurrentUserRole(tripId: String) {
        viewModelScope.launch {
            _currentUserRole.value = repository.getCurrentUserRole(tripId)
        }
    }

    fun loadTripMembersDisplay(tripId: String) {
        viewModelScope.launch {
            val members = repository.getTripMembersWithEmails(tripId)
            _tripMembersDisplay.value = members.map {
                TripMemberDisplay(userId = it.userId, email = it.email, role = it.role)
            }
        }
    }

    fun inviteUser(tripId: String, email: String, role: String) {
        viewModelScope.launch {
            if (!syncManager.isOnline()) {
                _shareMessage.value = "Sharing requires an internet connection"
                return@launch
            }
            val userId = repository.getUserIdByEmail(email)
            if (userId == null) {
                _shareMessage.value = "No account found for $email"
                return@launch
            }
            if (_tripMembersDisplay.value.any { it.userId == userId }) {
                _shareMessage.value = "$email is already a member of this trip"
                return@launch
            }
            val success = repository.addTripMember(tripId, userId, role)
            _shareMessage.value = if (success) "Invited $email as $role" else "Failed to invite user"
            if (success) loadTripMembersDisplay(tripId)
        }
    }

    fun shareMessageShown() {
        _shareMessage.value = null
    }

    suspend fun uploadTripCoverImage(tripId: String, uri: Uri, contentResolver: ContentResolver): Boolean {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes() ?: return false
            val fileName = "cover_${System.currentTimeMillis()}.jpg"
            val url = repository.uploadTripCoverImage(tripId, fileName, bytes)
            if (url != null) {
                loadTrips()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}