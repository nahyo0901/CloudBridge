package io.github.howielyn.cloudbridge.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusSummaryParserTest {
    @Test
    fun parsesStatusMetricsAndPeriodResponseTimes() {
        val service = StatusSummaryParser.parse(
            """
            [{
              "name":"Catbox",
              "slug":"catbox",
              "url":"https://catbox.moe",
              "status":"up",
              "uptime":"97.85%",
              "uptimeDay":"100.00%",
              "uptimeWeek":"100.00%",
              "uptimeMonth":"97.65%",
              "uptimeYear":"97.85%",
              "time":570,
              "timeDay":349,
              "timeWeek":406,
              "timeMonth":412,
              "timeYear":570
            }]
            """.trimIndent()
        ).single()

        assertEquals("Catbox", service.name)
        assertEquals(ServiceState.UP, service.state)
        assertEquals("97.85%", service.uptime(StatusPeriod.ALL))
        assertEquals("100.00%", service.uptime(StatusPeriod.DAY))
        assertEquals(349L, service.responseTime(StatusPeriod.DAY))
        assertEquals(406L, service.responseTime(StatusPeriod.WEEK))
        assertEquals(412L, service.responseTime(StatusPeriod.MONTH))
        assertEquals(570L, service.responseTime(StatusPeriod.YEAR))
        assertEquals(570L, service.responseTime(StatusPeriod.ALL))
    }

    @Test
    fun parsesOtherStatusesAndSkipsEntriesWithoutSlugs() {
        val services = StatusSummaryParser.parse(
            """
            [
              {"name":"Missing slug","status":"up"},
              {"name":"Unavailable","slug":"unavailable","status":"down"},
              {"name":"Slow","slug":"slow","status":"degraded"},
              {"name":"Other","slug":"other","status":"maintenance","time":-1}
            ]
            """.trimIndent()
        )

        assertEquals(3, services.size)
        assertEquals(ServiceState.DOWN, services[0].state)
        assertEquals(ServiceState.DEGRADED, services[1].state)
        assertEquals(ServiceState.UNKNOWN, services[2].state)
        assertNull(services[2].responseTimeMs)
    }
}
