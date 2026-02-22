package com.example.waterloop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.plugin.attribution.Attribution
import com.mapbox.maps.plugin.scalebar.ScaleBar

@Composable
fun MapScreen() {
    MapboxMap(
        Modifier.fillMaxSize(),
        mapViewportState = rememberMapViewportState {
            setCameraOptions {
                zoom(2.0)
                center(Point.fromLngLat(-98.0, 39.5))
                pitch(0.0)
                bearing(0.0)
            }
        },
        scaleBar = {
            ScaleBar(Modifier.padding(top = 60.dp))
        },
        attribution = {
            Attribution(Modifier.padding(bottom = 40.dp))
        }
    )
}
