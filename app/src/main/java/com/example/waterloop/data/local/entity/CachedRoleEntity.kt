package com.example.waterloop.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_roles")
data class CachedRoleEntity(
    @PrimaryKey val tripId: String,
    val role: String
)