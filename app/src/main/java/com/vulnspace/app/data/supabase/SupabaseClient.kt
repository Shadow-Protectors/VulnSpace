package com.vulnspace.app.data.supabase

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

object SupabaseApi {
    // ⚠️ PASTE YOUR SUPABASE URL AND ANON KEY HERE
    // Get these from: Supabase Dashboard -> Project Settings -> API
    private const val SUPABASE_URL = "https://xfxegvmerrbcdmorypzq.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhmeGVndm1lcnJiY2Rtb3J5cHpxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODk3OTQyNzQsImV4cCI6MjEwNTM3MDI3NH0.3uEBF2as3N3MAfNUNdN1WvgJIlpO2u819StoCRtNMwE"

    private val customJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        defaultSerializer = KotlinXSerializer(customJson)
        install(Auth)
        install(Postgrest)
        install(Realtime)
        install(Functions)
    }
}
