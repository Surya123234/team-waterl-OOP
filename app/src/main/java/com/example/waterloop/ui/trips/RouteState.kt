package com.example.waterloop.ui.trips

import com.mapbox.geojson.Point

data class RouteState(
    val isCreating: Boolean = false,
    val points: List<Point> = emptyList()
)