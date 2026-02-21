package com.example.waterloop.data.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TripMember(

    @SerialName("id")
    val id: String? = null,

    @SerialName("trip_id")
    val tripId: String,

    @SerialName("user_id")
    val userId: String,

    @EncodeDefault
    @SerialName("role")
    val role: String = "owner"
)