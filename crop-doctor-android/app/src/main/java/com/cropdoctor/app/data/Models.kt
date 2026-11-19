package com.cropdoctor.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Context chips. Every answer may be "not_sure". Chips never block the check. */
@Serializable
data class LeafContext(
    val crop: String = "unknown",
    val watered: String = "not_sure",
    val fertilizer: String = "not_sure",
    val spray: String = "not_sure",
    val weather: String = "not_sure",
    val stage: String = "not_sure",
    val spread: String = "not_sure"
)

/**
 * What the model may return: OBSERVATIONS ONLY (safety contract).
 * No severity-as-diagnosis, no urgency-as-treatment, no disease names.
 */
@Serializable
data class FieldObservation(
    val subject: String = "unclear", // plant | not_plant | unclear
    val cropGuess: String = "unknown", // model's visual guess, unconfirmed
    val symptoms: String = "",
    val location: String = "",
    val extent: String = "unknown", // none | small | moderate | large | unknown
    val photoQuality: String = "unknown", // ok | poor | unknown
    val notes: String = ""
) {
    fun isUsableCase(): Boolean = subject == "plant" && photoQuality != "poor"
}

/** Priority proposal from the local rules engine. No "all clear" exists. */
data class PriorityProposal(
    val priority: String, // prioritize_review | standard_followup
    val reasons: List<String>,
    val flags: List<String> // RECENT_SPRAY, UNKNOWN_CROP, LOW_INFO, ...
)

/** Independent cloud review. Observations + questions + priority only. */
@Serializable
data class CloudVerification(
    val subject: String = "unclear",
    val extent: String = "unknown",
    val reviewNote: String = "",
    val questionsForExpert: List<String> = emptyList(),
    val priority: String = "standard_followup"
)

data class Report(
    val id: String = "",
    val createdAt: Long = 0L,
    val note: String = "",
    val context: LeafContext = LeafContext(),
    val observation: FieldObservation = FieldObservation(),
    val proposal: PriorityProposal = PriorityProposal("standard_followup", emptyList(), emptyList()),
    val workerPriority: String = "",
    val workerOverrode: Boolean = false,
    val servedBy: String = "unknown",
    val verified: Boolean = false,
    val cloud: CloudVerification? = null,
    val delivered: Boolean = false,
    val feedback: String = ""
) {
    /** Agreement is computed here, never by either model. */
    fun observationsAgree(): Boolean {
        val c = cloud ?: return false
        return observation.subject == c.subject && observation.extent == c.extent
    }

    /**
     * Three-level agreement. Only a subject change (plant vs not_plant /
     * unclear) is an update; a mere extent wording difference is not.
     */
    fun agreementLevel(): String {
        val c = cloud ?: return "none"
        if (observation.subject != c.subject) return "updated"
        return if (observation.extent == c.extent) "match" else "small_diff"
    }
}

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

fun parseObservation(raw: String): FieldObservation {
    return try {
        json.decodeFromString<FieldObservation>(extractJson(raw))
    } catch (_: Exception) {
        FieldObservation(subject = "unclear", notes = "Model reply unreadable.")
    }
}

fun parseVerification(raw: String): CloudVerification {
    return try {
        json.decodeFromString<CloudVerification>(extractJson(raw))
    } catch (_: Exception) {
        CloudVerification(reviewNote = "Cloud review unreadable. Expert follow-up required.")
    }
}

fun extractJson(raw: String): String {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    return if (start >= 0 && end > start) raw.substring(start, end + 1) else "{}"
}
