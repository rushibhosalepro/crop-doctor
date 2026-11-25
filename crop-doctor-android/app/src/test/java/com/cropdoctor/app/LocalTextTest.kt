package com.cropdoctor.app

import com.cropdoctor.app.data.CloudVerification
import com.cropdoctor.app.data.FieldObservation
import com.cropdoctor.app.data.LeafContext
import com.cropdoctor.app.data.PriorityProposal
import com.cropdoctor.app.data.Report
import com.cropdoctor.app.util.AppLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Marathi/Hindi display text. The rules read the English keys; the screen
 * shows the local version only when it was made for that language and is
 * really in Devanagari.
 */
class LocalTextTest {

    private val obs = FieldObservation(
        subject = "plant", symptoms = "brown spots", symptomsLocal = "तपकिरी ठिपके",
        location = "leaves", locationLocal = "पाने", extent = "moderate",
        photoQuality = "ok", lang = "mr"
    )
    private val cloud = CloudVerification(
        subject = "plant", extent = "moderate", reviewNote = "Check it.",
        reviewNoteLocal = "तपासा.", questionsForExpert = listOf("Lower leaves?"),
        questionsForExpertLocal = listOf("खालची पाने?"), lang = "mr"
    )

    @Test fun `marathi case shows marathi text`() {
        assertEquals("तपकिरी ठिपके", obs.shownSymptoms(AppLang.MR))
        assertEquals("तपासा.", cloud.shownReviewNote(AppLang.MR))
        assertEquals(listOf("खालची पाने?"), cloud.shownQuestions(AppLang.MR))
    }

    @Test fun `other language falls back to english`() {
        assertEquals("brown spots", obs.shownSymptoms(AppLang.HI))
        assertEquals("brown spots", obs.shownSymptoms(AppLang.EN))
    }

    @Test fun `non devanagari local text falls back to english`() {
        assertEquals("brown spots", obs.copy(symptomsLocal = "brown spots").shownSymptoms(AppLang.MR))
    }

    @Test fun `headline shows marathi but severity comes from english fields`() {
        val r = Report(
            context = LeafContext(crop = "unknown"), observation = obs,
            proposal = PriorityProposal("standard_followup", emptyList(), emptyList()), cloud = cloud
        )
        val h = r.headline(AppLang.MR)
        assertEquals("MEDIUM", h.severity)
        assertTrue(h.issue, h.issue.contains("तपकिरी"))
    }
}
