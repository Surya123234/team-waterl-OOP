package com.example.waterloop.data.remote

import com.example.waterloop.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.SupabaseClient as SupabaseClientInstance
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseClient {
    lateinit var client: SupabaseClient

    fun initialize() {
        this.client = createClient()
    }

    @OptIn(SupabaseInternal::class)
    private fun createClient(): SupabaseClientInstance {
        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }
}
