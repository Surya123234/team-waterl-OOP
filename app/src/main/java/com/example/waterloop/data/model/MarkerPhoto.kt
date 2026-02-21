package com.example.waterloop.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MarkerPhoto(
    @SerialName("id")
    val id: String? = null,

    @SerialName("marker_id")
    val markerId: String,

    @SerialName("photo_url")
    val photoUrl: String
)
