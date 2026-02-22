package com.example.waterloop

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.waterloop.ui.trips.TripViewModel
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.annotation.Marker
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.ViewportStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Serializable
data class GeocodeResponse(
    val results: List<GeocodeResult>? = null
)

@Serializable
data class GeocodeResult(
    val lon: Double,
    val lat: Double
)

data class MapMarker(
    val point: Point,
    val text: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(navController: NavController, tripId: String?, viewModel: TripViewModel = viewModel()) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val selectedTrip by viewModel.selectedTrip.collectAsState()
    var permissionRequestCount by remember {
        mutableStateOf(1)
    }
    var showMap by remember {
        mutableStateOf(false)
    }
    var showRequestPermissionButton by remember {
        mutableStateOf(false)
    }
    var showMarkerDialog by remember {
        mutableStateOf(false)
    }
    var markerText by remember {
        mutableStateOf("")
    }
    val markers = remember { mutableStateListOf<MapMarker>() }
    val httpClient = remember {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                    isLenient = true
                })
            }
        }
    }

    LaunchedEffect(tripId) {
        if (tripId != null) {
            viewModel.loadTripById(tripId)
        }
    }
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            zoom(0.0)
            pitch(0.0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedTrip?.title ?: "Failed to get data") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMarkerDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Marker")
                    }
                }
            )
        },
        floatingActionButton = {
            if (mapViewportState.mapViewportStatus == ViewportStatus.Idle) {
                FloatingActionButton(
                    onClick = {
                        mapViewportState.transitionToFollowPuckState()
                    }
                ) {
                    Image(
                        painter = painterResource(id = android.R.drawable.ic_menu_mylocation),
                        contentDescription = "Locate button"
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)){
            RequestLocationPermission(
                requestCount = permissionRequestCount,
                onPermissionDenied = {
                    scope.launch {
                        snackbarHostState.showSnackbar("You need to accept location permissions.")
                    }
                    showRequestPermissionButton = true
                },
                onPermissionReady = {
                    showRequestPermissionButton = false
                    showMap = true
                }
            )
            if (showMap) {
                MapboxMap(
                    Modifier.fillMaxSize(),
                    mapViewportState = mapViewportState,
                ) {
                    markers.forEach { marker ->
                        Marker(
                            point = marker.point
                        )
                    }
                    MapEffect(Unit) { mapView ->
                        mapView.location.updateSettings {
                            locationPuck = createDefault2DPuck(withBearing = true)
                            puckBearingEnabled = true
                            puckBearing = PuckBearing.HEADING
                            enabled = true
                        }
                        mapViewportState.transitionToFollowPuckState()
                    }
                }
            }
            if (showRequestPermissionButton) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.align(Alignment.Center)) {
                        Button(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            onClick = {
                                permissionRequestCount += 1
                            }
                        ) {
                            Text("Request permission again ($permissionRequestCount)")
                        }
                        Button(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    )
                                )
                            }
                        ) {
                            Text("Show App Settings page")
                        }
                    }
                }
            }
        }
    }

    if (showMarkerDialog) {
        AlertDialog(
            onDismissRequest = { showMarkerDialog = false },
            title = { Text("Create Marker") },
            text = {
                Column {
                    OutlinedTextField(
                        value = markerText,
                        onValueChange = { markerText = it },
                        label = { Text("Enter Location Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val encodedText = URLEncoder.encode(markerText, StandardCharsets.UTF_8.toString())
                                val response = httpClient.get(
                                    "https://api.geoapify.com/v1/geocode/search?text=${encodedText}&format=json&apiKey=${BuildConfig.GEOAPIFY_API_KEY}"
                                ).body<GeocodeResponse>()
                                
                                if (!response.results.isNullOrEmpty()) {
                                    val result = response.results[0]
                                    val newMarker = MapMarker(
                                        point = Point.fromLngLat(result.lon, result.lat),
                                        text = markerText
                                    )
                                    markers.add(newMarker)
                                    showMarkerDialog = false
                                    markerText = ""
                                } else {
                                    snackbarHostState.showSnackbar("Location not found")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Failed to geocode location: ${e.message}")
                            }
                        }
                    }
                ) {
                    Text("Create Marker")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMarkerDialog = false
                        markerText = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
