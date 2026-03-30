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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.waterloop.ui.trips.MarkerPhotoViewModel
import com.example.waterloop.ui.trips.MarkerViewModel
import com.example.waterloop.ui.trips.TripMemberDisplay
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.waterloop.ui.theme.WaterloopBlue

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
    val autocompleteSuggestions by markerViewModel.autocompleteSuggestions.collectAsState()
    val currentUserRole by tripViewModel.currentUserRole.collectAsState()
    val tripMembersDisplay by tripViewModel.tripMembersDisplay.collectAsState()
    val shareMessage by tripViewModel.shareMessage.collectAsState()
    val canWrite = currentUserRole == "owner" || currentUserRole == "collaborator"

    var permissionRequestCount by remember { mutableStateOf(1) }
    var showMap by remember { mutableStateOf(false) }
    var showRequestPermissionButton by remember { mutableStateOf(false) }
    var showMarkerDialog by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var inviteEmail by remember { mutableStateOf("") }
    var selectedInviteRole by remember { mutableStateOf("collaborator") }
    val shareSheetState = rememberModalBottomSheetState()

    // Form fields for marker creation
    var markerTitle by remember { mutableStateOf("") }
    var markerDescription by remember { mutableStateOf("") }
    var markerCategory by remember { mutableStateOf("") }
    var markerNotes by remember { mutableStateOf("") }
    var fullscreenPhotoIndex by remember { mutableStateOf(-1) }

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
    var showSuggestions by remember { mutableStateOf(false) }

    val markerSheetState = rememberModalBottomSheetState()
    val allMarkersSheetState = rememberModalBottomSheetState()

    // Show suggestions when ViewModel returns results
    LaunchedEffect(autocompleteSuggestions) {
        showSuggestions = autocompleteSuggestions.isNotEmpty()
    }

    // Image Picker Launcher — delegates file I/O to ViewModel
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val markerId = selectedMarkerId ?: return@let
            photoViewModel.uploadMarkerPhoto(markerId, uri, context.contentResolver)
            scope.launch {
                snackbarHostState.showSnackbar("Photo uploaded successfully")
            }
        }
    }

    LaunchedEffect(tripId) {
        if (tripId != null) {
            tripViewModel.loadTripById(tripId)
            tripViewModel.loadCurrentUserRole(tripId)
            tripViewModel.loadTripMembersDisplay(tripId)
            markerViewModel.loadMarkers(tripId)
        }
    }

    LaunchedEffect(shareMessage) {
        shareMessage?.let {
            snackbarHostState.showSnackbar(it)
            tripViewModel.shareMessageShown()
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
                },
                actions = {
                    if (currentUserRole == "owner") {
                        IconButton(onClick = { showShareSheet = true }) {
                            Icon(Icons.Default.Share, contentDescription = "Share trip")
                        }
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
                            if (canWrite) {
                                longPressedPoint = point
                                showMarkerDialog = true
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("You have view-only access to this trip")
                                }
                            }
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
                                color = if (marker.visited) Color.Green else WaterloopBlue,
                                onClick = {
                                    selectedMarkerId = marker.id
                                    showMarkerSheet = true
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
                                onValueChange = {
                                    searchQuery = it
                                    markerViewModel.searchPlaces(it)
                                },
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
                                            markerViewModel.clearSuggestions()
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
                                                    // Always move camera to selected location
                                                    mapViewportState.setCameraOptions {
                                                        center(
                                                            Point.fromLngLat(
                                                                suggestion.lon,
                                                                suggestion.lat
                                                            )
                                                        )
                                                        zoom(16.0)
                                                    }
                                                    // Only open marker dialog if user can write
                                                    if (canWrite) {
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
                                                    }
                                                    // Clear search
                                                    searchQuery = ""
                                                    markerViewModel.clearSuggestions()
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
                                    if (markerTitle.isBlank()) {
                                        markerDialogError = "Please enter a location name"
                                        return@launch
                                    }

                                    val result = markerViewModel.geocodeLocation(markerTitle)
                                    if (result != null) {
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
                                        markerDialogError = "Location not found or not precise enough. Please try a different name."
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
                    if (canWrite) {
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
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(photo.photoUrl) {
                                            detectTapGestures(
                                                onTap = {
                                                    fullscreenPhotoIndex = photos.indexOf(photo)
                                                }
                                            )
                                        },
                                    contentScale = ContentScale.Crop
                                )
                                // Delete Photo Button — only visible to writers
                                if (canWrite) {
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
                    }
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.google_stock_marker),
                        contentDescription = "Placeholder",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                if (canWrite) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val isVisited = selectedMarker.visited

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
                            onClick = { imagePickerLauncher.launch("image/*") }
                        ) {
                            Text("Add Photos")
                        }
                    }
                } else {
                    // Read-only visited status for viewers
                    val visitedColor = if (selectedMarker.visited) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedMarker.visited) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = visitedColor, modifier = Modifier.padding(end = 4.dp))
                        }
                        Text(
                            text = if (selectedMarker.visited) "Visited" else "Not visited",
                            color = visitedColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
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

    // Share Sheet — only owners can open this
    if (showShareSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showShareSheet = false
                inviteEmail = ""
            },
            sheetState = shareSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text("Share Trip", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Invite someone by their account email",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = inviteEmail,
                    onValueChange = { inviteEmail = it },
                    label = { Text("Email address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Role", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "collaborator" to "Collaborator\n(Read + Write)",
                        "viewer" to "Viewer\n(Read Only)"
                    ).forEachIndexed { index, (value, label) ->
                        OutlinedButton(
                            onClick = { selectedInviteRole = value },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(
                                width = if (selectedInviteRole == value) 2.dp else 1.dp,
                                color = if (selectedInviteRole == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selectedInviteRole == value)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color.Transparent,
                                contentColor = if (selectedInviteRole == value)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        }
                        if (index == 0) Spacer(modifier = Modifier.width(8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (tripId != null && inviteEmail.isNotBlank()) {
                            tripViewModel.inviteUser(tripId, inviteEmail.trim(), selectedInviteRole)
                            inviteEmail = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = inviteEmail.isNotBlank()
                ) {
                    Text("Invite")
                }

                // Inline feedback — visible even when sheet is open
                if (shareMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = shareMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (shareMessage!!.startsWith("Invited"))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }

                if (tripMembersDisplay.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Members", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    tripMembersDisplay.forEach { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = member.email ?: member.userId.take(8) + "…",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = member.role.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                color = when (member.role) {
                                    "owner" -> MaterialTheme.colorScheme.primary
                                    "collaborator" -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    // Fullscreen Photo Viewer
    if (fullscreenPhotoIndex >= 0 && photos.isNotEmpty()) {
        Dialog(
            onDismissRequest = { fullscreenPhotoIndex = -1 },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(0.dp))
                    .background(Color.Black)
            ) {
                val pagerState = rememberPagerState(
                    initialPage = fullscreenPhotoIndex,
                    pageCount = { photos.size }
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    AsyncImage(
                        model = photos[page].photoUrl,
                        contentDescription = "Photo ${page + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = { fullscreenPhotoIndex = -1 }
                                )
                            },
                        contentScale = ContentScale.Fit
                    )
                }

                // Close button
                IconButton(
                    onClick = { fullscreenPhotoIndex = -1 },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                // Photo counter
                Text(
                    text = "${pagerState.currentPage + 1} / ${photos.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
