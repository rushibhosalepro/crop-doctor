package com.cropdoctor.app

import com.cropdoctor.app.data.CloudVerification
import com.cropdoctor.app.data.FieldObservation
import com.cropdoctor.app.data.PriorityProposal
import com.cropdoctor.app.data.Report
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the green "looks healthy" display rule (safety contract v2).
 * Green requires ALL of: standard proposal, clean observation, zero danger
 * flags, cloud confirmation with agreement, no worker prioritize.
 * Run: ./gradlew :app:testDebugUnitTest
 */
class GreenRuleTest {

    private fun greenCase(): Report {
        return Report(
            observation = FieldObservation(
                subject = "plant", symptoms = "none",
                location = "leaves", extent = "none", photoQuality = "ok"
            ),
            proposal = PriorityProposal("standard_followup", listOf("ok"), emptyList()),
            cloud = CloudVerification(
                subject = "plant", extent = "none",
                reviewNote = "fine", priority = "standard_followup"
            )
        )
    }

    @Test fun `clean dual-confirmed case shows green`() {
        assertTrue(greenCase().showGreen())
    }

    @Test fun `danger flag blocks green`() {
        val rep = greenCase().copy(
            proposal = PriorityProposal("standard_followup", emptyList(), listOf("FAST_SPREAD"))
        )
        assertFalse(rep.showGreen())
    }

    @Test fun `offline case never shows green`() {
        assertFalse(greenCase().copy(cloud = null).showGreen())
    }

    @Test fun `symptoms block green`() {
        val rep = greenCase().copy(
            observation = FieldObservation(
                subject = "plant", symptoms = "yellow spots",
                location = "leaves", extent = "small", photoQuality = "ok"
            )
        )
        assertFalse(rep.showGreen())
    }

    @Test fun `worker prioritize blocks green`() {
        assertFalse(greenCase().copy(workerPriority = "prioritize_review").showGreen())
    }

    @Test fun `cloud disagreement blocks green`() {
        val rep = greenCase().copy(
            cloud = CloudVerification(
                subject = "plant", extent = "moderate",
                reviewNote = "differs", priority = "standard_followup"
            )
        )
        assertFalse(rep.showGreen())
    }
}
