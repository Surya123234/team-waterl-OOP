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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.waterloop.ui.theme.WaterlOOPTheme
import com.example.waterloop.ui.theme.WaterloopBlue
import com.example.waterloop.ui.theme.WaterloopGold
import com.example.waterloop.ui.theme.WaterloopDarkBackground
import com.example.waterloop.data.sync.ConnectivityObserver
import com.example.waterloop.data.model.Trip
import com.example.waterloop.ui.trips.TripViewModel
import com.example.waterloop.ui.trips.AuthViewModel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.PaddingValues

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaterlOOPTheme(darkTheme = true) {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = viewModel()
                val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

                // react to auth state changes and navigate accordingly.
                // popUpTo clears the back stack so the user can't press back to loading/auth.
                LaunchedEffect(isLoggedIn) {
                    when (isLoggedIn) {
                        true -> navController.navigate("main") {
                            popUpTo("loading") { inclusive = true }
                        }
                        false -> navController.navigate("auth") {
                            popUpTo("loading") { inclusive = true }
                        }
                        null -> { /* still checking session, stay on loading screen */ }
                    }
                }

                // app always starts on loading while we check auth state
                NavHost(navController = navController, startDestination = "loading") {
                    composable("loading") {
                        LoadingScreen()
                    }
                    composable("auth") {
                        AuthScreen(authViewModel = authViewModel)
                    }
                    composable("main") {
                        MainScreen(navController = navController, authViewModel = authViewModel)
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
fun MainScreen(navController: NavController, authViewModel: AuthViewModel) {
    val viewModel: TripViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val trips by viewModel.trips.collectAsState()
    val tripCreationMessage by viewModel.tripCreationMessage.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var tripToDelete by remember { mutableStateOf<Trip?>(null) }
    var tripToEdit by remember { mutableStateOf<Trip?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Monitor network status
    val connectivityObserver = remember { ConnectivityObserver(context) }
    val isOnline by connectivityObserver.observe().collectAsState(initial = true)
    var showOfflineSignOutWarning by remember { mutableStateOf(false) }

    // Dialog state
    var tripTitle by remember { mutableStateOf("") }
    var tripCity by remember { mutableStateOf("") }
    var tripStartDate by remember { mutableStateOf("") }
    var tripEndDate by remember { mutableStateOf("") }
    var tripStatus by remember { mutableStateOf("planned") }

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

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            viewModel.loadTrips()
        }
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
            // user info row — email on the left, sign out on the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = authViewModel.getCurrentUserEmail() ?: "",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    if (isOnline) {
                        authViewModel.signOut()
                    } else {
                        showOfflineSignOutWarning = true
                    }
                }) {
                    Text(
                        text = "Sign Out",
                        color = WaterloopBlue,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Create Trip Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable {
                        tripTitle = ""; tripCity = ""; tripStartDate = ""; tripEndDate = ""
                        tripStatus = "planned"
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
                                    painter = painterResource(id = R.drawable.google_stock_location),
                                    contentDescription = trip.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.7f)
                                            ),
                                            startY = 100f
                                        )
                                    )
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
                            
                            // Status Badge
                            val statusColor = when (trip.status.lowercase()) {
                                "planned" -> WaterloopGold
                                "active" -> Color(0xFF4CAF50)
                                "finished" -> Color.Gray
                                else -> WaterloopBlue
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .background(statusColor, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = trip.status.uppercase(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
                                        tripStatus = trip.status
                                        selectedCoverImageUri = null
                                    },
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit trip",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { tripToDelete = trip },
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete trip",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Alert Dialog for offline user trying to sign out
    if (showOfflineSignOutWarning) {
        AlertDialog(
            onDismissRequest = { showOfflineSignOutWarning = false },
            title = { Text("You're Offline") },
            text = { Text("You won't be able to sign back in without an internet connection. Are you sure you want to sign out?") },
            confirmButton = {
                Button(onClick = {
                    showOfflineSignOutWarning = false
                    authViewModel.signOut()
                }) {
                    Text("Sign Out Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOfflineSignOutWarning = false }) {
                    Text("Cancel")
                }
            }
        )
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
                    
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("planned", "active", "finished").forEach { status ->
                            val isSelected = tripStatus == status
                            val color = when (status) {
                                "planned" -> WaterloopGold
                                "active" -> Color(0xFF4CAF50)
                                "finished" -> Color.Gray
                                else -> WaterloopBlue
                            }
                            OutlinedButton(
                                onClick = { tripStatus = status },
                                colors = if (isSelected) ButtonDefaults.outlinedButtonColors(containerColor = color.copy(alpha = 0.2f), contentColor = color) 
                                         else ButtonDefaults.outlinedButtonColors(),
                                border = BorderStroke(1.dp, if (isSelected) color else Color.Gray),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(status.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, maxLines = 1)
                            }
                        }
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
                        val newTrip = viewModel.createTripAndReturn(tripTitle, tripCity, tripStartDate, tripEndDate, tripStatus)
                        if (newTrip?.id != null && selectedCoverImageUri != null) {
                            viewModel.uploadTripCoverImage(newTrip.id, selectedCoverImageUri!!, context.contentResolver)
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

                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("planned", "active", "finished").forEach { status ->
                            val isSelected = tripStatus == status
                            val color = when (status) {
                                "planned" -> WaterloopGold
                                "active" -> Color(0xFF4CAF50)
                                "finished" -> Color.Gray
                                else -> WaterloopBlue
                            }
                            OutlinedButton(
                                onClick = { tripStatus = status },
                                colors = if (isSelected) ButtonDefaults.outlinedButtonColors(containerColor = color.copy(alpha = 0.2f), contentColor = color) 
                                         else ButtonDefaults.outlinedButtonColors(),
                                border = BorderStroke(1.dp, if (isSelected) color else Color.Gray),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(status.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, maxLines = 1)
                            }
                        }
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
                    } else if (!trip.coverImageUrl.isNullOrBlank()) {
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
                        if (selectedCoverImageUri != null && trip.id != null) {
                            viewModel.uploadTripCoverImage(trip.id, selectedCoverImageUri!!, context.contentResolver)
                        }
                        viewModel.updateTrip(trip.copy(
                            title = tripTitle,
                            city = tripCity,
                            startDate = tripStartDate,
                            endDate = tripEndDate,
                            status = tripStatus
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

// shown briefly on startup while we check if the user already has a session
@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WaterloopDarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "RoutePal",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = WaterloopBlue,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Team WaterlOOP",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "CS 446 · Team WaterlOOP",
                fontSize = 12.sp,
                color = WaterloopGold.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator(color = WaterloopBlue)
        }
    }
}
