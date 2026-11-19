package com.cropdoctor.app

import com.cropdoctor.app.data.FieldObservation
import com.cropdoctor.app.data.LeafContext
import com.cropdoctor.app.data.RulesEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Safety-contract tests. The rules engine is deterministic, so every rule
 * is tested here — not on a photo set. Run: ./gradlew :app:testDebugUnitTest
 */
class RulesEngineTest {

    private val cleanCrop = LeafContext(
        crop = "paddy", watered = "week", fertilizer = "older",
        spray = "none_4w", weather = "normal",
        stage = "vegetative", spread = "slow"
    )
    private val mildObs = FieldObservation(
        subject = "plant", symptoms = "few yellow spots",
        location = "lower leaves", extent = "small", photoQuality = "ok"
    )

    @Test fun `non-plant opens no case`() {
        val p = RulesEngine.propose(cleanCrop, mildObs.copy(subject = "not_plant"))
        assertTrue(p.flags.contains("NOT_PLANT"))
    }

    @Test fun `poor photo needs retake`() {
        val p = RulesEngine.propose(cleanCrop, mildObs.copy(photoQuality = "poor"))
        assertTrue(p.flags.contains("NEEDS_RETAKE"))
    }

    @Test fun `recent spray forces prioritize without chemical instruction`() {
        val p = RulesEngine.propose(cleanCrop.copy(spray = "today"), mildObs)
        assertEquals("prioritize_review", p.priority)
        assertTrue(p.flags.contains("RECENT_SPRAY"))
        assertTrue(
            p.reasons.none {
                it.contains("don't spray", ignoreCase = true) ||
                    it.contains("do not spray", ignoreCase = true)
            }
        )
    }

    @Test fun `unknown crop is an info flag, not red`() {
        val p = RulesEngine.propose(cleanCrop.copy(crop = "banana"), mildObs)
        assertEquals("standard_followup", p.priority)
        assertTrue(p.flags.contains("UNKNOWN_CROP"))
    }

    @Test fun `selectable crops are covered`() {
        listOf("cotton", "sugarcane", "soybean", "maize").forEach { crop ->
            val p = RulesEngine.propose(cleanCrop.copy(crop = crop), mildObs)
            assertTrue(p.flags.none { it == "UNKNOWN_CROP" })
        }
    }

    @Test fun `low info is an info flag, not red`() {
        val p = RulesEngine.propose(LeafContext(crop = "paddy"), mildObs)
        assertEquals("standard_followup", p.priority)
        assertTrue(p.flags.contains("LOW_INFO"))
    }

    @Test fun `large extent prioritizes`() {
        val p = RulesEngine.propose(cleanCrop, mildObs.copy(extent = "large"))
        assertEquals("prioritize_review", p.priority)
    }

    @Test fun `fast spread prioritizes`() {
        val p = RulesEngine.propose(cleanCrop.copy(spray = "none_4w"), mildObs)
        val p2 = RulesEngine.propose(cleanCrop.copy(spread = "fast"), mildObs)
        assertEquals("prioritize_review", p2.priority)
        assertEquals("standard_followup", p.priority)
    }

    @Test fun `clean mild case stays standard with worker-confirm note`() {
        val p = RulesEngine.propose(cleanCrop, mildObs)
        assertEquals("standard_followup", p.priority)
        assertTrue(p.reasons.any { it.contains("Worker confirms") })
    }

    @Test fun `no rule ever clears a case`() {
        val p = RulesEngine.propose(cleanCrop, mildObs)
        assertNotEquals("routine", p.priority)
        assertNotEquals("all_clear", p.priority)
    }

    @Test fun `unknown crop with model guess mentions the guess`() {
        val ctx = LeafContext(
            crop = "unknown", watered = "week", fertilizer = "older",
            spray = "none_4w", weather = "normal",
            stage = "vegetative", spread = "slow"
        )
        val p = RulesEngine.propose(ctx, mildObs.copy(cropGuess = "cotton"))
        assertEquals("standard_followup", p.priority)
        assertTrue(p.reasons.any { it.contains("cotton") })
    }
}
