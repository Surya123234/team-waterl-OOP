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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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
    var selectedMarker by remember {
        mutableStateOf<MapMarker?>(null)
    }
    var showMarkerSheet by remember {
        mutableStateOf(false)
    }
    var showAllMarkersSheet by remember {
        mutableStateOf(false)
    }
    var showEditMarkerDialog by remember {
        mutableStateOf(false)
    }
    var editMarkerText by remember {
        mutableStateOf("")
    }
    var longPressedPoint by remember {
        mutableStateOf<Point?>(null)
    }
    val markerSheetState = rememberModalBottomSheetState()
    val allMarkersSheetState = rememberModalBottomSheetState()
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
        containerColor = MaterialTheme.colorScheme.background,
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
        Box(modifier = Modifier.padding(paddingValues)) {
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
                Box(modifier = Modifier.fillMaxSize()) {
                    MapboxMap(
                        Modifier.fillMaxSize(),
                        mapViewportState = mapViewportState,
                        onMapLongClickListener = { point ->
                            longPressedPoint = point
                            showMarkerDialog = true
                            true
                        }
                    ) {
                        markers.forEach { marker ->
                            Marker(
                                point = marker.point,
                                onClick = {
                                    selectedMarker = marker
                                    showMarkerSheet = true
                                    true
                                }
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
                    Button(
                        onClick = { showAllMarkersSheet = true },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 16.dp)
                    ) {
                        Text("View All Markers")
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
            onDismissRequest = {
                showMarkerDialog = false
                longPressedPoint = null
            },
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
                                val point = longPressedPoint
                                if (point != null) {
                                    // Long press - use the pressed coordinates
                                    val newMarker = MapMarker(
                                        point = point,
                                        text = markerText
                                    )
                                    markers.add(newMarker)
                                    mapViewportState.setCameraOptions {
                                        center(newMarker.point)
                                        zoom(16.0)
                                    }
                                    showMarkerDialog = false
                                    markerText = ""
                                    longPressedPoint = null
                                } else {
                                    // Button press - geocode the location
                                    val encodedText = URLEncoder.encode(
                                        markerText,
                                        StandardCharsets.UTF_8.toString()
                                    )
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
                                        mapViewportState.setCameraOptions {
                                            center(newMarker.point)
                                            zoom(16.0)
                                        }
                                        showMarkerDialog = false
                                        markerText = ""
                                    } else {
                                        snackbarHostState.showSnackbar("Location not found")
                                    }
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
                        longPressedPoint = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showMarkerSheet && selectedMarker != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showMarkerSheet = false
                selectedMarker = null
            },
            sheetState = markerSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedMarker?.text.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        editMarkerText = selectedMarker?.text.orEmpty()
                        showEditMarkerDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit marker"
                        )
                    }
                    IconButton(onClick = {
                        selectedMarker?.let { marker ->
                            markers.remove(marker)
                            showMarkerSheet = false
                            selectedMarker = null
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete marker"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Image(
                    painter = painterResource(id = R.drawable.google_dublin),
                    contentDescription = "Marker image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {}
                    ) {
                        Text("Mark as visited")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {}
                    ) {
                        Text("Add Photos")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showEditMarkerDialog && selectedMarker != null) {
        AlertDialog(
            onDismissRequest = { showEditMarkerDialog = false },
            title = { Text("Edit Marker") },
            text = {
                OutlinedTextField(
                    value = editMarkerText,
                    onValueChange = { editMarkerText = it },
                    label = { Text("Marker Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedMarker?.let { oldMarker ->
                            val index = markers.indexOf(oldMarker)
                            if (index != -1) {
                                markers[index] = MapMarker(
                                    point = oldMarker.point,
                                    text = editMarkerText
                                )
                                selectedMarker = markers[index]
                            }
                        }
                        showEditMarkerDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditMarkerDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAllMarkersSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showAllMarkersSheet = false
            },
            sheetState = allMarkersSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "All Markers",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (markers.isEmpty()) {
                    Text("No markers added yet")
                } else {
                    markers.forEach { marker ->
                        Button(
                            onClick = {
                                mapViewportState.setCameraOptions {
                                    center(marker.point)
                                    zoom(16.0)
                                }
                                showAllMarkersSheet = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(marker.text)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
