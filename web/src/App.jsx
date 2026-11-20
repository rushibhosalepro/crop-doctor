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
import { headline, badgeFor, TONES } from "./headline.js";
import { LANGS, LOCALES, STR, tx, useLang } from "./i18n.js";
import { loadCases, saveCases, newCaseId, makeThumb } from "./cases.js";
import CaseFile from "./CaseFile.jsx";

const MODEL_TONE = {
  checking: "slate",
  no_api: "slate",
  unavailable: "slate",
  downloadable: "amber",
  downloading: "amber",
  available: "green",
};
const MODEL_HELP_KEY = {
  no_api: "model_help_no_api",
  unavailable: "model_help_unavailable",
};

const CROP_VALUES = [
  "unknown",
  "cotton", "paddy", "wheat", "tomato",
  "sugarcane", "soybean", "maize", "mango",
  "chili", "papaya", "chikoo", "fenugreek",
  "other",
];

const CHIP_KEYS = [
  { key: "spray", values: ["none", "recent", "not_sure"], warn: "recent" },
  { key: "water", values: ["dry", "normal", "wet", "not_sure"] },
  { key: "stage", values: ["seedling", "growing", "flowering", "not_sure"] },
];

const SAMPLE_SRCS = [
  "samples/sample_spotted.jpg",
  "samples/sample_healthy.jpg",
  "samples/sample_newshoots.jpg",
  "samples/sample_notplant.jpg",
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
  const [lang, setLang] = useLang();
  const [mode, setMode] = useState("auto");
  const [devStatus, setDevStatus] = useState("checking");
  const [progress, setProgress] = useState(0);
  const [preparing, setPreparing] = useState(false);
  const [image, setImage] = useState(null);
  const [picked, setPicked] = useState(-1);
  const [ctx, setCtx] = useState(DEFAULT_CTX);
  const [showNote, setShowNote] = useState(false);
  const [error, setError] = useState("");
  const [cases, setCases] = useState(loadCases);
  const [activeId, setActiveId] = useState(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const resultRef = useRef(null);
  const busyRef = useRef(new Set()); // case ids with a step in flight

  const t = (key) => tx(lang, key);
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
  useEffect(() => {
    if (!saveCases(cases)) {
      setError(t("err_storage"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cases]);

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
              throw new Error(t("err_device_source") + source);
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
    [patch, lang]
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
      setError(t("err_prepare") + errText(e));
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
      setPicked(-1);
    } catch (e) {
      setError(errText(e));
    }
  }

  async function onSample(i) {
    setError("");
    setPicked(i);
    try {
      const blob = await (await fetch(SAMPLE_SRCS[i])).blob();
      const img = await fileToDownscaled(blob);
      setImage(img);
      analyze(img, i);
    } catch (e) {
      setError(t("err_sample") + errText(e));
    }
  }

  async function analyze(explicit, sampleIdx = picked) {
    setError("");
    const img = explicit || image;
    if (!img) {
      setError(t("err_pick_first"));
      return;
    }
    if (mode === "device" && !deviceUsable) {
      setError(t("err_device_need") + t("model_" + devStatus) + ".");
      return;
    }
    const c = {
      id: newCaseId(),
      createdAt: Date.now(),
      ctx: { ...ctx },
      thumb: await makeThumb(img.previewUrl),
      image: { base64: img.base64, mimeType: img.mimeType },
      sample: sampleIdx >= 0 ? STR.en.samples[sampleIdx].label : "",
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

  // Retry a failed first check (e.g. flaky connection) with the same mode.
  function retryField() {
    if (!active) return;
    if (!active.image) {
      setError(t("err_photo_gone"));
      return;
    }
    runCase({ ...active, field: null, cloud: null }, active.localOnly ? "device" : "auto", deviceUsable);
  }

  // Retry a failed cloud step, or (on-device-only cases) send the photo to
  // the cloud because the worker explicitly asked for it.
  function retryCloud() {
    if (!active) return;
    if (!active.image) {
      setError(t("err_photo_gone"));
      return;
    }
    const next = { ...active, cloud: null, localOnly: false };
    patch(active.id, () => ({ localOnly: false }));
    runCase(next, "auto", deviceUsable);
  }

  const setChip = (key, val) => setCtx((p) => ({ ...p, [key]: val }));
  const modelLabel = t("model_" + devStatus);
  const modelTone = MODEL_TONE[devStatus] || "slate";
  const modelHelp = MODEL_HELP_KEY[devStatus] ? t(MODEL_HELP_KEY[devStatus]) : "";
  const waitingCount = cases.filter((c) => c.status === "waiting").length;
  const samples = STR[lang].samples;
  const runLabel = !busy
    ? t("run_analyze")
    : active.field && active.field.obs
      ? t("run_double")
      : mode === "device" || deviceUsable
        ? t("run_device")
        : t("run_cloud");

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <div className="logo">🌿</div>
          <div>
            <div className="title">Crop Doctor</div>
            <div className="sub">{t("appTag")}</div>
          </div>
        </div>
        <div className="top-actions">
          <span className={"pill tone-" + (online ? "green" : "amber")}>{online ? t("online") : t("offline")}</span>
          <span className={"pill tone-" + modelTone} title={modelHelp}>
            {modelLabel}
            {deviceSupported && !deviceUsable && (
              <button className="pill-btn" onClick={prepare} disabled={preparing || !configured}>
                {preparing ? `${progress}%` : t("prepare")}
              </button>
            )}
          </span>
          <div className="seg" role="group" aria-label="Inference mode">
            <button className={mode === "auto" ? "on" : ""} onClick={() => setMode("auto")}>{t("modeAuto")}</button>
            <button
              className={mode === "device" ? "on" : ""}
              onClick={() => setMode("device")}
              disabled={!deviceSupported}
              title={deviceSupported ? t("modeDeviceTitle") : modelHelp}
            >
              {t("modeDevice")}
            </button>
          </div>
          <div className="seg" role="group" aria-label={t("langLabel")}>
            {LANGS.map((l) => (
              <button key={l.id} className={lang === l.id ? "on" : ""} onClick={() => setLang(l.id)}>
                {l.label}
              </button>
            ))}
          </div>
          <button className="btn-ghost" onClick={() => setHistoryOpen(true)}>
            {t("history")} <span className="count">{cases.length}</span>
            {waitingCount > 0 && <span className="count wait">{waitingCount} {t("waiting")}</span>}
          </button>
        </div>
      </header>

      {!configured && <div className="banner-inline tone-amber">{t("firebaseMissing")}</div>}
      {modelHelp && <div className="banner-inline tone-slate">{modelHelp}</div>}

      <main className="workspace">
        <section className="pane capture card">
          <div className="pane-head">
            <h3>{t("step1")}</h3>
            <label className="link">
              {t("uploadPhoto")}
              <input type="file" accept="image/jpeg,image/png" capture="environment" onChange={onFile} hidden />
            </label>
          </div>
          <label className="photo">
            {image ? <img src={image.previewUrl} alt="Chosen leaf" /> : <span>{t("photoTap")}</span>}
            {image && picked >= 0 && <span className="photo-tag">{t("sampleTag")}{samples[picked].label}</span>}
            <input type="file" accept="image/jpeg,image/png" capture="environment" onChange={onFile} hidden />
          </label>

          <div className="label">{t("trySample")}</div>
          <div className="samples">
            {samples.map((s, i) => (
              <button key={SAMPLE_SRCS[i]} className={"sample" + (picked === i ? " picked" : "")} onClick={() => onSample(i)} disabled={busy || !configured}>
                <img src={SAMPLE_SRCS[i]} alt="" />
                <span><b>{s.label}</b><small>{s.hint}</small></span>
              </button>
            ))}
          </div>

          <div className="pane-head">
            <h3>{t("step2")} <small>{t("step2opt")}</small></h3>
          </div>
          <p className="hint tip">{t("sprayTip")}</p>
          <div className="chip-row">
            <span className="chip-label">{t("cropLabel")}</span>
            <select value={ctx.crop} onChange={(e) => setChip("crop", e.target.value)}>
              {CROP_VALUES.map((v) => <option key={v} value={v}>{STR[lang].crops[v]}</option>)}
            </select>
          </div>
          {CHIP_KEYS.map((q) => (
            <div className="chip-row" key={q.key}>
              <span className="chip-label">{STR[lang].chips[q.key].label}</span>
              <div className="chips">
                {q.values.map((v) => (
                  <button
                    key={v}
                    className={"chip" + (ctx[q.key] === v ? (q.warn === v ? " on warn" : " on") : "")}
                    onClick={() => setChip(q.key, v)}
                  >
                    {STR[lang].chips[q.key][v]}
                  </button>
                ))}
              </div>
            </div>
          ))}
          {ctx.spray === "recent" && <p className="hint warn-text">{t("sprayWarn")}</p>}
          {showNote ? (
            <input type="text" placeholder={t("notePh")} value={ctx.note} onChange={(e) => setChip("note", e.target.value)} />
          ) : (
            <button className="link" onClick={() => setShowNote(true)}>{t("addNote")}</button>
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
            lang={lang}
            onRetry={retryCloud}
            onRetryField={retryField}
            onWorker={(w) => active && patch(active.id, (c) => ({ worker: { ...c.worker, ...w } }))}
          />
        </section>
      </main>

      <footer className="footer card">
        <b>{t("footer_ai")}</b> {t("footer_confirm")}
        <a href="tel:18001801551">1800-180-1551</a> {t("footer_hours")}
      </footer>

      {historyOpen && (
        <div className="drawer-bg" onClick={() => setHistoryOpen(false)}>
          <aside className="drawer" onClick={(e) => e.stopPropagation()}>
            <div className="pane-head">
              <h3>{t("hist_title")} <small>{t("hist_browser")}</small></h3>
              <button className="link" onClick={() => setHistoryOpen(false)}>{t("hist_close")}</button>
            </div>
            {cases.length === 0 && <p className="hint">{t("hist_empty")}</p>}
            {cases.map((c) => {
              const h = c.field && c.field.obs ? headline(c.field.obs, c.cloud && c.cloud.obs, c.ctx, lang) : null;
              return (
                <button key={c.id} className={"hist" + (c.id === activeId ? " on" : "")} onClick={() => { setActiveId(c.id); setHistoryOpen(false); }}>
                  {c.thumb ? <img src={c.thumb} alt="" /> : <span className="hist-ph" />}
                  <span className="hist-body">
                    <b>{h ? h.issue : c.status === "waiting" ? t("hist_waiting") : c.status === "error" ? t("hist_failed") : t("hist_checking")}</b>
                    <small>{c.id} · {new Date(c.createdAt).toLocaleString(LOCALES[lang])}</small>
                    {h && <span className={"sev-mini tone-" + TONES[h.severity]}>{badgeFor(h.severity, lang)}</span>}
                    {c.status === "waiting" && <span className="sev-mini tone-amber">{t("hist_waiting_pill")}</span>}
                  </span>
                </button>
              );
            })}
            {cases.length > 0 && (
              <button className="link danger" onClick={() => { if (confirm(t("hist_confirm"))) { setCases([]); setActiveId(null); } }}>
                {t("hist_clear")}
              </button>
            )}
          </aside>
        </div>
      )}
    </div>
  );
}
