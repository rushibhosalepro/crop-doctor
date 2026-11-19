// Result headline: what is wrong + how bad, in one glance. Same rules as the
// Android app (Report.headline() in Models.kt):
// - Severity comes only from what the photo shows (worse of field and cloud
//   extent) and danger signals. A missing crop never raises it.
// - "Healthy" needs the cloud check too; offline clean is "Not confirmed".
// - Names symptoms, never diseases.

const RANK = { none: 0, small: 1, moderate: 2, large: 3 };

export const ACTIONS = {
  HIGH: "Show an expert before spraying. Call 1800-180-1551.",
  MEDIUM: "Check nearby plants. Ask an expert within 2–3 days.",
  LOW: "Watch it. Take a new photo in 3 days.",
  NONE: "No problem seen. Not a guarantee. Watch for spots, holes, curling or white powder. New photo in 3 days.",
  UNCONFIRMED: "Go online to confirm this check.",
  RETAKE: "Take a clear, close-up photo of one leaf.",
};

export const BADGES = {
  HIGH: ["HIGH RISK", "#B71C1C"],
  MEDIUM: ["MEDIUM RISK", "#E65100"],
  LOW: ["LOW RISK", "#8D6E00"],
  NONE: ["HEALTHY", "#1B5E20"],
  UNCONFIRMED: ["NOT CONFIRMED", "#8D6E00"],
  RETAKE: ["RETAKE PHOTO", "#616161"],
};

const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);

function isClean(obs) {
  const s = (obs.symptoms || "").trim().toLowerCase();
  const none = s === "" || s === "none" || s === "none." || s === "healthy" ||
    s.startsWith("no visible") || s.startsWith("no symptoms");
  return none && obs.extent === "none";
}

/** Returns { issue, severity }. `cloud` may be null (offline / not yet run). */
export function headline(obs, cloud, chosenCrop) {
  if (obs.subject === "not_plant") return { issue: "No leaf in photo", severity: "RETAKE" };
  if (obs.subject !== "plant" || obs.photoQuality === "poor") {
    return { issue: "Photo unclear", severity: "RETAKE" };
  }
  const raw = ["unknown", "other", "", undefined].includes(chosenCrop) ? obs.cropGuess : chosenCrop;
  const crop = raw && raw.trim() && raw !== "unknown" ? cap(raw.trim()) : null;
  const prefix = crop ? `${crop}: ` : "";
  const cloudPriority = cloud && cloud.priority === "prioritize_review";

  if (isClean(obs) && !cloudPriority) {
    return cloud && cloud.subject === "plant"
      ? { issue: `${prefix}No problem seen`, severity: "NONE" }
      : { issue: `${prefix}No problem seen yet`, severity: "UNCONFIRMED" };
  }
  const rank = (e) => (e in RANK ? RANK[e] : 2); // unknown extent counts as moderate
  let level = Math.max(rank(obs.extent), cloud ? rank(cloud.extent) : 0);
  if (cloudPriority) level = 3;
  let issue = (obs.symptoms || "").trim().replace(/\.+$/, "");
  if (!issue || issue.toLowerCase() === "none") issue = "Possible problem seen";
  const severity = level >= 3 ? "HIGH" : level === 2 ? "MEDIUM" : "LOW";
  return { issue: prefix + cap(issue.slice(0, 48)), severity };
}
