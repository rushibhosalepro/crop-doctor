import { initializeApp } from "firebase/app";
import {
  initializeAppCheck,
  ReCaptchaEnterpriseProvider,
} from "firebase/app-check";
import {
  getAI,
  getGenerativeModel,
  GoogleAIBackend,
  InferenceMode,
} from "firebase/ai";

const firebaseConfig = {
  apiKey: "AIzaSyA0tI6M9MPuQvQzyuTR-EnefDmyiKBBjmY",
  authDomain: "crop-doctor-d6c91.firebaseapp.com",
  projectId: "crop-doctor-d6c91",
  storageBucket: "crop-doctor-d6c91.firebasestorage.app",
  messagingSenderId: "942518715445",
  appId: "1:942518715445:web:dae15d51dfc8d2a77f1d7c",
  measurementId: "G-Q4SF3N19X9",
};

// reCAPTCHA Enterprise SITE key (public string, safe to commit). Must also be
// registered in Firebase console under App Check -> Apps -> your web app,
// with the hosted domain allowlisted. Localhost uses the debug-token flow.
const RECAPTCHA_SITE_KEY = "6LfQMyguAAAAAGDngOwFlNIuvzxddwHpoQjYnatz";

export function isConfigured() {
  return firebaseConfig.apiKey !== "PASTE-ME" && RECAPTCHA_SITE_KEY !== "PASTE-ME";
}

let cached = null;

// Cloud model, same as the Android app. With a hybrid `mode`, the SDK IGNORES a
// top-level `model` and uses its built-in default instead (gemini-2.5-flash-lite
// in firebase 12.x, now retired -> 404). It must go inside inCloudParams.
const CLOUD_MODEL = "gemini-3.5-flash-lite";

// Tell Chrome's Prompt API the request contains an image, so on-device
// multimodal input is set up for it.
const ON_DEVICE_PARAMS = {
  createOptions: {
    expectedInputs: [{ type: "text", languages: ["en"] }, { type: "image" }],
    expectedOutputs: [{ type: "text", languages: ["en"] }],
  },
};

/**
 * Real on-device status. The SDK can't be trusted for this: when the browser
 * has no Prompt API (Edge, Firefox, old Chrome), it skips on-device setup
 * silently, initializeDeviceModel() returns at once, and even ONLY_ON_DEVICE
 * requests go to the cloud. Returns:
 * "no_api" | "unavailable" | "downloadable" | "downloading" | "available".
 */
export async function onDeviceStatus() {
  const LM = typeof self !== "undefined" ? self.LanguageModel : undefined;
  if (!LM || typeof LM.availability !== "function") return "no_api";
  try {
    return await LM.availability(ON_DEVICE_PARAMS.createOptions);
  } catch {
    return "unavailable";
  }
}

function hybrid(mode) {
  return { mode, inCloudParams: { model: CLOUD_MODEL }, onDeviceParams: ON_DEVICE_PARAMS };
}

export function getModels() {
  if (cached) return cached;
  const app = initializeApp(firebaseConfig);
  // Localhost only: prints "AppCheck debug token: ..." in devtools console.
  // Register it under App Check -> Apps -> your web app -> Manage debug tokens.
  // On the hosted site this stays off, so visitors pass real reCAPTCHA.
  const host = window.location.hostname;
  if (host === "localhost" || host === "127.0.0.1") {
    self.FIREBASE_APPCHECK_DEBUG_TOKEN = true;
  }
  initializeAppCheck(app, {
    provider: new ReCaptchaEnterpriseProvider(RECAPTCHA_SITE_KEY),
    isTokenAutoRefreshEnabled: true,
  });
  const ai = getAI(app, { backend: new GoogleAIBackend() });
  cached = {
    prefer: getGenerativeModel(ai, hybrid(InferenceMode.PREFER_ON_DEVICE)),
    device: getGenerativeModel(ai, hybrid(InferenceMode.ONLY_ON_DEVICE)),
    cloud: getGenerativeModel(ai, hybrid(InferenceMode.ONLY_IN_CLOUD)),
  };
  return cached;
}
