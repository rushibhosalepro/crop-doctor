// Result headline: what is wrong + how bad, in one glance. Same rules as the
// Android app (Report.headline() in Models.kt):
// - Severity comes only from what the photo shows (worse of field and cloud
//   extent) and danger signals (recent spray, cloud asks for expert review).
//   A missing crop or unanswered chips never raise it.
// - "Healthy" needs the cloud check too; offline clean is "Not confirmed".
// - Names symptoms, never diseases.
//
// All fixed text is translated via i18n.js (lang: en|mr|hi). The model's own
// free text (symptoms) stays in English, as the prompts require.
import { tx, cropDisplay } from "./i18n.js";
import { shown } from "./ai.js";

const RANK = { none: 0, small: 1, moderate: 2, large: 3 };

export function actionFor(severity, lang) {
  return tx(lang, "action_" + severity);
}

export function badgeFor(severity, lang) {
  return tx(lang, "badge_" + severity);
}

// Colour family per severity (see App.css .tone-*).
export const TONES = {
  HIGH: "red", MEDIUM: "amber", LOW: "amber", NONE: "green", UNCONFIRMED: "amber", RETAKE: "slate",
};

const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);

export function isClean(obs) {
  const s = (obs.symptoms || "").trim().toLowerCase();
  const none = s === "" || s === "none" || s === "none." || s === "healthy" ||
    s.startsWith("no visible") || s.startsWith("no symptoms");
  return none && obs.extent === "none";
}

/**
 * Returns { issue, severity, reasons }.
 * `cloud` may be null (offline / not run yet). `ctx` is { crop, spray } or,
 * for older callers, just the chosen crop string.
 */
// madeFor: the language the case was checked in (its local text is in that language).
export function headline(obs, cloud, ctx, lang = "en", madeFor = "en") {
  const c = typeof ctx === "string" ? { crop: ctx } : ctx || {};
  if (obs.subject === "not_plant") {
    return { issue: tx(lang, "hl_no_leaf"), severity: "RETAKE", reasons: [tx(lang, "r_not_plant")] };
  }
  if (obs.subject !== "plant" || obs.photoQuality === "poor") {
    return { issue: tx(lang, "hl_unclear"), severity: "RETAKE", reasons: [tx(lang, "r_blurry")] };
  }
  // The independent cloud check can veto a usable photo: if it sees no leaf
  // or can't tell, the first answer isn't trusted on its own.
  if (cloud && (cloud.subject === "not_plant" || cloud.subject === "unclear")) {
    return {
      issue: cloud.subject === "not_plant" ? tx(lang, "hl_no_leaf") : tx(lang, "hl_unclear"),
      severity: "RETAKE",
      reasons: [tx(lang, "r_cloud_cant")],
    };
  }
  // Only a crop the user tapped. The eval showed Nano guesses the crop right
  // just 2 of 16 times, so its guess never goes in the headline.
  const raw = ["unknown", "other", "", undefined].includes(c.crop) ? "" : c.crop;
  const crop = raw && raw.trim() && raw !== "unknown" ? cropDisplay(raw.trim(), lang) : null;
  const prefix = crop ? `${crop}: ` : "";

  const reasons = [];
  const recentSpray = c.spray === "recent";
  const cloudPriority = !!cloud && cloud.priority === "prioritize_review";
  if (recentSpray) reasons.push(tx(lang, "r_spray"));
  if (cloudPriority) reasons.push(tx(lang, "r_cloud_priority"));

  // Green needs BOTH checks clean: the cloud seeing marks blocks "healthy".
  const cloudClean = !cloud || cloud.extent === "none";
  if (isClean(obs) && cloudClean && !recentSpray && !cloudPriority) {
    return cloud && cloud.subject === "plant"
      ? { issue: `${prefix}${tx(lang, "hl_no_problem")}`, severity: "NONE", reasons: [tx(lang, "r_both_clean")] }
      : { issue: `${prefix}${tx(lang, "hl_no_problem_yet")}`, severity: "UNCONFIRMED", reasons: [tx(lang, "r_first_only")] };
  }

  const rank = (e) => (e in RANK ? RANK[e] : 2); // unknown extent counts as moderate
  let level = Math.max(rank(obs.extent), cloud ? rank(cloud.extent) : 0);
  if (level >= 3) reasons.push(tx(lang, "r_large"));
  else if (level === 2) reasons.push(tx(lang, "r_moderate"));
  else if (level === 1) reasons.push(tx(lang, "r_small"));
  if (recentSpray || cloudPriority) level = 3;

  let issue = (obs.symptoms || "").trim().replace(/\.+$/, "");
  if (!issue || issue.toLowerCase() === "none")
    issue = recentSpray ? tx(lang, "hl_recent_spray") : tx(lang, "hl_possible");
  // The rules above used the English text; the screen shows the local version.
  else issue = cap(shorten(shown(obs, "symptoms", lang, madeFor).trim().replace(/[.\u0964]+$/, ""), 60));
  const severity = level >= 3 ? "HIGH" : level === 2 ? "MEDIUM" : "LOW";
  return { issue: prefix + issue, severity, reasons };
}

/** Cut at a word boundary, never mid-word ("some lea" bug). */
export function shorten(s, max) {
  if (s.length <= max) return s;
  const cut = s.slice(0, max + 1);
  const i = Math.max(cut.lastIndexOf(" "), cut.lastIndexOf(","));
  return (i > 20 ? cut.slice(0, i) : s.slice(0, max)).replace(/[,\s]+$/, "") + "…";
}

export function priorityLabel(p, lang = "en") {
  if (p === "prioritize_review") return tx(lang, "priority_expert");
  if (p === "standard_followup") return tx(lang, "priority_routine");
  return tx(lang, "priority_unset");
}
