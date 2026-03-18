package com.example.waterloop.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Geocode search response models
@Serializable
data class GeocodeResponse(
    val results: List<GeocodeResult>? = null
)

@Serializable
data class GeocodeResult(
    val lon: Double,
    val lat: Double,
    val rank: GeocodeRank? = null
)

@Serializable
data class GeocodeRank(
    val confidence: Double? = null,
    @SerialName("match_type")
    val matchType: String? = null
)

// Autocomplete response models
@Serializable
data class AutocompleteResponse(
    val results: List<AutocompleteResult>? = null
)

@Serializable
data class AutocompleteResult(
    val formatted: String? = null,
    val name: String? = null,
    val lat: Double,
    val lon: Double,
    @SerialName("address_line1")
    val addressLine1: String? = null,
    @SerialName("address_line2")
    val addressLine2: String? = null
)
