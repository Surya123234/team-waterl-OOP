package com.example.waterloop

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequestLocationPermission(
    requestCount: Int,
    onPermissionDenied: () -> Unit,
    onPermissionReady: () -> Unit
) {
    val locationPermissionsState = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    )

    if (locationPermissionsState.allPermissionsGranted) {
        onPermissionReady()
    } else {
        LaunchedEffect(requestCount) {
            locationPermissionsState.launchMultiplePermissionRequest()
        }
        if (locationPermissionsState.shouldShowRationale) {
            onPermissionDenied()
        } else if (!locationPermissionsState.allPermissionsGranted && requestCount > 1) {
            onPermissionDenied()
        }
    }
}
