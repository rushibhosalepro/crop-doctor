package com.cropdoctor.app.data

import com.cropdoctor.app.util.AppLang
import com.cropdoctor.app.util.stringsFor

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

    fun propose(
        context: LeafContext,
        obs: FieldObservation,
        lang: AppLang = AppLang.EN
    ): PriorityProposal {
        val s = stringsFor(lang)
        // Not-a-case states first: no priority without a usable observation.
        if (obs.subject == "not_plant") {
            return PriorityProposal(
                "standard_followup",
                listOf(s.ruleNotPlant),
                listOf("NOT_PLANT")
            )
        }
        if (obs.subject == "unclear" || obs.photoQuality == "poor") {
            return PriorityProposal(
                "standard_followup",
                listOf(s.ruleRetake),
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
            reasons.add(s.ruleSprayPrefix + context.spray + s.ruleSpraySuffix)
        }
        // R-UNKNOWN-CROP: worker couldn't name the crop. Info flag only, never
        // red on its own: the crop-confirm prompt handles it. The model's
        // visual guess is shown but never auto-applied.
        if (context.crop !in coveredCrops) {
            flags.add("UNKNOWN_CROP")
            val guess = obs.cropGuess
            val guessNote = if (guess.isNotEmpty() && guess != "unknown") {
                s.ruleCropGuessPrefix + guess + s.ruleCropGuessSuffix
            } else {
                ""
            }
            reasons.add(s.ruleCropUnknown + guessNote + s.ruleCropConfirm)
        }
        // R-LOW-INFO: too many unknowns. Info flag only, never red on its
        // own: the questions prompt handles it.
        val unsure = listOf(
            context.watered, context.fertilizer, context.spray,
            context.weather, context.stage, context.spread
        ).count { it == "not_sure" }
        if (unsure >= 4) {
            flags.add("LOW_INFO")
            reasons.add(s.ruleLowInfo)
        }
        // R-EXTENT: large visible extent => prioritize. UNVALIDATED threshold.
        if (obs.extent == "large") {
            prioritize = true
            flags.add("LARGE_EXTENT")
            reasons.add(s.ruleLargeExtent)
        }
        // R-SPREAD: fast spread per worker report => prioritize. UNVALIDATED.
        if (context.spread == "fast") {
            prioritize = true
            flags.add("FAST_SPREAD")
            reasons.add(s.ruleFastSpread)
        }

        if (!prioritize) {
            reasons.add(s.ruleNoTriggers)
        }
        return PriorityProposal(
            priority = if (prioritize) "prioritize_review" else "standard_followup",
            reasons = reasons,
            flags = flags
        )
    }
}
