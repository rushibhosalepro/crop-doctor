import { useCallback, useEffect, useRef, useState } from "react";
import { isConfigured, getModels, onDeviceStatus } from "./firebase.js";
import {
  TRIAGE_PROMPT,
  VERIFY_PROMPT,
  extractJson,
  withTimeout,
  fileToDownscaled,
  contextText,
  isUsable,
} from "./ai.js";
import { headline, BADGES, TONES } from "./headline.js";
import { loadCases, saveCases, newCaseId, makeThumb } from "./cases.js";
import CaseFile from "./CaseFile.jsx";

const MODEL_TEXT = {
  checking: ["Checking…", "slate"],
  no_api: ["No on-device AI in this browser", "slate"],
  unavailable: ["On-device AI not supported here", "slate"],
  downloadable: ["On-device model not downloaded", "amber"],
  downloading: ["On-device model downloading…", "amber"],
  available: ["Gemini Nano ready", "green"],
};
const MODEL_HELP = {
  no_api: "On-device AI needs Google Chrome on desktop (v139+) with chrome://flags/#prompt-api-for-gemini-nano-multimodal-input enabled. Edge and other browsers don't ship Gemini Nano. Auto mode uses the cloud meanwhile.",
  unavailable: "This computer doesn't meet Chrome's on-device model requirements. Auto mode uses the cloud.",
};

const CROPS = [
  ["unknown", "Not sure"],
  ["cotton", "Cotton"], ["paddy", "Paddy"], ["wheat", "Wheat"], ["tomato", "Tomato"],
  ["sugarcane", "Sugarcane"], ["soybean", "Soybean"], ["maize", "Maize"], ["mango", "Mango"],
  ["chili", "Chili"], ["papaya", "Papaya"], ["chikoo", "Chikoo"], ["fenugreek", "Fenugreek"],
  ["other", "Other"],
];

const CHIPS = [
  { key: "spray", label: "Last spray", options: [["none", "None in 4 weeks"], ["recent", "This week"], ["not_sure", "Not sure"]], warn: "recent" },
  { key: "water", label: "Watering", options: [["dry", "Dry"], ["normal", "Normal"], ["wet", "Waterlogged"], ["not_sure", "Not sure"]] },
  { key: "stage", label: "Crop stage", options: [["seedling", "Seedling"], ["growing", "Growing"], ["flowering", "Flowering"], ["not_sure", "Not sure"]] },
];

const SAMPLES = [
  { src: "samples/sample_spotted.jpg", label: "Spotted", hint: "Damage case" },
  { src: "samples/sample_healthy.jpg", label: "Healthy", hint: "Mature green leaf" },
  { src: "samples/sample_newshoots.jpg", label: "New shoots", hint: "Checks may disagree" },
  { src: "samples/sample_notplant.jpg", label: "Not plant", hint: "Safety test" },
];

const DEFAULT_CTX = { crop: "unknown", spray: "not_sure", water: "not_sure", stage: "not_sure", note: "" };

function useOnline() {
  const [online, setOnline] = useState(navigator.onLine);
  useEffect(() => {
    const on = () => setOnline(true);
    const off = () => setOnline(false);
    window.addEventListener("online", on);
    window.addEventListener("offline", off);
    return () => {
      window.removeEventListener("online", on);
      window.removeEventListener("offline", off);
    };
  }, []);
  return online;
}

const errText = (e) => String((e && e.message) || e);

export default function App() {
  const configured = isConfigured();
  const online = useOnline();
  const [mode, setMode] = useState("auto");
  const [devStatus, setDevStatus] = useState("checking");
  const [progress, setProgress] = useState(0);
  const [preparing, setPreparing] = useState(false);
  const [image, setImage] = useState(null);
  const [picked, setPicked] = useState("");
  const [ctx, setCtx] = useState(DEFAULT_CTX);
  const [showNote, setShowNote] = useState(false);
  const [error, setError] = useState("");
  const [cases, setCases] = useState(loadCases);
  const [activeId, setActiveId] = useState(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const resultRef = useRef(null);
  const busyRef = useRef(new Set()); // case ids with a step in flight

  const deviceUsable = devStatus === "available";
  const deviceSupported = !["no_api", "unavailable", "checking"].includes(devStatus);
  const active = cases.find((c) => c.id === activeId) || null;
  const busy = !!active && active.status === "running";

  useEffect(() => {
    onDeviceStatus().then(setDevStatus);
  }, []);
  useEffect(() => {
    if (devStatus !== "checking" && !deviceSupported && mode === "device") setMode("auto");
  }, [devStatus, deviceSupported, mode]);
  useEffect(() => saveCases(cases), [cases]);

  const patch = useCallback((id, fn) => {
    setCases((prev) => prev.map((c) => (c.id === id ? { ...c, ...fn(c) } : c)));
  }, []);

  /**
   * Runs whatever steps a case still needs. Safe to call again for a case
   * that was waiting for signal. Returns when the case is done or waiting.
   */
  const runCase = useCallback(
    async (c0, runMode, canUseDevice) => {
      const id = c0.id;
      if (busyRef.current.has(id)) return;
      busyRef.current.add(id);
      const models = getModels();
      const img = c0.image;
      const imagePart = { inlineData: { data: img.base64, mimeType: img.mimeType } };
      const context = contextText(c0.ctx);
      let field = c0.field;
      try {
        // Step 1: first check. On-device when possible (PREFER_ON_DEVICE).
        if (!field || !field.obs) {
          if (!navigator.onLine && !canUseDevice) {
            patch(id, () => ({ status: "waiting" }));
            return;
          }
          patch(id, () => ({ status: "running", field: null, error: "" }));
          try {
            const m = runMode === "device" ? models.device : models.prefer;
            const t0 = performance.now();
            const call = m.generateContent([TRIAGE_PROMPT + context, imagePart]);
            const res = runMode === "device" ? await call : await withTimeout(call, 90000, "First check");
            const ms = Math.round(performance.now() - t0);
            const source = res.response.inferenceSource || "unknown";
            // The SDK can silently use the cloud even in ONLY_ON_DEVICE mode.
            if (runMode === "device" && source !== "on_device") {
              throw new Error("Expected an on-device answer, but it came from: " + source);
            }
            field = { obs: JSON.parse(extractJson(res.response.text())), source, ms };
            patch(id, () => ({ field }));
          } catch (e) {
            const msg = errText(e);
            if (!navigator.onLine && !canUseDevice) {
              patch(id, () => ({ status: "waiting" }));
            } else {
              patch(id, () => ({ status: "error", field: { error: msg }, error: msg }));
            }
            return;
          }
        }
        // Step 2: independent cloud double-check (ONLY_IN_CLOUD, no field JSON shown).
        if (!isUsable(field.obs)) {
          patch(id, () => ({ status: "done", image: null }));
          return;
        }
        // On-device only: the photo never leaves this device unless the
        // worker asks for the cloud double-check (keeps the image for that).
        if (c0.localOnly) {
          patch(id, () => ({ status: "done" }));
          return;
        }
        if (!navigator.onLine) {
          patch(id, () => ({ status: "waiting" }));
          return;
        }
        patch(id, () => ({ status: "running", cloud: null }));
        try {
          const t0 = performance.now();
          const res = await withTimeout(
            models.cloud.generateContent([VERIFY_PROMPT + context, imagePart]),
            90000,
            "Cloud double-check"
          );
          const ms = Math.round(performance.now() - t0);
          const cloud = { obs: JSON.parse(extractJson(res.response.text())), source: res.response.inferenceSource || "in_cloud", ms };
          patch(id, () => ({ cloud, status: "done", image: null }));
        } catch (e) {
          if (!navigator.onLine) patch(id, () => ({ status: "waiting" }));
          else patch(id, () => ({ status: "done", cloud: { error: errText(e) } }));
        }
      } finally {
        busyRef.current.delete(id);
      }
    },
    [patch]
  );

  // Back online: finish every case that was waiting for signal.
  useEffect(() => {
    if (!online || !configured) return;
    cases.filter((c) => c.status === "waiting" && c.image).forEach((c) => runCase(c, "auto", deviceUsable));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [online]);

  async function prepare() {
    setError("");
    setPreparing(true);
    try {
      // Must run on a user gesture, before any model query.
      await getModels().prefer.initializeDeviceModel((v) => setProgress(Math.round(v * 100)));
    } catch (e) {
      setError("Prepare failed: " + errText(e));
    } finally {
      // Report what the browser really says, not just that the call returned.
      setDevStatus(await onDeviceStatus());
      setPreparing(false);
    }
  }

  async function onFile(ev) {
    const f = ev.target.files && ev.target.files[0];
    ev.target.value = "";
    if (!f) return;
    setError("");
    try {
      setImage(await fileToDownscaled(f));
      setPicked("");
    } catch (e) {
      setError(errText(e));
    }
  }

  async function onSample(s) {
    setError("");
    setPicked(s.label);
    try {
      const blob = await (await fetch(s.src)).blob();
      const img = await fileToDownscaled(blob);
      setImage(img);
      analyze(img);
    } catch (e) {
      setError("Sample failed: " + errText(e));
    }
  }

  async function analyze(explicit) {
    setError("");
    const img = explicit || image;
    if (!img) {
      setError("Pick a photo or tap a sample first.");
      return;
    }
    if (mode === "device" && !deviceUsable) {
      setError("On-device only needs the on-device model. " + MODEL_TEXT[devStatus][0] + ".");
      return;
    }
    const c = {
      id: newCaseId(),
      createdAt: Date.now(),
      ctx: { ...ctx },
      thumb: await makeThumb(img.previewUrl),
      image: { base64: img.base64, mimeType: img.mimeType },
      sample: picked,
      localOnly: mode === "device",
      status: "running",
      field: null,
      cloud: null,
      error: "",
      worker: { priority: "", delivered: false, feedback: "", flagNote: "", checks: {} },
    };
    setCases((prev) => [c, ...prev]);
    setActiveId(c.id);
    if (window.matchMedia("(max-width: 900px)").matches) {
      setTimeout(() => resultRef.current && resultRef.current.scrollIntoView({ behavior: "smooth" }), 50);
    }
    runCase(c, mode, deviceUsable);
  }

  // Retry a failed cloud step, or (on-device-only cases) send the photo to
  // the cloud because the worker explicitly asked for it.
  function retryCloud() {
    if (!active) return;
    if (!active.image) {
      setError("The photo for this case is no longer stored. Start a new case.");
      return;
    }
    const next = { ...active, cloud: null, localOnly: false };
    patch(active.id, () => ({ localOnly: false }));
    runCase(next, "auto", deviceUsable);
  }

  const setChip = (key, val) => setCtx((p) => ({ ...p, [key]: val }));
  const [modelLabel, modelTone] = MODEL_TEXT[devStatus] || [devStatus, "slate"];
  const waitingCount = cases.filter((c) => c.status === "waiting").length;
  const runLabel = !busy
    ? "Analyze leaf case →"
    : active.field && active.field.obs
      ? "Double-checking in cloud…"
      : mode === "device" || deviceUsable
        ? "Checking on this device…"
        : "Checking in cloud…";

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <div className="logo">🌿</div>
          <div>
            <div className="title">Crop Doctor</div>
            <div className="sub">Snap a leaf. Get a case file, with or without signal.</div>
          </div>
        </div>
        <div className="top-actions">
          <span className={"pill tone-" + (online ? "green" : "amber")}>{online ? "● Online" : "● Offline"}</span>
          <span className={"pill tone-" + modelTone} title={MODEL_HELP[devStatus] || ""}>
            {modelLabel}
            {deviceSupported && !deviceUsable && (
              <button className="pill-btn" onClick={prepare} disabled={preparing || !configured}>
                {preparing ? `${progress}%` : "Prepare"}
              </button>
            )}
          </span>
          <div className="seg" role="group" aria-label="Inference mode">
            <button className={mode === "auto" ? "on" : ""} onClick={() => setMode("auto")}>Auto (hybrid)</button>
            <button
              className={mode === "device" ? "on" : ""}
              onClick={() => setMode("device")}
              disabled={!deviceSupported}
              title={deviceSupported ? "First check never uses the cloud" : MODEL_HELP[devStatus] || ""}
            >
              On-device only
            </button>
          </div>
          <button className="btn-ghost" onClick={() => setHistoryOpen(true)}>
            History <span className="count">{cases.length}</span>
            {waitingCount > 0 && <span className="count wait">{waitingCount} waiting</span>}
          </button>
        </div>
      </header>

      {!configured && <div className="banner-inline tone-amber">Firebase isn't configured. See README.</div>}
      {MODEL_HELP[devStatus] && <div className="banner-inline tone-slate">{MODEL_HELP[devStatus]}</div>}

      <main className="workspace">
        <section className="pane capture card">
          <div className="pane-head">
            <h3>1. Leaf photo</h3>
            <label className="link">
              ⬆ Upload photo
              <input type="file" accept="image/jpeg,image/png" capture="environment" onChange={onFile} hidden />
            </label>
          </div>
          <label className="photo">
            {image ? <img src={image.previewUrl} alt="Chosen leaf" /> : <span>Tap to take or upload a leaf photo</span>}
            {image && picked && <span className="photo-tag">Sample: {picked}</span>}
            <input type="file" accept="image/jpeg,image/png" capture="environment" onChange={onFile} hidden />
          </label>

          <div className="label">Or try a sample (one tap analyzes)</div>
          <div className="samples">
            {SAMPLES.map((s) => (
              <button key={s.label} className={"sample" + (picked === s.label ? " picked" : "")} onClick={() => onSample(s)} disabled={busy || !configured}>
                <img src={s.src} alt="" />
                <span><b>{s.label}</b><small>{s.hint}</small></span>
              </button>
            ))}
          </div>

          <div className="pane-head">
            <h3>2. Quick context <small>(optional, tap if known)</small></h3>
          </div>
          <p className="hint tip">Tip: set Last spray to “This week” and analyze again to see the spray-safety rule.</p>
          <div className="chip-row">
            <span className="chip-label">Crop</span>
            <select value={ctx.crop} onChange={(e) => setChip("crop", e.target.value)}>
              {CROPS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          </div>
          {CHIPS.map((q) => (
            <div className="chip-row" key={q.key}>
              <span className="chip-label">{q.label}</span>
              <div className="chips">
                {q.options.map(([v, l]) => (
                  <button
                    key={v}
                    className={"chip" + (ctx[q.key] === v ? (q.warn === v ? " on warn" : " on") : "")}
                    onClick={() => setChip(q.key, v)}
                  >
                    {l}
                  </button>
                ))}
              </div>
            </div>
          ))}
          {ctx.spray === "recent" && <p className="hint warn-text">Recent spray: the result will ask for an expert before anything new is applied.</p>}
          {showNote ? (
            <input type="text" placeholder="Field note: what do you see? (e.g. started 3 days ago)" value={ctx.note} onChange={(e) => setChip("note", e.target.value)} />
          ) : (
            <button className="link" onClick={() => setShowNote(true)}>+ Add field note</button>
          )}

          <div className="run-bar">
            {error && <div className="err">{error}</div>}
            <button className="btn-primary" onClick={() => analyze()} disabled={!configured || busy}>
              {busy && <span className="spinner" />}
              {runLabel}
            </button>
          </div>
        </section>

        <section className="pane result" ref={resultRef}>
          <CaseFile
            c={active}
            onRetry={retryCloud}
            onWorker={(w) => active && patch(active.id, (c) => ({ worker: { ...c.worker, ...w } }))}
          />
        </section>
      </main>

      <footer className="footer card">
        <b>AI observation only.</b> Confirm with a local agronomist before spraying. Kisan Call Centre:{" "}
        <a href="tel:18001801551">1800-180-1551</a> (toll-free, 6 AM–10 PM). Demo only, not for real-world use before agronomist review.
      </footer>

      {historyOpen && (
        <div className="drawer-bg" onClick={() => setHistoryOpen(false)}>
          <aside className="drawer" onClick={(e) => e.stopPropagation()}>
            <div className="pane-head">
              <h3>Case history <small>(this browser)</small></h3>
              <button className="link" onClick={() => setHistoryOpen(false)}>Close ✕</button>
            </div>
            {cases.length === 0 && <p className="hint">No cases yet.</p>}
            {cases.map((c) => {
              const h = c.field && c.field.obs ? headline(c.field.obs, c.cloud && c.cloud.obs, c.ctx) : null;
              return (
                <button key={c.id} className={"hist" + (c.id === activeId ? " on" : "")} onClick={() => { setActiveId(c.id); setHistoryOpen(false); }}>
                  {c.thumb ? <img src={c.thumb} alt="" /> : <span className="hist-ph" />}
                  <span className="hist-body">
                    <b>{h ? h.issue : c.status === "waiting" ? "Waiting for signal" : c.status === "error" ? "Check failed" : "Checking…"}</b>
                    <small>{c.id} · {new Date(c.createdAt).toLocaleString()}</small>
                    {h && <span className={"sev-mini tone-" + TONES[h.severity]}>{BADGES[h.severity]}</span>}
                    {c.status === "waiting" && <span className="sev-mini tone-amber">⏳ Waiting for signal</span>}
                  </span>
                </button>
              );
            })}
            {cases.length > 0 && (
              <button className="link danger" onClick={() => { if (confirm("Delete all saved cases in this browser?")) { setCases([]); setActiveId(null); } }}>
                Clear history
              </button>
            )}
          </aside>
        </div>
      )}
    </div>
  );
}
