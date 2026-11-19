package com.cropdoctor.app.data

/**
 * Safety-critical rules engine. Pure Kotlin, fully unit-tested (see
 * RulesEngineTest). Contract v2 enforced here:
 * - Danger signs (spray, extent, spread) may ESCALATE to red.
 * - Info gaps (crop, context) are yellow flags only, never red.
 * - Nothing here reassures, clears a case, names a disease, or instructs
 *   any chemical action. Green "looks healthy" is a strict display rule
 *   in Models (dual-confirmed, zero danger flags) — never from this engine.
 * - Every proposal carries human-readable reasons. The worker confirms or
 *   overrides; the app never has the last word on priority.
 */
object RulesEngine {

    // All selectable crops are covered. Only genuinely unknown crops escalate.
    // Rules stay crop-agnostic and escalate-only; no per-crop thresholds here.
    val coveredCrops = setOf(
        "paddy", "wheat", "tomato", "cotton", "sugarcane", "soybean", "maize",
        "mango", "chili", "papaya", "chikoo", "fenugreek"
    )

    fun propose(context: LeafContext, obs: FieldObservation): PriorityProposal {
        // Not-a-case states first: no priority without a usable observation.
        if (obs.subject == "not_plant") {
            return PriorityProposal(
                "standard_followup",
                listOf("Not a plant. No case opened. Retake with a leaf photo."),
                listOf("NOT_PLANT")
            )
        }
        if (obs.subject == "unclear" || obs.photoQuality == "poor") {
            return PriorityProposal(
                "standard_followup",
                listOf("Photo unusable. Retake a clear, close-up leaf photo."),
                listOf("NEEDS_RETAKE")
            )
        }

        val reasons = mutableListOf<String>()
        val flags = mutableListOf<String>()
        var prioritize = false

        // R-RECENT-SPRAY: record + escalate. Never a chemical instruction.
        // Source: safety contract (agronomy-free recency flag).
        if (context.spray == "today" || context.spray == "this_week") {
            prioritize = true
            flags.add("RECENT_SPRAY")
            reasons.add(
                "Recent application recorded (${context.spray}). " +
                    "Expert must review before any new recommendation."
            )
        }
        // R-UNKNOWN-CROP: worker couldn't name the crop. Info flag only, never
        // red on its own: the crop-confirm prompt handles it. The model's
        // visual guess is shown but never auto-applied.
        if (context.crop !in coveredCrops) {
            flags.add("UNKNOWN_CROP")
            val guess = obs.cropGuess
            val guessNote = if (guess.isNotEmpty() && guess != "unknown") {
                " The photo looks like $guess (not confirmed)."
            } else {
                ""
            }
            reasons.add("Crop not identified.$guessNote An expert should confirm the crop.")
        }
        // R-LOW-INFO: too many unknowns. Info flag only, never red on its
        // own: the questions prompt handles it.
        val unsure = listOf(
            context.watered, context.fertilizer, context.spray,
            context.weather, context.stage, context.spread
        ).count { it == "not_sure" }
        if (unsure >= 4) {
            flags.add("LOW_INFO")
            reasons.add("Only a few details were given. Answer the quick questions for a better check.")
        }
        // R-EXTENT: large visible extent => prioritize. UNVALIDATED threshold.
        if (obs.extent == "large") {
            prioritize = true
            flags.add("LARGE_EXTENT")
            reasons.add("Large visible extent reported. Prioritize expert review.")
        }
        // R-SPREAD: fast spread per worker report => prioritize. UNVALIDATED.
        if (context.spread == "fast") {
            prioritize = true
            flags.add("FAST_SPREAD")
            reasons.add("Spreading fast (worker report). Prioritize expert review.")
        }

        if (!prioritize) {
            reasons.add("No escalation triggers. Worker confirms final priority.")
        }
        return PriorityProposal(
            priority = if (prioritize) "prioritize_review" else "standard_followup",
            reasons = reasons,
            flags = flags
        )
    }
}
