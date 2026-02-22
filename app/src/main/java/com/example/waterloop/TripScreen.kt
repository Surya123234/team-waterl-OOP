package com.example.waterloop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun TripScreen(navController: NavController, tripId: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Trip ID: $tripId")
        Button(
            onClick = { navController.navigate("map") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View on Map")
        }
    }
}
