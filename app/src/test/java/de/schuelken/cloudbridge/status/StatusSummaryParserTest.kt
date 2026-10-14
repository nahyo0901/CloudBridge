package de.schuelken.cloudbridge.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusSummaryParserTest {
    @Test
    fun parsesServiceMetricsAndStatus() {
        val services = StatusSummaryParser.parse(
            """
            [{
              "name":"Catbox",
              "url":"https://catbox.moe",
              "slug":"catbox",
              "status":"up",
              "uptimeDay":"100.00%",
              "uptimeWeek":"99.50%",
              "uptimeMonth":"98.00%",
              "uptimeYear":"97.00%",
              "time":586
            }]
            """.trimIndent()
        )

        assertEquals(1, services.size)
        assertEquals("Catbox", services[0].name)
        assertEquals(ServiceState.UP, services[0].state)
        assertEquals(586L, services[0].responseTimeMs)
        assertEquals("100.00%", services[0].uptimeDay)
        assertEquals("99.50%", services[0].uptimeWeek)
        assertEquals("98.00%", services[0].uptimeMonth)
        assertEquals("97.00%", services[0].uptimeYear)
    }

    @Test
    fun recognizesDownAndDegradedAndFallsBackToUnknown() {
        val services = StatusSummaryParser.parse(
            """
            [
              {"name":"Down service","status":"down"},
              {"name":"Slow service","status":"degraded"},
              {"name":"Unrecognized service","status":"maintenance"}
            ]
            """.trimIndent()
        )

        assertEquals(ServiceState.DOWN, services[0].state)
        assertEquals(ServiceState.DEGRADED, services[1].state)
        assertEquals(ServiceState.UNKNOWN, services[2].state)
    }

    @Test
    fun usesSlugWhenNameIsMissingAndKeepsMissingMetricsUnknown() {
        val service = StatusSummaryParser.parse(
            """[{"slug":"google-drive","status":"up","time":-1}]"""
        ).single()

        assertEquals("google-drive", service.name)
        assertNull(service.responseTimeMs)
        assertNull(service.uptimeDay)
        assertEquals("", service.url)
    }

    @Test
    fun skipsMalformedEntriesWithoutDroppingValidServices() {
        val services = StatusSummaryParser.parse(
            """[null, {"status":"up"}, {"name":"Valid","status":"up"}]"""
        )

        assertEquals(1, services.size)
        assertEquals("Valid", services.single().name)
    }
}
