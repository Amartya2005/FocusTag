package com.focustag.app.data.supabase

import android.content.Context
import com.focustag.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class SharedPreferencesSessionManager(context: Context) : SessionManager {
    private val prefs = context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)

    override suspend fun saveSession(session: UserSession) {
        prefs.edit().putString("session", Json.encodeToString(session)).apply()
    }

    override suspend fun loadSession(): UserSession? {
        val json = prefs.getString("session", null) ?: return null
        return try {
            Json.decodeFromString<UserSession>(json)
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun deleteSession() {
        prefs.edit().remove("session").apply()
    }
}

object SupabaseModule {
    private var _client: SupabaseClient? = null

    val client: SupabaseClient
        get() = _client ?: throw IllegalStateException("SupabaseModule not initialized. Call initialize(context) first.")

    @Synchronized
    fun initialize(context: Context) {
        if (_client != null) return
        _client = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth) {
                scheme = "focustag"
                host = "auth"
                sessionManager = SharedPreferencesSessionManager(context.applicationContext)
            }
            install(Postgrest)
        }
    }
}
