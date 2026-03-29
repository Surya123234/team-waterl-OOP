package com.example.waterloop.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.waterloop.data.local.dao.CachedRoleDao
import com.example.waterloop.data.local.dao.MarkerDao
import com.example.waterloop.data.local.dao.MarkerPhotoDao
import com.example.waterloop.data.local.dao.TripDao
import com.example.waterloop.data.local.entity.CachedRoleEntity
import com.example.waterloop.data.local.entity.MarkerEntity
import com.example.waterloop.data.local.entity.MarkerPhotoEntity
import com.example.waterloop.data.local.entity.TripEntity

/**
 * The main Room database configuration for the application.
 * Defines the schema (entities) and serves as the primary access point
 * for the underlying SQLite database.
 */
@Database(
    entities = [
        TripEntity::class,
        MarkerEntity::class,
        MarkerPhotoEntity::class,
        CachedRoleEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun markerDao(): MarkerDao
    abstract fun markerPhotoDao(): MarkerPhotoDao
    abstract fun cachedRoleDao(): CachedRoleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the singleton instance of the database.
         * Using the Singleton pattern since creating a Database instance is computationally expensive.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "waterloop_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}