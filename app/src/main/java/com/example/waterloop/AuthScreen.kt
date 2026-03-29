package com.example.waterloop

import android.app.Activity
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.waterloop.ui.theme.WaterloopBlue
import com.example.waterloop.ui.theme.WaterloopDarkBackground
import com.example.waterloop.ui.theme.WaterloopGold
import com.example.waterloop.ui.theme.WaterloopSurface
import com.example.waterloop.ui.trips.AuthViewModel
import com.example.waterloop.data.sync.ConnectivityObserver

@Composable
fun AuthScreen(authViewModel: AuthViewModel) {

    // tracks which tab the user is on — true = sign in, false = sign up
    var isSignIn by remember { mutableStateOf(true) }

    // local text field values
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // observe state from the viewmodel
    val isLoading by authViewModel.isLoading.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()

    // observe network connectivity
    val context = LocalContext.current
    val connectivityObserver = remember { ConnectivityObserver(context) }
    val isOnline by connectivityObserver.observe().collectAsState(initial = true)

    if (!isOnline) {
        AlertDialog(
            onDismissRequest = { },  // Prevent user from dismissing the alert by tapping outside
            title = { Text("No Internet Connection") },
            text = { Text("An internet connection is required to sign in to an existing account or sign up for a new account. Please connect and try again.") },
            confirmButton = {
                Button(onClick = {
                    (context as? Activity)?.finishAffinity()
                }) {
                    Text("Close App")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WaterloopDarkBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {

            // app title — same branding as the loading screen
            Text(
                text = "waterlOOP",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = WaterloopBlue,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Interactive Travel Map Planner",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // tab row to switch between sign in and sign up
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(WaterloopSurface)
            ) {
                // sign in tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSignIn) WaterloopBlue else Color.Transparent)
                        .clickable { isSignIn = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sign In",
                        color = if (isSignIn) Color.White else Color.White.copy(alpha = 0.5f),
                        fontWeight = if (isSignIn) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // sign up tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isSignIn) WaterloopBlue else Color.Transparent)
                        .clickable { isSignIn = false }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sign Up",
                        color = if (!isSignIn) Color.White else Color.White.copy(alpha = 0.5f),
                        fontWeight = if (!isSignIn) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // email input
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    authViewModel.clearError()
                },
                label = { Text("Email", color = Color.White.copy(alpha = 0.6f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WaterloopBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = WaterloopBlue
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // password input — masked with dots
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    authViewModel.clearError()
                },
                label = { Text("Password", color = Color.White.copy(alpha = 0.6f)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WaterloopBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = WaterloopBlue
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // show error from the viewmodel if there is one
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = Color.Red.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // submit button — disabled and shows spinner while a network call is in flight
            Button(
                onClick = {
                    if (isSignIn) authViewModel.signIn(email, password)
                    else authViewModel.signUp(email, password)
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WaterloopBlue,
                    disabledContainerColor = WaterloopBlue.copy(alpha = 0.4f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isSignIn) "Sign In" else "Sign Up",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // secondary way to switch tabs
            TextButton(onClick = { isSignIn = !isSignIn }) {
                Text(
                    text = if (isSignIn) "No account? Sign Up" else "Have an account? Sign In",
                    color = WaterloopGold.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
            }
        }
    }
}
