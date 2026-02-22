package com.example.waterloop

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.waterloop.ui.markers.MarkerPhotoViewModel
import com.example.waterloop.ui.trips.TripViewModel
import com.example.waterloop.ui.theme.WaterlOOPTheme
import com.example.waterloop.ui.trips.MarkerViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaterlOOPTheme {
                val viewModel = TripViewModel()
                val markerViewModel = MarkerViewModel()
                val markerPhotoViewModel = MarkerPhotoViewModel()
                val coroutineScope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }

                val trips by viewModel.trips.collectAsState()
                val tripCreationMessage by viewModel.tripCreationMessage.collectAsState()
                val markers by markerViewModel.markers.collectAsState()
                val markerPhotos by markerPhotoViewModel.markerPhotos.collectAsState()

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                LaunchedEffect(tripCreationMessage) {
                    tripCreationMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.messageShown()
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.createTrip("Test Trip", "Toronto")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            interactionSource = interactionSource
                        ) {
                            Text(
                                text = "Create Trip",
                                color = if (isPressed) Color.Gray else Color.White
                            )
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.loadTrips()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Load Your Trips")
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {

                                    markerViewModel.createMarker(
                                        "74460e74-3ad3-4647-94d6-2590a2d7ca96"
                                    )

                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Create Marker")
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    markerViewModel.loadMarkers(
                                        "74460e74-3ad3-4647-94d6-2590a2d7ca96"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Load Markers")
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    markerViewModel.updateFirstMarker("Updated Marker")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Update First Marker")
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    markerViewModel.deleteFirstMarker()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete First Marker")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- MarkerPhoto Buttons ---
                        Text("MarkerPhoto Actions", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    markerPhotoViewModel.loadMarkerPhotos("d30a57c2-bfe0-438f-b293-3dd8cb7e3c1b")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Load Marker Photos")
                        }

                        UploadPhotoButton(markerPhotoViewModel)

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    if (markerPhotos.isNotEmpty()) {
                                        val photo = markerPhotos.first()
                                        markerPhotoViewModel.updateMarkerPhoto(
                                            photoId = photo.id!!,
                                            markerId = "1c3642ca-5d00-442f-b817-38c9f83dcd87",
                                            newUrl = "https://example.com/updated-photo.jpg"
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Update First Photo") }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    if (markerPhotos.isNotEmpty()) {
                                        val photo = markerPhotos.first()
                                        markerPhotoViewModel.deleteMarkerPhoto(
                                            photo.id!!,
                                            "1c3642ca-5d00-442f-b817-38c9f83dcd87"
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Delete First Photo") }

                        Spacer(modifier = Modifier.height(16.dp))

                        LazyColumn { items(trips) { trip -> Text(text = trip.title) } }

                        LazyColumn { items(markers) { marker -> Text(text = marker.title) } }

                        LazyColumn {
                            items(markerPhotos) { photo ->
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {

                                    Text("Photo ID: ${photo.id}")

                                    AsyncImage(
                                        model = photo.photoUrl,
                                        contentDescription = "Marker Photo",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- UploadPhotoButton Composable ---
@Composable
fun UploadPhotoButton(markerPhotoViewModel: MarkerPhotoViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val fileBytes = inputStream!!.readBytes()
                val fileName = "photo_${System.currentTimeMillis()}.jpg"

                coroutineScope.launch {
                    markerPhotoViewModel.uploadAndCreateMarkerPhoto(
                        "d30a57c2-bfe0-438f-b293-3dd8cb7e3c1b",
                        fileName,
                        fileBytes
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Button(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
        Text("Pick Image from Gallery")
    }
}