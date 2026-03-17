package com.example.waterloop.data.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Marker(
    @SerialName("id")
    val id: String? = null,

    @SerialName("trip_id")
    val tripId: String,

    @SerialName("title")
    val title: String,

    @SerialName("description")
    val description: String? = null,

    @SerialName("category")
    val category: String? = null,

    @SerialName("notes")
    val notes: String? = null,

    @SerialName("latitude")
    val latitude: Double,

    @SerialName("longitude")
    val longitude: Double,

    @EncodeDefault
    @SerialName("visited")
    val visited: Boolean = false
)