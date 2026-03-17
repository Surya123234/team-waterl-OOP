package com.example.waterloop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.waterloop.ui.theme.WaterlOOPTheme
import com.example.waterloop.data.model.Trip
import com.example.waterloop.ui.trips.TripViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaterlOOPTheme(darkTheme = true) {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(navController = navController)
                    }
                    composable("map") {
                        MapScreen(navController = navController)
                    }
                    composable("single_trip/{tripId}") { backStackEntry ->
                        TripScreen(
                            navController = navController,
                            tripId = backStackEntry.arguments?.getString("tripId")
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController) {
    val viewModel: TripViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val trips by viewModel.trips.collectAsState()
    val tripCreationMessage by viewModel.tripCreationMessage.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var tripToDelete by remember { mutableStateOf<Trip?>(null) }
    var tripToEdit by remember { mutableStateOf<Trip?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Dialog state
    var tripTitle by remember { mutableStateOf("") }
    var tripCity by remember { mutableStateOf("") }
    var tripStartDate by remember { mutableStateOf("") }
    var tripEndDate by remember { mutableStateOf("") }

    // Cover image state for dialogs
    var selectedCoverImageUri by remember { mutableStateOf<Uri?>(null) }

    // Date Picker state
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val datePickerStateStart = rememberDatePickerState()
    val datePickerStateEnd = rememberDatePickerState()

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") } }

    // Image picker launcher — just stores the URI for preview, doesn't upload yet
    val coverImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedCoverImageUri = it }
    }

    LaunchedEffect(Unit) {
        viewModel.loadTrips()
    }

    LaunchedEffect(tripCreationMessage) {
        tripCreationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Create Trip Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable {
                        tripTitle = ""; tripCity = ""; tripStartDate = ""; tripEndDate = ""
                        selectedCoverImageUri = null
                        showCreateDialog = true
                    },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.moraine_lake),
                        contentDescription = "Moraine Lake",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Create New Trip",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
            }

            LazyColumn {
                items(trips) { trip ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(vertical = 8.dp)
                            .clickable {
                                viewModel.setSelectedTrip(trip)
                                navController.navigate("single_trip/${trip.id}")
                            },
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (!trip.coverImageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = trip.coverImageUrl,
                                    contentDescription = trip.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.toronto),
                                    contentDescription = trip.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Column {
                                    Text(
                                        text = trip.title,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Trip to ${trip.city}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                    if (!trip.startDate.isNullOrBlank()) {
                                        Text(
                                            text = "${trip.startDate} - ${trip.endDate.orEmpty()}",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                            // Action Icons (Edit and Delete only)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        tripToEdit = trip
                                        tripTitle = trip.title
                                        tripCity = trip.city ?: ""
                                        tripStartDate = trip.startDate ?: ""
                                        tripEndDate = trip.endDate ?: ""
                                        selectedCoverImageUri = null
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit trip",
                                        tint = Color.White
                                    )
                                }
                                IconButton(
                                    onClick = { tripToDelete = trip }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete trip",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Trip Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                selectedCoverImageUri = null
            },
            title = { Text("Plan a New Journey") },
            text = {
                Column {
                    OutlinedTextField(
                        value = tripTitle,
                        onValueChange = { tripTitle = it },
                        label = { Text("Trip Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tripCity,
                        onValueChange = { tripCity = it },
                        label = { Text("City") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        OutlinedTextField(
                            value = tripStartDate,
                            onValueChange = { tripStartDate = it },
                            label = { Text("Start Date") },
                            placeholder = { Text("YYYY-MM-DD") },
                            modifier = Modifier.weight(1f),
                            trailingIcon = {
                                IconButton(onClick = { showStartDatePicker = true }) {
                                    Icon(Icons.Default.DateRange, contentDescription = "Pick Start Date")
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = tripEndDate,
                            onValueChange = { tripEndDate = it },
                            label = { Text("End Date") },
                            placeholder = { Text("YYYY-MM-DD") },
                            modifier = Modifier.weight(1f),
                            trailingIcon = {
                                IconButton(onClick = { showEndDatePicker = true }) {
                                    Icon(Icons.Default.DateRange, contentDescription = "Pick End Date")
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Cover Image Picker
                    Text(
                        text = "Cover Image",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (selectedCoverImageUri != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { coverImagePickerLauncher.launch("image/*") }
                        ) {
                            AsyncImage(
                                model = selectedCoverImageUri,
                                contentDescription = "Selected cover image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = { selectedCoverImageUri = null },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Remove")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { coverImagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Choose Cover Image")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch {
                        // Create the trip first
                        val newTrip = viewModel.createTripAndReturn(tripTitle, tripCity, tripStartDate, tripEndDate)
                        if (newTrip?.id != null && selectedCoverImageUri != null) {
                            // Upload cover image with the new trip's ID
                            try {
                                val inputStream = context.contentResolver.openInputStream(selectedCoverImageUri!!)
                                val bytes = inputStream?.readBytes()
                                if (bytes != null) {
                                    val fileName = "cover_${System.currentTimeMillis()}.jpg"
                                    viewModel.uploadTripCoverImage(newTrip.id, fileName, bytes)
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Trip created, but cover image upload failed")
                            }
                        }
                        selectedCoverImageUri = null
                        showCreateDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    selectedCoverImageUri = null
                }) { Text("Cancel") }
            }
        )
    }

    // Edit Trip Dialog
    tripToEdit?.let { trip ->
        AlertDialog(
            onDismissRequest = {
                tripToEdit = null
                selectedCoverImageUri = null
            },
            title = { Text("Edit Trip Details") },
            text = {
                Column {
                    OutlinedTextField(
                        value = tripTitle,
                        onValueChange = { tripTitle = it },
                        label = { Text("Trip Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tripCity,
                        onValueChange = { tripCity = it },
                        label = { Text("City") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        OutlinedTextField(
                            value = tripStartDate,
                            onValueChange = { tripStartDate = it },
                            label = { Text("Start Date") },
                            placeholder = { Text("YYYY-MM-DD") },
                            modifier = Modifier.weight(1f),
                            trailingIcon = {
                                IconButton(onClick = { showStartDatePicker = true }) {
                                    Icon(Icons.Default.DateRange, contentDescription = "Pick Start Date")
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = tripEndDate,
                            onValueChange = { tripEndDate = it },
                            label = { Text("End Date") },
                            placeholder = { Text("YYYY-MM-DD") },
                            modifier = Modifier.weight(1f),
                            trailingIcon = {
                                IconButton(onClick = { showEndDatePicker = true }) {
                                    Icon(Icons.Default.DateRange, contentDescription = "Pick End Date")
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Cover Image Picker
                    Text(
                        text = "Cover Image",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (selectedCoverImageUri != null) {
                        // User picked a new image
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { coverImagePickerLauncher.launch("image/*") }
                        ) {
                            AsyncImage(
                                model = selectedCoverImageUri,
                                contentDescription = "Selected cover image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = { selectedCoverImageUri = null },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Remove")
                        }
                    } else if (!trip.coverImageUrl.isNullOrBlank()) {
                        // Show existing cover image
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { coverImagePickerLauncher.launch("image/*") }
                        ) {
                            AsyncImage(
                                model = trip.coverImageUrl,
                                contentDescription = "Current cover image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = { coverImagePickerLauncher.launch("image/*") },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Change")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { coverImagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Choose Cover Image")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch {
                        // Upload new cover image if one was selected
                        if (selectedCoverImageUri != null && trip.id != null) {
                            try {
                                val inputStream = context.contentResolver.openInputStream(selectedCoverImageUri!!)
                                val bytes = inputStream?.readBytes()
                                if (bytes != null) {
                                    val fileName = "cover_${System.currentTimeMillis()}.jpg"
                                    viewModel.uploadTripCoverImage(trip.id, fileName, bytes)
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Failed to upload cover image")
                            }
                        }
                        // Update trip details
                        viewModel.updateTrip(trip.copy(
                            title = tripTitle,
                            city = tripCity,
                            startDate = tripStartDate,
                            endDate = tripEndDate
                        ))
                        selectedCoverImageUri = null
                        tripToEdit = null
                    }
                }) { Text("Save Changes") }
            },
            dismissButton = {
                TextButton(onClick = {
                    tripToEdit = null
                    selectedCoverImageUri = null
                }) { Text("Cancel") }
            }
        )
    }

    // Start Date Picker
    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerStateStart.selectedDateMillis?.let {
                        tripStartDate = dateFormatter.format(Date(it))
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerStateStart)
        }
    }

    // End Date Picker
    if (showEndDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerStateEnd.selectedDateMillis?.let {
                        tripEndDate = dateFormatter.format(Date(it))
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerStateEnd)
        }
    }

    // Delete Confirmation
    tripToDelete?.let { trip ->
        AlertDialog(
            onDismissRequest = { tripToDelete = null },
            title = { Text("Delete Trip") },
            text = { Text("Are you sure you want to delete '${trip.title}'? All markers and photos will be lost.") },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch { viewModel.deleteTrip(trip.id) }
                    tripToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { tripToDelete = null }) { Text("Cancel") } }
        )
    }
}