package com.upsi.smartbus.core.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object AiApiClient {
    private const val TAG = "AiApiClient"

    // Default: 127.0.0.1:5000 works seamlessly via USB adb reverse (and local emulator)
    // For wireless hotspot/Wi-Fi: can be switched to http://192.168.x.x:5000
    var baseUrl: String = "http://127.0.0.1:5000"
        private set

    private var retrofit: Retrofit? = null
    private var service: AiApiService? = null

    /**
     * Initialize saved AI URL from SharedPreferences
     */
    fun init(context: android.content.Context) {
        val saved = context.getSharedPreferences("ai_settings", android.content.Context.MODE_PRIVATE)
            .getString("server_url", null)
        if (!saved.isNullOrEmpty()) {
            configure(saved)
        }
    }

    /**
     * Save and switch to a new AI server URL
     */
    fun saveConfig(context: android.content.Context, newUrl: String) {
        val cleanUrl = if (newUrl.startsWith("http://") || newUrl.startsWith("https://")) newUrl else "http://$newUrl"
        configure(cleanUrl)
        context.getSharedPreferences("ai_settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("server_url", baseUrl)
            .apply()
    }

    /**
     * Configure the AI server URL in-memory.
     * @param url The full base URL including port, e.g. "http://192.168.0.104:5000"
     */
    fun configure(url: String) {
        baseUrl = url.trimEnd('/')
        retrofit = null
        service = null
    }

    fun getService(): AiApiService {
        service?.let { return it }

        val logging = HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val r = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit = r
        val s = r.create(AiApiService::class.java)
        service = s
        return s
    }

    /**
     * Check if the AI server is reachable by pinging the health endpoint.
     * Must be called from a background thread.
     */
    fun isServerReachable(): Boolean {
        return try {
            val response = getService().healthCheck().execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "AI server unreachable: ${e.message}")
            false
        }
    }

    /**
     * Proactively pre-warms the AI server in the background (asynchronous).
     * Useful when running on Render/Cloud free tier where instances spin down after inactivity.
     * Call this from SplashActivity or MainActivity to wake up the server early.
     */
    fun warmUpServerAsync() {
        try {
            getService().healthCheck().enqueue(object : retrofit2.Callback<HealthResponse> {
                override fun onResponse(call: retrofit2.Call<HealthResponse>, response: retrofit2.Response<HealthResponse>) {
                    if (response.isSuccessful) {
                        Log.i(TAG, "AI Server is warm and ready: ${response.body()?.service}")
                    }
                }
                override fun onFailure(call: retrofit2.Call<HealthResponse>, t: Throwable) {
                    Log.d(TAG, "AI Server pre-warm triggered (waking up instance): ${t.message}")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Pre-warm failed to dispatch: ${e.message}")
        }
    }
}
