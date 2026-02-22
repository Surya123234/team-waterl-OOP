package com.example.waterloop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.waterloop.ui.theme.WaterlOOPTheme
import com.example.waterloop.ui.trips.TripViewModel
import com.example.waterloop.ui.trips.MarkerViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaterlOOPTheme {
                val viewModel = TripViewModel()
                val coroutineScope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }
                val trips by viewModel.trips.collectAsState()
                val tripCreationMessage by viewModel.tripCreationMessage.collectAsState()
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val markerViewModel = MarkerViewModel()
                val markers by markerViewModel.markers.collectAsState()

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
                        LazyColumn {
                            items(trips) { trip ->
                                Text(text = trip.title)
                            }
                        }
                        LazyColumn {
                            items(markers) { marker ->
                                Text(
                                    text = marker.title
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
