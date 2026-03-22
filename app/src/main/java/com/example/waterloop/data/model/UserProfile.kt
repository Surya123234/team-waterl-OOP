package com.example.waterloop.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row returned by the get_user_id_by_email RPC function. */
@Serializable
data class UserIdResult(
    @SerialName("id")
    val id: String
)

/** Row returned by the get_trip_members_with_emails RPC function. */
@Serializable
data class TripMemberWithEmail(
    @SerialName("user_id")
    val userId: String,
    @SerialName("role")
    val role: String,
    @SerialName("email")
    val email: String?
)
