// Result headline: what is wrong + how bad, in one glance. Same rules as the
// Android app (Report.headline() in Models.kt):
// - Severity comes only from what the photo shows (worse of field and cloud
//   extent) and danger signals (recent spray, cloud asks for expert review).
//   A missing crop or unanswered chips never raise it.
// - "Healthy" needs the cloud check too; offline clean is "Not confirmed".
// - Names symptoms, never diseases.

const RANK = { none: 0, small: 1, moderate: 2, large: 3 };

export const ACTIONS = {
  HIGH: "Show an expert before spraying. Do not apply anything new until checked.",
  MEDIUM: "Check nearby plants. Ask an expert within 2–3 days.",
  LOW: "Watch it. Take a new photo in 3 days.",
  NONE: "No problem seen. Not a guarantee. Watch for spots, holes, curling or white powder. New photo in 3 days.",
  UNCONFIRMED: "Nothing seen on this device. Go online so the cloud can confirm.",
  RETAKE: "Take a clear, close-up photo of one leaf.",
};

export const BADGES = {
  HIGH: "HIGH RISK",
  MEDIUM: "MEDIUM RISK",
  LOW: "LOW RISK",
  NONE: "LOOKS HEALTHY",
  UNCONFIRMED: "NOT CONFIRMED YET",
  RETAKE: "RETAKE PHOTO",
};

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
export function headline(obs, cloud, ctx) {
  const c = typeof ctx === "string" ? { crop: ctx } : ctx || {};
  if (obs.subject === "not_plant") {
    return { issue: "No leaf in this photo", severity: "RETAKE", reasons: ["The photo doesn't show a plant."] };
  }
  if (obs.subject !== "plant" || obs.photoQuality === "poor") {
    return { issue: "Photo unclear", severity: "RETAKE", reasons: ["The leaf is too blurry, dark or far away to check."] };
  }
  const raw = ["unknown", "other", "", undefined].includes(c.crop) ? obs.cropGuess : c.crop;
  const crop = raw && raw.trim() && raw !== "unknown" ? cap(raw.trim()) : null;
  const prefix = crop ? `${crop}: ` : "";

  const reasons = [];
  const recentSpray = c.spray === "recent";
  const cloudPriority = !!cloud && cloud.priority === "prioritize_review";
  if (recentSpray) reasons.push("Sprayed this week. An expert must check before anything new is applied.");
  if (cloudPriority) reasons.push("The cloud reviewer asked for an expert review.");

  if (isClean(obs) && !recentSpray && !cloudPriority) {
    return cloud && cloud.subject === "plant"
      ? { issue: `${prefix}No problem seen`, severity: "NONE", reasons: ["Both checks found no visible problem."] }
      : { issue: `${prefix}No problem seen yet`, severity: "UNCONFIRMED", reasons: ["Only the first check has run."] };
  }

  const rank = (e) => (e in RANK ? RANK[e] : 2); // unknown extent counts as moderate
  let level = Math.max(rank(obs.extent), cloud ? rank(cloud.extent) : 0);
  if (level >= 3) reasons.push("Damage covers a large part of the leaf.");
  else if (level === 2) reasons.push("Damage seen on part of the leaf.");
  else if (level === 1) reasons.push("Small marks seen.");
  if (recentSpray || cloudPriority) level = 3;

  let issue = (obs.symptoms || "").trim().replace(/\.+$/, "");
  if (!issue || issue.toLowerCase() === "none") issue = recentSpray ? "Recent spray recorded" : "Possible problem seen";
  const severity = level >= 3 ? "HIGH" : level === 2 ? "MEDIUM" : "LOW";
  return { issue: prefix + cap(issue.slice(0, 48)), severity, reasons };
}

export function priorityLabel(p) {
  if (p === "prioritize_review") return "Expert review soon";
  if (p === "standard_followup") return "Routine follow-up";
  return "Not set";
}
