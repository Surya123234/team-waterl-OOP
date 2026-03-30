package com.example.waterloop.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Trip (
    @SerialName("id")
    val id: String? = null,

    @SerialName("owner_id")
    val ownerId: String? = null,

    @SerialName("title")
    val title: String,

    @SerialName("city")
    val city: String? = null,

    @SerialName("start_date")
    val startDate: String? = null,

    @SerialName("end_date")
    val endDate: String? = null,

    @SerialName("cover_image_url")
    val coverImageUrl: String? = null,

    @SerialName("status")
    val status: String = "active",

    @SerialName("routes")
    val routes: List<List<Double>>? = null
)