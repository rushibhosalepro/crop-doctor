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

/** Result-screen headline. severity: NONE|LOW|MEDIUM|HIGH|UNCONFIRMED|RETAKE. */
data class Headline(val issue: String, val severity: String, val color: String)

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

    companion object {
        val DANGER_FLAGS = setOf("RECENT_SPRAY", "LARGE_EXTENT", "FAST_SPREAD")
    }

    fun hasDangerFlags(): Boolean = proposal.flags.any { it in DANGER_FLAGS }

    fun isCleanObservation(): Boolean {
        if (!observation.isUsableCase()) return false
        val s = observation.symptoms.trim().lowercase()
        val noSymptoms = s.isEmpty() || s == "none" || s == "none." ||
            s == "healthy" || s.startsWith("no visible") || s.startsWith("no symptoms")
        return noSymptoms && observation.extent == "none"
    }

    /**
     * Green "looks healthy" display rule (safety contract v2): only when the
     * field observation is clean, the cloud independently agrees, nothing is
     * flagged dangerous, and the worker hasn't prioritized. Offline-only
     * cases can never show green (cloud confirmation required).
     */
    /**
     * One-glance headline: what is wrong + how bad. Severity comes only from
     * what the photo shows (worst of field and cloud extent) and danger
     * flags. A missing crop or unanswered chips never raise it. Escalate-only:
     * no "all clear" without cloud agreement. Names symptoms, never diseases.
     */
    fun headline(): Headline {
        val obs = observation
        if (obs.subject == "not_plant") return Headline("No leaf in photo", "RETAKE", "grey")
        if (!obs.isUsableCase()) return Headline("Photo unclear", "RETAKE", "grey")
        val cropName = (if (context.crop in setOf("unknown", "other")) obs.cropGuess else context.crop)
            .trim().takeIf { it.isNotEmpty() && it != "unknown" }
            ?.replaceFirstChar { it.uppercase() }
        val prefix = if (cropName != null) "$cropName: " else ""
        val c = cloud
        val cloudSaysPriority = c?.priority == "prioritize_review"
        // Green needs BOTH checks clean: the cloud seeing marks blocks "healthy".
        val cloudClean = c == null || c.extent == "none"
        if (isCleanObservation() && cloudClean && !hasDangerFlags() && !cloudSaysPriority) {
            return if (c != null && c.subject == "plant") Headline("${prefix}No problem seen", "NONE", "green")
            else Headline("${prefix}No problem seen yet", "UNCONFIRMED", "yellow")
        }
        fun rank(e: String) = when (e) { "large" -> 3; "moderate" -> 2; "small" -> 1; "none" -> 0; else -> 2 }
        var level = maxOf(rank(obs.extent), c?.let { rank(it.extent) } ?: 0)
        if (hasDangerFlags() || cloudSaysPriority) level = 3
        val issue = obs.symptoms.trim().trimEnd('.').let {
            if (it.isEmpty() || it.lowercase() == "none") "Possible problem seen" else it.take(48)
        }
        val (sev, color) = when (level) {
            3 -> "HIGH" to "red"
            2 -> "MEDIUM" to "orange"
            else -> "LOW" to "yellow"
        }
        return Headline(prefix + issue.replaceFirstChar { it.uppercase() }, sev, color)
    }

    fun showGreen(): Boolean {
        if (proposal.priority != "standard_followup") return false
        if (workerPriority == "prioritize_review") return false
        if (hasDangerFlags()) return false
        if (!isCleanObservation()) return false
        val c = cloud ?: return false
        if (agreementLevel() != "match") return false
        return c.priority == "standard_followup"
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
