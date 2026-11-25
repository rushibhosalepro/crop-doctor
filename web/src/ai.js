// Same prompts and pipeline shape as the Android app (AiClient.kt).

export const TRIAGE_PROMPT =
  "You are a crop observation assistant. Describe ONLY what is visible. " +
  'Answer with ONLY this JSON, no other text: {"subject": one of plant|not_plant|unclear, ' +
  '"cropGuess": "crop name in one or two words if identifiable, else unknown", ' +
  '"symptoms": "visible marks in plain farmer words, max 8 words, e.g. yellow patches and small holes; no technical terms", "location": "where on the plant", ' +
  '"extent": one of none|small|moderate|large|unknown, ' +
  '"photoQuality": one of ok|poor, "notes": "anything uncertain"}. ' +
  "Rules: never name a disease or pest; never suggest treatment, severity, or urgency; " +
  "cropGuess is only an unconfirmed visual guess; if not a plant use not_plant; " +
  "if unsure use unclear; English only.";

export const VERIFY_PROMPT =
  "You are an expert crop reviewer. Analyze this leaf photo INDEPENDENTLY. " +
  'Answer with ONLY this JSON, no other text: {"subject": one of plant|not_plant|unclear, ' +
  '"extent": one of none|small|moderate|large|unknown, ' +
  '"reviewNote": "2-3 short lines in plain words a farmer understands, no technical terms like chlorosis or necrosis", ' +
  '"questionsForExpert": ["up to 3 short questions a field worker can check on the spot, plain words"], ' +
  '"priority": one of prioritize_review|standard_followup}. ' +
  "Rules: never name a confirmed disease; never recommend chemicals, dosage, or spraying; " +
  "never say the case can be ignored; English only.";

export function extractJson(raw) {
  const s = (raw || "").indexOf("{");
  const e = (raw || "").lastIndexOf("}");
  if (s >= 0 && e > s) return (raw || "").substring(s, e + 1);
  return "{}";
}

export function withTimeout(promise, ms, label) {
  let timer = null;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(label + " timed out after " + ms / 1000 + "s.")), ms);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

// Local-language display text. The English keys above stay exactly as they
// are, because the safety rules read them. For Marathi or Hindi the model
// ALSO returns the free-text fields in that language, and only the screen
// uses them. If they're missing or not in Devanagari, English is shown.
const LANG_NAMES = { mr: "Marathi", hi: "Hindi" };

export function localSuffix(lang, keys) {
  const name = LANG_NAMES[lang];
  if (!name) return "";
  return (
    ` ALSO add these keys, written in ${name} in Devanagari script, in short plain words a farmer understands: ` +
    keys.map((k) => `"${k}Local"`).join(", ") +
    `. Each one says the same thing as its English key. The same rules apply in ${name}: never name a disease or pest, ` +
    `never mention a chemical, dose or spray. Every other key stays in English exactly as specified.`
  );
}

const DEVANAGARI = /[\u0900-\u097F]/;

/** Text to show: the local version if it was made for this language, else English. */
export function shown(obj, key, lang, madeFor) {
  if (!obj) return "";
  const en = obj[key];
  if (lang === "en" || madeFor !== lang) return en;
  const local = obj[key + "Local"];
  if (Array.isArray(en)) {
    return Array.isArray(local) && local.length && local.every((q) => typeof q === "string" && DEVANAGARI.test(q)) ? local : en;
  }
  return typeof local === "string" && DEVANAGARI.test(local) ? local : en;
}

export function contextText(ctx) {
  return (
    `\nContext: crop=${ctx.crop}, last spray=${ctx.spray}, watering=${ctx.water}, ` +
    `crop stage=${ctx.stage}. Worker note: ${ctx.note || "none"}`
  );
}

export function isUsable(obs) {
  return obs.subject === "plant" && obs.photoQuality !== "poor";
}

export function agreement(obs, cloud) {
  if (!cloud) return "none";
  if (obs.subject !== cloud.subject) return "updated";
  return obs.extent === cloud.extent ? "match" : "small_diff";
}

export function fileToDownscaled(fileOrBlob, maxDim = 1024) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    const url = URL.createObjectURL(fileOrBlob);
    img.onload = () => {
      const scale = Math.min(1, maxDim / Math.max(img.naturalWidth, img.naturalHeight));
      const canvas = document.createElement("canvas");
      canvas.width = Math.round(img.naturalWidth * scale);
      canvas.height = Math.round(img.naturalHeight * scale);
      canvas.getContext("2d").drawImage(img, 0, 0, canvas.width, canvas.height);
      const dataUrl = canvas.toDataURL("image/jpeg", 0.85);
      URL.revokeObjectURL(url);
      resolve({ base64: dataUrl.split(",")[1], mimeType: "image/jpeg", previewUrl: dataUrl });
    };
    img.onerror = () => reject(new Error("Could not read that image."));
    img.src = url;
  });
}
