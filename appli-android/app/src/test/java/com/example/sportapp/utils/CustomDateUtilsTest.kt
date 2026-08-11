package com.example.sportapp.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * T1.1.d (2026-05-06) : 1er test JVM Android — fondation JUnit côté Android.
 *
 * Cible : `CustomDateUtils` (utility pur, pas de mock requis). Valide que le
 * format wire canonique projet `"YYYY-MM-DDTHH:MM:SS.UUUUUUZ"` est respecté
 * (cf. CLAUDE.md V3.2 + docs/DATES.md §"Après V3.2").
 *
 * Exécution : `./gradlew test` (depuis Android Studio ou ligne de commande
 * avec JAVA_HOME configuré).
 */
class CustomDateUtilsTest {

    /** Le format wire canonique projet : 4-2-2T2-2-2.6Z. */
    private val canonicalRegex = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{6}Z$""")

    @Test
    fun `getNowISO8601 returns canonical format with 6 decimal places`() {
        val iso = CustomDateUtils.getNowISO8601()

        assertNotNull(iso)
        assertEquals("Length should be 27 chars (24 digits + T + . + Z)", 27, iso.length)
        assertTrue("Format should match canonical regex: $iso", canonicalRegex.matches(iso))
    }

    @Test
    fun `getNowISO8601 is parseable as Instant and close to now`() {
        val before = Instant.now()
        val iso = CustomDateUtils.getNowISO8601()
        val after = Instant.now()

        // Doit être parseable directement comme Instant ISO 8601
        val parsed = Instant.parse(iso)

        // Doit être entre before et after (avec tolérance microsec — getNowISO8601 tronque à microsec)
        val beforeTrunc = before.truncatedTo(ChronoUnit.MICROS)
        val afterPlusOne = after.truncatedTo(ChronoUnit.MICROS).plusNanos(1000)
        assertTrue(
            "Parsed $parsed should be between $beforeTrunc and $afterPlusOne",
            !parsed.isBefore(beforeTrunc) && parsed.isBefore(afterPlusOne)
        )
    }

    @Test
    fun `getNowISO8601 always ends with Z (UTC)`() {
        val iso = CustomDateUtils.getNowISO8601()
        assertTrue("Should end with 'Z' (UTC marker): $iso", iso.endsWith("Z"))
    }
}
