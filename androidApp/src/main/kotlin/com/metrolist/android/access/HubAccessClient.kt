package com.metrolist.android.access

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class HubCredentials(
    val username: String,
    val password: String,
) {
    fun toJson(): JSONObject = JSONObject().put("username", username).put("password", password)

    override fun toString(): String = "HubCredentials([redacted])"
}

internal sealed interface HubDecision {
    data class Active(
        val until: Long,
    ) : HubDecision

    data object Inactive : HubDecision

    data object Unavailable : HubDecision
}

internal class HubAccessClient {
    // Separate from music networking: no cookies, logging interceptors or credential redirects.
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    suspend fun activate(code: String): HubCredentials? =
        withContext(Dispatchers.IO) {
            val json = post("/activate", JSONObject().put("activation_code", code))
            if (json.opt("ok") != true) return@withContext null
            val username = json.optString("username").trim()
            val password = json.optString("password")
            if (username.isBlank() || password.isBlank()) throw IOException("Incomplete activation response")
            HubCredentials(username, password)
        }

    suspend fun verify(credentials: HubCredentials): HubDecision =
        withContext(Dispatchers.IO) {
            val began = SystemClock.elapsedRealtime()
            try {
                val json = post("/management/primary-verify", credentials.toJson())
                if (json.optInt("schema_version") != 1 || json.opt("active") !is Boolean) {
                    return@withContext HubDecision.Unavailable
                }
                if (!json.getBoolean("active")) return@withContext HubDecision.Inactive
                val seconds = json.optLong("valid_for_seconds", -1)
                if (seconds !in 1..900) return@withContext HubDecision.Unavailable
                // The Hub worker calculates expiry; the device wall clock cannot extend it.
                val until = began + seconds * 1000L
                if (until > SystemClock.elapsedRealtime()) HubDecision.Active(until) else HubDecision.Inactive
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                HubDecision.Unavailable
            }
        }

    private fun post(
        path: String,
        payload: JSONObject,
    ): JSONObject {
        val request =
            Request
                .Builder()
                .url("https://activationworker.extremeiptv.net$path")
                .header("Cache-Control", "no-store")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Hub verification unavailable")
            val source = response.body?.source() ?: throw IOException("Empty Hub response")
            source.request(8193)
            if (source.buffer.size > 8192) throw IOException("Oversized Hub response")
            JSONObject(source.readUtf8())
        }
    }
}
