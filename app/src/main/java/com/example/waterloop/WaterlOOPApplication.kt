package com.example.waterloop

import android.app.Application
import com.example.waterloop.data.remote.SupabaseClient

class WaterlOOPApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SupabaseClient.initialize()
    }
}
