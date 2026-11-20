package io.github.howielyn.cloudbridge.status

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

enum class ServiceState {
    UP,
    DOWN,
    DEGRADED,
    UNKNOWN
}

enum class StatusPeriod(val label: String, val graphSuffix: String) {
    DAY("24h", "-day"),
    WEEK("7d", "-week"),
    MONTH("30d", "-month"),
    YEAR("1y", "-year"),
    ALL("all", "")
}

data class CloudServiceStatus(
    val name: String,
    val slug: String,
    val url: String,
    val state: ServiceState,
    val responseTimeMs: Long?,
    val responseTimeDayMs: Long?,
    val responseTimeWeekMs: Long?,
    val responseTimeMonthMs: Long?,
    val responseTimeYearMs: Long?,
    val uptime: String?,
    val uptimeDay: String?,
    val uptimeWeek: String?,
    val uptimeMonth: String?,
    val uptimeYear: String?
) {
    fun uptime(period: StatusPeriod): String? = when (period) {
        StatusPeriod.DAY -> uptimeDay
        StatusPeriod.WEEK -> uptimeWeek
        StatusPeriod.MONTH -> uptimeMonth
        StatusPeriod.YEAR -> uptimeYear
        StatusPeriod.ALL -> uptime
    }

    fun responseTime(period: StatusPeriod): Long? =
        when (period) {
            StatusPeriod.DAY -> responseTimeDayMs
            StatusPeriod.WEEK -> responseTimeWeekMs
            StatusPeriod.MONTH -> responseTimeMonthMs
            StatusPeriod.YEAR -> responseTimeYearMs
            StatusPeriod.ALL -> responseTimeMs
        }
}

internal object StatusSummaryParser {
    fun parse(json: String): List<CloudServiceStatus> {
        val entries = try {
            Json.parseToJsonElement(json).jsonArray
        } catch (e: SerializationException) {
            throw e
        } catch (e: IllegalStateException) {
            throw SerializationException("Expected a JSON array of services", e)
        }

        return entries.mapNotNull { entry ->
            val service = entry as? JsonObject ?: return@mapNotNull null
            val slug = service.string("slug") ?: return@mapNotNull null
            val name = service.string("name") ?: slug

            CloudServiceStatus(
                name = name,
                slug = slug,
                url = service.string("url").orEmpty(),
                state = when (service.string("status")?.lowercase()) {
                    "up" -> ServiceState.UP
                    "down" -> ServiceState.DOWN
                    "degraded" -> ServiceState.DEGRADED
                    else -> ServiceState.UNKNOWN
                },
                responseTimeMs = service.long("time"),
                responseTimeDayMs = service.long("timeDay"),
                responseTimeWeekMs = service.long("timeWeek"),
                responseTimeMonthMs = service.long("timeMonth"),
                responseTimeYearMs = service.long("timeYear"),
                uptime = service.string("uptime"),
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
        string(key)?.toLongOrNull()?.takeIf { it >= 0 }
}
