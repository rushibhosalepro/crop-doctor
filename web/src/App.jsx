import { useState } from "react";
import { isConfigured, getModels } from "./firebase.js";
import {
  TRIAGE_PROMPT,
  VERIFY_PROMPT,
  extractJson,
  withTimeout,
  agreement,
  fileToDownscaled,
} from "./ai.js";
import { headline, ACTIONS, BADGES } from "./headline.js";

const CROPS = [
  ["unknown", "Crop: Not sure"],
  ["paddy", "Paddy"], ["wheat", "Wheat"], ["tomato", "Tomato"],
  ["cotton", "Cotton"], ["sugarcane", "Sugarcane"], ["soybean", "Soybean"],
  ["maize", "Maize"], ["mango", "Mango"], ["chili", "Chili"],
  ["papaya", "Papaya"], ["chikoo", "Chikoo"], ["fenugreek", "Fenugreek"],
  ["other", "Other"],
];

const SAMPLES = [
  { src: "samples/sample_spotted.jpg", label: "Spotted" },
  { src: "samples/sample_healthy.jpg", label: "Healthy" },
  { src: "samples/sample_notplant.jpg", label: "Not plant" },
];

function Badge({ source }) {
  const onDevice = String(source || "").toUpperCase().includes("DEVICE");
  return (
    <span className={onDevice ? "badge-device" : "badge-cloud"}>
      {onDevice ? "Ran ON THIS DEVICE" : "Ran in cloud"}
    </span>
  );
}

function Headline({ head }) {
  const [label, color] = BADGES[head.severity];
  return (
    <div className="card" style={{ background: color + "1F" }}>
      <span className="sev-badge" style={{ background: color }}>{label}</span>
      <div style={{ fontSize: 22, fontWeight: "bold", color, marginTop: 6 }}>{head.issue}</div>
      <div>{ACTIONS[head.severity]}</div>
    </div>
  );
}

export default function App() {
  const configured = isConfigured();
  const [mode, setMode] = useState("auto");
  const [prepareStatus, setPrepareStatus] = useState(
    "Not prepared. Click, then wait for 100% before the offline demo."
  );
  const [progress, setProgress] = useState(0);
  const [preparing, setPreparing] = useState(false);
  const [image, setImage] = useState(null);
  const [picked, setPicked] = useState("");
  const [crop, setCrop] = useState("unknown");
  const [note, setNote] = useState("");
  const [analyzing, setAnalyzing] = useState(false);
  const [triage, setTriage] = useState(null);
  const [verify, setVerify] = useState(null);
  const [error, setError] = useState("");

  async function prepare() {
    setError("");
    setPreparing(true);
    try {
      const { prefer } = getModels();
      // Must run on a user gesture, before any model query.
      await prefer.initializeDeviceModel((val) => {
        setProgress(Math.round(val * 10000) / 100);
      });
      setProgress(100);
      setPrepareStatus("On-device model ready. You can now go offline (Wi-Fi off) and analyze.");
    } catch (e) {
      setPrepareStatus("Prepare failed: " + (e && e.message ? e.message : e));
    } finally {
      setPreparing(false);
    }
  }

  async function onFile(ev) {
    const f = ev.target.files && ev.target.files[0];
    if (!f) return;
    setError("");
    try {
      const img = await fileToDownscaled(f);
      setImage(img);
      setPicked("");
      setTriage(null);
      setVerify(null);
    } catch (e) {
      setError(e.message);
    }
  }

  async function onSample(src, label) {
    setError("");
    setPicked(label);
    try {
      const res = await fetch(src);
      const blob = await res.blob();
      const img = await fileToDownscaled(blob);
      setImage(img);
      setTriage(null);
      setVerify(null);
      analyze(img);
    } catch (e) {
      setError("Sample failed: " + e.message);
    }
  }

  async function analyze(explicit) {
    setError("");
    setTriage(null);
    setVerify(null);
    const img = explicit || image;
    if (!img) {
      setError("Pick a photo or tap a sample first.");
      return;
    }
    setAnalyzing(true);
    try {
      const { prefer, device, cloud } = getModels();
      const imagePart = {
        inlineData: { data: img.base64, mimeType: img.mimeType },
      };
      const context = `\nContext: crop=${crop}. Worker note: ${note}`;
      const triageModel = mode === "device" ? device : prefer;
      // On-device-only waits for a pending model download, so no timeout there.
      const triageCall = triageModel.generateContent([TRIAGE_PROMPT + context, imagePart]);
      const triageRes =
        mode === "device" ? await triageCall : await withTimeout(triageCall, 90000, "Field check");
      const obs = JSON.parse(extractJson(triageRes.response.text()));
      const triageSource = triageRes.response.inferenceSource || "unknown";
      setTriage({ obs, source: triageSource });

      if (obs.subject === "plant" && obs.photoQuality !== "poor" && navigator.onLine) {
        const verifyCall = cloud.generateContent([VERIFY_PROMPT + context, imagePart]);
        try {
          const verifyRes = await withTimeout(verifyCall, 90000, "Cloud review");
          const cloudObs = JSON.parse(extractJson(verifyRes.response.text()));
          const cloudSource = verifyRes.response.inferenceSource || "unknown";
          setVerify({ obs: cloudObs, source: cloudSource });
        } catch (e) {
          setVerify({ error: e.message });
        }
      } else if (!navigator.onLine) {
        setVerify({ pending: true });
      }
    } catch (e) {
      const msg = String((e && e.message) || e);
      // A cloud URL in the message means the cloud call failed, not on-device.
      const cloudError = msg.includes("googleapis.com");
      setError(
        mode === "device" && !cloudError
          ? "On-device check failed: " + msg + " (Prepare the model first, or switch to Auto.)"
          : "Analysis failed: " + msg
      );
    } finally {
      setAnalyzing(false);
    }
  }

  const head = triage ? headline(triage.obs, verify && verify.obs, crop) : null;
  const level = triage && verify && verify.obs ? agreement(triage.obs, verify.obs) : "none";
  let agreeLine = "";
  if (verify && verify.obs) {
    const bothClean = triage.obs.extent === "none" && verify.obs.extent === "none";
    if (bothClean) agreeLine = "Both checks found no problem.";
    else if (level === "match") agreeLine = "Field and cloud agree.";
    else if (level === "small_diff")
      agreeLine = `Mostly agree — small detail difference (field: ${triage.obs.extent}, cloud: ${verify.obs.extent}).`;
    else agreeLine = "Updated check — the cloud sees it differently.";
  }

  return (
    <div>
      <h1>Crop Doctor</h1>
      <p>Snap a leaf. Get a case file, with or without signal. Same hybrid pipeline as the Android app.</p>
      {!configured && (
        <div className="warn">Paste your Firebase web config into <code>src/firebase.js</code> first (see README).</div>
      )}

      <div className="card">
        <b>Mode</b><br /><br />
        <button className={"chip" + (mode === "auto" ? " on" : "")} onClick={() => setMode("auto")}>
          Auto (hybrid)
        </button>{" "}
        <button className={"chip" + (mode === "device" ? " on" : "")} onClick={() => setMode("device")}>
          On-device only
        </button>
        {mode === "device" && <p><small>On-device only proves offline mode on Nano hardware.</small></p>}
        <button className="btn btn-dark" onClick={prepare} disabled={!configured || preparing}>
          1. Prepare on-device model
        </button>
        <p><small>{prepareStatus}</small></p>
        {preparing && <progress value={progress} max="100" />}
      </div>

      <div className="card">
        <b>2. Pick a photo</b><br /><br />
        <input type="file" accept="image/*" onChange={onFile} /><br /><br />
        <small>OR TRY A SAMPLE (one tap analyzes)</small>
        <div className="btn-row" style={{ marginTop: 6 }}>
          {SAMPLES.map((s) => (
            <button
              key={s.label}
              className={"sample" + (picked === s.label ? " picked" : "")}
              onClick={() => onSample(s.src, s.label)}
            >
              <img src={s.src} alt={s.label} />
              <span>{s.label}</span>
            </button>
          ))}
        </div>
        {image && <img className="preview" src={image.previewUrl} alt="chosen photo" />}
      </div>

      <div className="card">
        <b>3. Context (optional)</b>
        <select value={crop} onChange={(e) => setCrop(e.target.value)}>
          {CROPS.map(([v, l]) => (
            <option key={v} value={v}>{l}</option>
          ))}
        </select>
        <input type="text" placeholder="Note (optional): what do you see?" value={note} onChange={(e) => setNote(e.target.value)} />
        <button className="btn" onClick={() => analyze()} disabled={!configured || analyzing}>
          4. {analyzing ? "Analyzing…" : "Analyze"}
        </button>
      </div>

      {error && <div className="err">{error}</div>}

      {head && <Headline head={head} />}

      {triage && (
        <div className="card">
          <b>Field observation</b> <Badge source={triage.source} /><br />
          {triage.obs.cropGuess && triage.obs.cropGuess !== "unknown" && (
            <span>Looks like: {triage.obs.cropGuess} (not confirmed)<br /></span>
          )}
          {triage.obs.symptoms && <span>Seen: {triage.obs.symptoms}<br /></span>}
          {triage.obs.location && <span>Where: {triage.obs.location}<br /></span>}
          Extent: {triage.obs.extent || "unknown"}
          {triage.obs.notes && <span><br />Notes: {triage.obs.notes}</span>}
        </div>
      )}

      {verify && verify.obs && (
        <div className="card">
          <b>Cloud verification</b> <Badge source={verify.source} /><br />
          {agreeLine}<br />
          {verify.obs.reviewNote}
          {(verify.obs.questionsForExpert || []).map((q, i) => (
            <span key={i}><br />• Ask expert: {q}</span>
          ))}
          <br />Cloud priority: {verify.obs.priority || "standard_followup"}
        </div>
      )}
      {verify && verify.error && (
        <div className="card"><b>Cloud verification</b><br />Cloud review failed: {verify.error}. Retry when online.</div>
      )}
      {verify && verify.pending && (
        <div className="card"><b>Cloud verification</b><br />Verification PENDING — no connection. This case is recorded and will verify when online.</div>
      )}

      <div className="card">
        <small>
          AI guess — confirm with a local expert before spraying.<br />
          Kisan Call Centre: 1800-180-1551 (toll-free, 6 AM–10 PM).<br />
          Demo only. No real-world use before agronomist review.
        </small>
      </div>
    </div>
  );
}