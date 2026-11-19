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
  '"reviewNote": "2-3 lines for the extension worker", ' +
  '"questionsForExpert": ["up to 3 questions"], ' +
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
