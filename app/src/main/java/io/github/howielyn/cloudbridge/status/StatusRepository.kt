package io.github.howielyn.cloudbridge.status

import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.Request

internal class StatusRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val graphCache = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean =
            size > MAX_CACHED_GRAPHS
    }

    @Throws(IOException::class)
    fun fetchServices(): List<CloudServiceStatus> {
        val json = fetchRawContent(SUMMARY_PATH)
        val services = try {
            StatusSummaryParser.parse(json)
        } catch (e: SerializationException) {
            throw IOException("Status feed contained invalid JSON", e)
        }
        if (services.isEmpty()) {
            throw IOException("Status feed contained no services")
        }
        return services
    }

    @Throws(IOException::class)
    fun fetchGraph(slug: String, period: StatusPeriod): ByteArray {
        if (!slug.matches(Regex("[a-z0-9-]+"))) {
            throw IOException("Status feed contained an invalid service identifier")
        }
        val path = "graphs/$slug/response-time${period.graphSuffix}.png"
        synchronized(graphCache) {
            graphCache[path]?.let { return it }
        }
        val graph = fetchRawContentBytes(path)
        synchronized(graphCache) {
            graphCache[path] = graph
        }
        return graph
    }

    private fun fetchRawContent(path: String): String =
        fetchRawContentBytes(path).toString(Charsets.UTF_8)

    private fun fetchRawContentBytes(path: String): ByteArray {
        val request = Request.Builder()
            .url("$CONTENTS_URL/$path?ref=$REF")
            .header("Accept", "application/vnd.github.raw+json")
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub status feed returned HTTP ${response.code}")
            }
            return response.body?.bytes()
                ?: throw IOException("GitHub status feed returned an empty response")
        }
    }

    private companion object {
        const val CONTENTS_URL = "https://api.github.com/repos/Howielyn/Cloud-Status/contents"
        const val REF = "master"
        const val SUMMARY_PATH = "history/summary.json"
        const val USER_AGENT = "CloudBridge-Android"
        const val MAX_CACHED_GRAPHS = 40
    }
}
