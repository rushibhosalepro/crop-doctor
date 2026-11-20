package com.cropdoctor.app

import com.cropdoctor.app.data.CloudVerification
import com.cropdoctor.app.data.FieldObservation
import com.cropdoctor.app.data.LeafContext
import com.cropdoctor.app.data.PriorityProposal
import com.cropdoctor.app.data.Report
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Headline = issue + severity in one glance. Severity comes from what the
 * photo shows and danger flags only, never from missing crop/chip answers.
 */
class HeadlineTest {

    private fun case(
        symptoms: String = "yellow patches and small holes",
        extent: String = "moderate",
        cloudExtent: String? = "moderate",
        flags: List<String> = emptyList(),
        crop: String = "unknown",
        guess: String = "cotton"
    ) = Report(
        context = LeafContext(crop = crop),
        observation = FieldObservation(
            subject = "plant", cropGuess = guess, symptoms = symptoms,
            location = "leaves", extent = extent, photoQuality = "ok"
        ),
        proposal = PriorityProposal("standard_followup", emptyList(), flags),
        cloud = cloudExtent?.let {
            CloudVerification(subject = "plant", extent = it, priority = "standard_followup")
        }
    )

    @Test fun `visible damage shows issue and medium severity, not case recorded`() {
        val h = case().headline()
        assertEquals("MEDIUM", h.severity)
        // Crop not tapped: the model's guess must not appear in the headline.
        assertEquals("Yellow patches and small holes", h.issue)
    }

    @Test fun `unknown crop and unanswered chips do not raise severity`() {
        val h = case(
            symptoms = "none", extent = "none", cloudExtent = "none",
            flags = listOf("UNKNOWN_CROP", "LOW_INFO"), guess = "mango"
        ).headline()
        assertEquals("NONE", h.severity)
        assertTrue(!h.issue.startsWith("Mango"))
    }

    @Test fun `danger flag forces high`() {
        assertEquals("HIGH", case(extent = "small", cloudExtent = "small", flags = listOf("RECENT_SPRAY")).headline().severity)
    }

    @Test fun `worse cloud extent wins`() {
        assertEquals("HIGH", case(extent = "small", cloudExtent = "large").headline().severity)
    }

    @Test fun `clean but offline is unconfirmed, never healthy`() {
        assertEquals("UNCONFIRMED", case(symptoms = "none", extent = "none", cloudExtent = null).headline().severity)
    }

    @Test fun `chosen crop beats model guess`() {
        assertTrue(case(crop = "tomato").headline().issue.startsWith("Tomato"))
    }

    @Test fun `cloud seeing marks blocks healthy`() {
        val h = case(symptoms = "none", extent = "none", cloudExtent = "small", guess = "wheat").headline()
        assertTrue(h.severity != "NONE")
    }

    @Test fun `cloud seeing no leaf forces retake`() {
        val r = case()
        val h = r.copy(cloud = r.cloud!!.copy(subject = "not_plant")).headline()
        assertEquals("RETAKE", h.severity)
    }

    @Test fun `not a plant asks for retake`() {
        val r = case()
        val h = r.copy(observation = r.observation.copy(subject = "not_plant")).headline()
        assertEquals("RETAKE", h.severity)
    }
}
