package com.example.waterloop.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waterloop.BuildConfig
import com.example.waterloop.WaterlOOPApplication
import com.example.waterloop.data.model.AutocompleteResponse
import com.example.waterloop.data.model.AutocompleteResult
import com.example.waterloop.data.model.GeocodeResponse
import com.example.waterloop.data.model.GeocodeResult
import com.example.waterloop.data.model.Marker
import com.example.waterloop.data.repository.MarkerRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MarkerViewModel : ViewModel() {

    private val repository = MarkerRepository()
    private val syncManager get() = WaterlOOPApplication.instance.syncManager

    private val _markers = MutableStateFlow<List<Marker>>(emptyList())
    val markers: StateFlow<List<Marker>> = _markers

    private val _autocompleteSuggestions = MutableStateFlow<List<AutocompleteResult>>(emptyList())
    val autocompleteSuggestions: StateFlow<List<AutocompleteResult>> = _autocompleteSuggestions

    private var searchJob: Job? = null

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                isLenient = true
            })
        }
    }

    fun loadMarkers(tripId: String) {
        viewModelScope.launch {
            // show cached markers immediately
            _markers.value = repository.getMarkers(tripId)

            // then sync and refresh
            launch {
                try {
                    syncManager.sync()
                    _markers.value = repository.getMarkers(tripId)
                } catch (_: Exception) { /* offline — cached data is fine */ }
            }
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
            _markers.value = repository.getMarkers(tripId)
        }
    }

    fun updateMarker(marker: Marker) {
        viewModelScope.launch {
            repository.updateMarker(marker)
            _markers.value = repository.getMarkers(marker.tripId)
        }
    }

    fun deleteMarker(markerId: String, tripId: String) {
        viewModelScope.launch {
            repository.deleteMarker(markerId)
            _markers.value = repository.getMarkers(tripId)
        }
    }

    // Debounced autocomplete search — requires network
    fun searchPlaces(query: String) {
        searchJob?.cancel()
        if (query.length < 3) {
            _autocompleteSuggestions.value = emptyList()
            return
        }
        if (!syncManager.isOnline()) {
            _autocompleteSuggestions.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            try {
                val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
                val response = httpClient.get(
                    "https://api.geoapify.com/v1/geocode/autocomplete?text=$encoded&limit=5&format=json&apiKey=${BuildConfig.GEOAPIFY_API_KEY}"
                ).body<AutocompleteResponse>()
                _autocompleteSuggestions.value = response.results ?: emptyList()
            } catch (e: Exception) {
                _autocompleteSuggestions.value = emptyList()
            }
        }
    }

    fun clearSuggestions() {
        searchJob?.cancel()
        _autocompleteSuggestions.value = emptyList()
    }

    // Geocode a location name — requires network
    suspend fun geocodeLocation(text: String): GeocodeResult? {
        if (!syncManager.isOnline()) return null
        return try {
            val encoded = URLEncoder.encode(text.trim().lowercase(), StandardCharsets.UTF_8.toString())
            val response = httpClient.get(
                "https://api.geoapify.com/v1/geocode/search?text=$encoded&format=json&apiKey=${BuildConfig.GEOAPIFY_API_KEY}"
            ).body<GeocodeResponse>()
            val result = response.results?.firstOrNull()
            if (result != null && (result.rank?.confidence ?: 0.0) >= 0.7) result else null
        } catch (e: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}