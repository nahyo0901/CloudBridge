package de.schuelken.cloudbridge.status

import java.io.IOException
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal class StatusRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    @Throws(IOException::class)
    fun fetchServices(): List<CloudServiceStatus> {
        val request = Request.Builder()
            .url(SUMMARY_URL)
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Status feed returned HTTP ${response.code}")
            }
            val body = response.body?.string()
                ?: throw IOException("Status feed returned an empty response")
            val services = try {
                StatusSummaryParser.parse(body)
            } catch (e: SerializationException) {
                throw IOException("Status feed contained invalid JSON", e)
            }
            if (services.isEmpty()) {
                throw IOException("Status feed contained no services")
            }
            return services
        }
    }

    private companion object {
        const val SUMMARY_URL =
            "https://raw.githubusercontent.com/Howielyn/Cloud-Status/HEAD/history/summary.json"
    }
}
