package com.example.waterloop.data.local

import androidx.room.TypeConverter
import com.mapbox.geojson.Point
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromPointList(value: List<Point>?): String? {
        if (value == null) return null
        val coords = value.map { listOf(it.longitude(), it.latitude()) }
        return Json.encodeToString(coords)
    }

    @TypeConverter
    fun toPointList(value: String?): List<Point>? {
        if (value == null) return null
        val coords = Json.decodeFromString<List<List<Double>>>(value)
        return coords.map { Point.fromLngLat(it[0], it[1]) }
    }
}