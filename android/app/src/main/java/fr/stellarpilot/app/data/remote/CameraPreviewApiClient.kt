package fr.stellarpilot.app.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class CameraPreviewApiClient {

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    suspend fun getPreview(
        serverBaseUrl: String
    ): ByteArray = withContext(Dispatchers.IO) {

        val url =
            serverBaseUrl.trimEnd('/') +
                "/camera/preview.jpg?t=" +
                System.currentTimeMillis()

        val request =
            Request.Builder()
                .url(url)
                .get()
                .build()

        Log.i("StellarPreview", "1 REQUEST $url")

        client.newCall(request)
            .execute()
            .use { response ->

                Log.i("StellarPreview", "2 HTTP ${response.code} length=${response.body?.contentLength()}")

                check(response.isSuccessful) {
                    "HTTP ${response.code} sur /camera/preview.jpg"
                }

                response.body?.bytes().also { bytes ->
                    Log.i("StellarPreview", "3 BODY ${bytes?.size} bytes")
                }
                    ?: error(
                        "Image camera vide"
                    )
            }
    }
}