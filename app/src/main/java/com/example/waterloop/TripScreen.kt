package com.example.waterloop

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.waterloop.ui.trips.MarkerPhotoViewModel
import com.example.waterloop.ui.trips.MarkerViewModel
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// Geocode response models (used by marker creation dialog)
@Serializable
data class GeocodeResponse(
    val results: List<GeocodeResult>? = null
)

@Serializable
data class GeocodeResult(
    val lon: Double,
    val lat: Double,
    val rank: GeocodeRank? = null
)

@Serializable
data class GeocodeRank(
    val confidence: Double? = null,
    @SerialName("match_type")
    val matchType: String? = null
)

// Autocomplete response models
@Serializable
data class AutocompleteResponse(
    val results: List<AutocompleteResult>? = null
)

@Serializable
data class AutocompleteResult(
    val formatted: String? = null,
    val name: String? = null,
    val lat: Double,
    val lon: Double,
    @SerialName("address_line1")
    val addressLine1: String? = null,
    @SerialName("address_line2")
    val addressLine2: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(
    navController: NavController,
    tripId: String?,
    tripViewModel: TripViewModel = viewModel(),
    markerViewModel: MarkerViewModel = viewModel(),
    photoViewModel: MarkerPhotoViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val selectedTrip by tripViewModel.selectedTrip.collectAsState()
    val markers by markerViewModel.markers.collectAsState()
    val photos by photoViewModel.markerPhotos.collectAsState()

    var permissionRequestCount by remember { mutableStateOf(1) }
    var showMap by remember { mutableStateOf(false) }
    var showRequestPermissionButton by remember { mutableStateOf(false) }
    var showMarkerDialog by remember { mutableStateOf(false) }

    // Form fields for marker creation
    var markerTitle by remember { mutableStateOf("") }
    var markerDescription by remember { mutableStateOf("") }
    var markerCategory by remember { mutableStateOf("") }
    var markerNotes by remember { mutableStateOf("") }

    // Local error message state for the dialog
    var markerDialogError by remember { mutableStateOf<String?>(null) }

    var selectedMarkerId by remember { mutableStateOf<String?>(null) }
    var showMarkerSheet by remember { mutableStateOf(false) }
    var showAllMarkersSheet by remember { mutableStateOf(false) }
    var showEditMarkerDialog by remember { mutableStateOf(false) }

    // Derived state: Get the latest marker data from the list
    val selectedMarker = remember(markers, selectedMarkerId) {
        markers.find { it.id == selectedMarkerId }
    }

    // Form fields for marker editing
    var editMarkerTitle by remember { mutableStateOf("") }
    var editMarkerDescription by remember { mutableStateOf("") }
    var editMarkerCategory by remember { mutableStateOf("") }
    var editMarkerNotes by remember { mutableStateOf("") }

    var longPressedPoint by remember { mutableStateOf<Point?>(null) }

    // Search bar state
    var searchQuery by remember { mutableStateOf("") }
    var autocompleteSuggestions by remember { mutableStateOf<List<AutocompleteResult>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }

    val markerSheetState = rememberModalBottomSheetState()
    val allMarkersSheetState = rememberModalBottomSheetState()

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

    // Debounced autocomplete search
    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 3) {
            autocompleteSuggestions = emptyList()
            showSuggestions = false
            return@LaunchedEffect
        }
        delay(300) // debounce 300ms
        try {
            val encoded = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8.toString())
            val response = httpClient.get(
                "https://api.geoapify.com/v1/geocode/autocomplete?text=$encoded&limit=5&format=json&apiKey=${BuildConfig.GEOAPIFY_API_KEY}"
            ).body<AutocompleteResponse>()
            autocompleteSuggestions = response.results ?: emptyList()
            showSuggestions = autocompleteSuggestions.isNotEmpty()
        } catch (e: Exception) {
            autocompleteSuggestions = emptyList()
            showSuggestions = false
        }
    }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val markerId = selectedMarkerId ?: return@launch
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes() ?: return@launch
                    val fileName = "photo_${System.currentTimeMillis()}.jpg"

                    photoViewModel.uploadAndCreateMarkerPhoto(markerId, fileName, bytes)
                    snackbarHostState.showSnackbar("Photo uploaded successfully")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to upload photo")
                }
            }
        }
    }

    LaunchedEffect(tripId) {
        if (tripId != null) {
            tripViewModel.loadTripById(tripId)
            markerViewModel.loadMarkers(tripId)
        }
    }

    // Load photos whenever a marker is selected
    LaunchedEffect(selectedMarkerId) {
        selectedMarkerId?.let { id ->
            photoViewModel.loadMarkerPhotos(id)
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
                title = { Text(selectedTrip?.title ?: "Loading Trip...") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (mapViewportState.mapViewportStatus == ViewportStatus.Idle) {
                SmallFloatingActionButton(
                    onClick = {
                        mapViewportState.transitionToFollowPuckState()
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
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
                        },
                        onMapClickListener = {
                            // Dismiss suggestions when tapping the map
                            if (showSuggestions) {
                                showSuggestions = false
                                focusManager.clearFocus()
                            }
                            false
                        }
                    ) {
                        markers.forEach { marker ->
                            Marker(
                                point = Point.fromLngLat(marker.longitude, marker.latitude),
                                onClick = {
                                    selectedMarkerId = marker.id
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

                    // Search bar overlay on the map
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 30.dp)
                            .fillMaxWidth()
                    ) {
                        // Search bar
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search places") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = {
                                            searchQuery = ""
                                            autocompleteSuggestions = emptyList()
                                            showSuggestions = false
                                            focusManager.clearFocus()
                                        }) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Suggestions dropdown
                        if (showSuggestions && autocompleteSuggestions.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .heightIn(max = 300.dp),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column {
                                    autocompleteSuggestions.forEachIndexed { index, suggestion ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    // Move camera to selected location
                                                    mapViewportState.setCameraOptions {
                                                        center(
                                                            Point.fromLngLat(
                                                                suggestion.lon,
                                                                suggestion.lat
                                                            )
                                                        )
                                                        zoom(16.0)
                                                    }
                                                    // Pre-fill marker dialog
                                                    val title = suggestion.name
                                                        ?: suggestion.addressLine1
                                                        ?: suggestion.formatted
                                                        ?: "Unknown"
                                                    markerTitle = title
                                                    markerDescription = ""
                                                    markerCategory = ""
                                                    markerNotes = ""
                                                    longPressedPoint = Point.fromLngLat(
                                                        suggestion.lon,
                                                        suggestion.lat
                                                    )
                                                    showMarkerDialog = true

                                                    // Clear search
                                                    searchQuery = ""
                                                    autocompleteSuggestions = emptyList()
                                                    showSuggestions = false
                                                    focusManager.clearFocus()
                                                }
                                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                        ) {
                                            Text(
                                                text = suggestion.addressLine1
                                                    ?: suggestion.name
                                                    ?: "Unknown",
                                                style = MaterialTheme.typography.bodyLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!suggestion.addressLine2.isNullOrBlank()) {
                                                Text(
                                                    text = suggestion.addressLine2,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (index < autocompleteSuggestions.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { showAllMarkersSheet = true },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 24.dp)
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
                markerDialogError = null
            },
            title = { Text("Create Marker") },
            text = {
                Column {
                    OutlinedTextField(
                        value = markerTitle,
                        onValueChange = { markerTitle = it; markerDialogError = null },
                        label = { Text("Title / Location") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = markerDescription,
                        onValueChange = { markerDescription = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = markerCategory,
                        onValueChange = { markerCategory = it },
                        label = { Text("Category (e.g., Food, Sight)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = markerNotes,
                        onValueChange = { markerNotes = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (markerDialogError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = markerDialogError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                if (tripId == null) return@launch
                                val point = longPressedPoint
                                if (point != null) {
                                    markerViewModel.createMarker(
                                        tripId = tripId,
                                        title = markerTitle,
                                        latitude = point.latitude(),
                                        longitude = point.longitude(),
                                        description = markerDescription,
                                        category = markerCategory,
                                        notes = markerNotes
                                    )
                                    showMarkerDialog = false
                                    markerTitle = ""; markerDescription = ""; markerCategory = ""; markerNotes = ""
                                    longPressedPoint = null
                                    markerDialogError = null
                                } else {
                                    val sanitizedText = markerTitle.trim().lowercase()
                                    if (sanitizedText.isEmpty()) {
                                        markerDialogError = "Please enter a location name"
                                        return@launch
                                    }

                                    val encodedText = URLEncoder.encode(sanitizedText, StandardCharsets.UTF_8.toString())
                                    val response = httpClient.get(
                                        "https://api.geoapify.com/v1/geocode/search?text=${encodedText}&format=json&apiKey=${BuildConfig.GEOAPIFY_API_KEY}"
                                    ).body<GeocodeResponse>()

                                    if (!response.results.isNullOrEmpty()) {
                                        val result = response.results[0]
                                        val confidence = result.rank?.confidence ?: 0.0

                                        if (confidence >= 0.7) {
                                            markerViewModel.createMarker(
                                                tripId = tripId,
                                                title = markerTitle,
                                                latitude = result.lat,
                                                longitude = result.lon,
                                                description = markerDescription,
                                                category = markerCategory,
                                                notes = markerNotes
                                            )
                                            mapViewportState.setCameraOptions {
                                                center(Point.fromLngLat(result.lon, result.lat))
                                                zoom(16.0)
                                            }
                                            showMarkerDialog = false
                                            markerTitle = ""; markerDescription = ""; markerCategory = ""; markerNotes = ""
                                            markerDialogError = null
                                        } else {
                                            markerDialogError = "Unable to find a precise location for '$markerTitle'. Please recheck the name."
                                        }
                                    } else {
                                        markerDialogError = "Location not found. Please try a different name."
                                    }
                                }
                            } catch (e: Exception) {
                                markerDialogError = "Error: ${e.message}"
                            }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMarkerDialog = false
                    markerDialogError = null
                }) { Text("Cancel") }
            }
        )
    }

    if (showMarkerSheet && selectedMarker != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showMarkerSheet = false
                selectedMarkerId = null
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedMarker.title,
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (!selectedMarker.category.isNullOrBlank()) {
                            Text(
                                text = selectedMarker.category.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = {
                        editMarkerTitle = selectedMarker.title
                        editMarkerDescription = selectedMarker.description.orEmpty()
                        editMarkerCategory = selectedMarker.category.orEmpty()
                        editMarkerNotes = selectedMarker.notes.orEmpty()
                        showEditMarkerDialog = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = {
                        if (selectedMarker.id != null && tripId != null) {
                            markerViewModel.deleteMarker(selectedMarker.id, tripId)
                        }
                        showMarkerSheet = false
                        selectedMarkerId = null
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }

                if (!selectedMarker.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = selectedMarker.description.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }

                if (!selectedMarker.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Notes: ${selectedMarker.notes.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Multiple Photo Display
                if (photos.isNotEmpty()) {
                    LazyRow(modifier = Modifier.fillMaxWidth()) {
                        items(photos) { photo ->
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(200.dp)
                            ) {
                                AsyncImage(
                                    model = photo.photoUrl,
                                    contentDescription = "Marker photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // Delete Photo Button (Dustbin)
                                IconButton(
                                    onClick = {
                                        photo.id?.let { id ->
                                            photoViewModel.deleteMarkerPhoto(id, selectedMarker.id!!)
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete Photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.google_stock),
                        contentDescription = "Placeholder",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    val isVisited = selectedMarker.visited

                    // Button Animations
                    val buttonColor by animateColorAsState(
                        targetValue = if (isVisited) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                        label = "buttonColor"
                    )

                    var isPressed by remember { mutableStateOf(false) }
                    val buttonScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = 0.5f),
                        label = "buttonScale"
                    )

                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .scale(buttonScale),
                        colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                        onClick = {
                            scope.launch {
                                isPressed = true
                                markerViewModel.updateMarker(selectedMarker.copy(visited = !selectedMarker.visited))
                                delay(100)
                                isPressed = false
                            }
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isVisited) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            }
                            Crossfade(targetState = isVisited, label = "buttonText") { visited ->
                                Text(if (visited) "Visited" else "Mark as visited")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            imagePickerLauncher.launch("image/*")
                        }
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
                Column {
                    OutlinedTextField(
                        value = editMarkerTitle,
                        onValueChange = { editMarkerTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editMarkerDescription,
                        onValueChange = { editMarkerDescription = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editMarkerCategory,
                        onValueChange = { editMarkerCategory = it },
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editMarkerNotes,
                        onValueChange = { editMarkerNotes = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        markerViewModel.updateMarker(selectedMarker.copy(
                            title = editMarkerTitle,
                            description = editMarkerDescription,
                            category = editMarkerCategory,
                            notes = editMarkerNotes
                        ))
                        showEditMarkerDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditMarkerDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAllMarkersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAllMarkersSheet = false },
            sheetState = allMarkersSheetState
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(text = "All Markers", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                if (markers.isEmpty()) {
                    Text("No markers added yet")
                } else {
                    markers.forEach { marker ->
                        Button(
                            onClick = {
                                mapViewportState.setCameraOptions {
                                    center(Point.fromLngLat(marker.longitude, marker.latitude))
                                    zoom(16.0)
                                }
                                showAllMarkersSheet = false
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(marker.title)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}