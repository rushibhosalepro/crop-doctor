package com.cropdoctor.app

import com.cropdoctor.app.data.FieldObservation
import com.cropdoctor.app.data.LeafContext
import com.cropdoctor.app.data.Report
import com.cropdoctor.app.data.RulesEngine
import com.cropdoctor.app.util.AppLang
import com.cropdoctor.app.util.cropDisplayName
import com.cropdoctor.app.util.stringsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Language coverage: Marathi/Hindi render their own strings, and the rules
 * logic (flags, priority, severity) is identical in every language.
 * Run: ./gradlew :app:testDebugUnitTest
 */
class LangTest {

    @Test fun `marathi and hindi strings are present and differ from english`() {
        val en = stringsFor(AppLang.EN)
        listOf(AppLang.MR, AppLang.HI).forEach { lang ->
            val s = stringsFor(lang)
            assertTrue(s.analyzeBtn.isNotEmpty())
            assertTrue(s.badgeHigh.isNotEmpty())
            assertTrue(s.hlNoLeaf.isNotEmpty())
            assertTrue(s.ruleNoTriggers.isNotEmpty())
            assertNotEquals(en.analyzeBtn, s.analyzeBtn)
            assertNotEquals(en.badgeHigh, s.badgeHigh)
            assertNotEquals(en.crops["cotton"], s.crops["cotton"])
        }
    }

    @Test fun `crop display names translate known crops`() {
        assertEquals("कापूस", cropDisplayName("cotton", AppLang.MR))
        assertEquals("धान", cropDisplayName("paddy", AppLang.HI))
        assertEquals("गेहूँ", cropDisplayName("Wheat", AppLang.HI))
        // English keeps the old capitalize behavior.
        assertEquals("Cotton", cropDisplayName("cotton", AppLang.EN))
    }

    @Test fun `headline issues translate but severity stays the same`() {
        val rep = Report(
            observation = FieldObservation(subject = "not_plant")
        )
        assertEquals("RETAKE", rep.headline(AppLang.MR).severity)
        assertEquals("फोटोमध्ये पान नाही", rep.headline(AppLang.MR).issue)
        assertEquals("फोटो में पत्ता नहीं है", rep.headline(AppLang.HI).issue)
    }

    @Test fun `rules reasons translate but flags and priority are language-free`() {
        val ctx = LeafContext(crop = "paddy", spray = "today")
        val obs = FieldObservation(
            subject = "plant", symptoms = "few yellow spots",
            extent = "small", photoQuality = "ok"
        )
        val en = RulesEngine.propose(ctx, obs, AppLang.EN)
        val mr = RulesEngine.propose(ctx, obs, AppLang.MR)
        val hi = RulesEngine.propose(ctx, obs, AppLang.HI)
        assertEquals(en.priority, mr.priority)
        assertEquals(en.priority, hi.priority)
        assertEquals(en.flags, mr.flags)
        assertEquals(en.flags, hi.flags)
        assertEquals(en.reasons.size, mr.reasons.size)
        assertNotEquals(en.reasons.first(), mr.reasons.first())
        assertNotEquals(en.reasons.first(), hi.reasons.first())
    }
}
