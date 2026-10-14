package de.schuelken.cloudbridge.status

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

enum class ServiceState {
    UP,
    DOWN,
    DEGRADED,
    UNKNOWN
}

data class CloudServiceStatus(
    val name: String,
    val url: String,
    val state: ServiceState,
    val responseTimeMs: Long?,
    val uptimeDay: String?,
    val uptimeWeek: String?,
    val uptimeMonth: String?,
    val uptimeYear: String?
)

internal object StatusSummaryParser {
    fun parse(json: String): List<CloudServiceStatus> {
        val services = try {
            Json.parseToJsonElement(json).jsonArray
        } catch (e: SerializationException) {
            throw e
        } catch (e: IllegalStateException) {
            throw SerializationException("Expected a JSON array of services", e)
        }

        return services.mapNotNull { element ->
            val service = element as? JsonObject ?: return@mapNotNull null
            val slug = service.string("slug")
            val name = service.string("name") ?: slug ?: return@mapNotNull null
            val responseTime = service.long("time")?.takeIf { it >= 0 }

            CloudServiceStatus(
                name = name,
                url = service.string("url").orEmpty(),
                state = when (service.string("status")?.lowercase()) {
                    "up" -> ServiceState.UP
                    "down" -> ServiceState.DOWN
                    "degraded" -> ServiceState.DEGRADED
                    else -> ServiceState.UNKNOWN
                },
                responseTimeMs = responseTime,
                uptimeDay = service.string("uptimeDay"),
                uptimeWeek = service.string("uptimeWeek"),
                uptimeMonth = service.string("uptimeMonth"),
                uptimeYear = service.string("uptimeYear")
            )
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
}
