package com.example.waterloop.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.waterloop.data.local.entity.CachedRoleEntity

@Dao
interface CachedRoleDao {

    @Query("SELECT role FROM cached_roles WHERE tripId = :tripId")
    suspend fun getRole(tripId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedRoleEntity)

    @Query("DELETE FROM cached_roles")
    suspend fun deleteAll()
}