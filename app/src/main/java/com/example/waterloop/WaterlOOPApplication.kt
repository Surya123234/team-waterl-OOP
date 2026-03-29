package com.example.waterloop

import android.app.Application
import com.example.waterloop.data.local.AppDatabase
import com.example.waterloop.data.remote.SupabaseClient
import com.example.waterloop.data.sync.SyncManager

class WaterlOOPApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var syncManager: SyncManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        SupabaseClient.initialize()
        database = AppDatabase.getInstance(this)
        syncManager = SyncManager.getInstance(this)
        syncManager.startObserving()
    }

    companion object {
        lateinit var instance: WaterlOOPApplication
            private set
    }
}