import { useRef, useState } from "react";
import { getModels, onDeviceStatus } from "./firebase.js";
import { TRIAGE_PROMPT, VERIFY_PROMPT, extractJson, withTimeout, fileToDownscaled, contextText, agreement, isUsable } from "./ai.js";
import { headline } from "./headline.js";

// Runs the 21 real field photos in /eval through the exact app pipeline:
// first check with PREFER_ON_DEVICE, independent check with ONLY_IN_CLOUD,
// same prompts, same headline rules. No context is given, so crop
// identification is measured from the photo alone.
//
// What this measures honestly without an agronomist: crop identification
// (the file name is the crop), whether the two models agree, how often an
// answer says "retake", where each step ran and how long it took. It does
// NOT measure disease accuracy: there are no expert labels yet.

const PHOTOS = [
  "chikoo-01", "chikoo-02", "chili-01", "chili-02", "chili-03", "cotton-01", "cotton-02",
  "fenugreek-01", "maize-01", "maize-02", "mango-01", "mango-02", "papaya-01", "papaya-02",
  "unknown-01", "unknown-02", "unknown-03", "unknown-04", "unknown-05", "wheat-01", "wheat-02",
];

const SYNONYMS = {
  chikoo: ["chikoo", "chiku", "sapodilla", "sapota", "manilkara"],
  chili: ["chili", "chilli", "chile", "pepper", "capsicum"],
  cotton: ["cotton", "gossypium"],
  fenugreek: ["fenugreek", "methi", "trigonella"],
  maize: ["maize", "corn"],
  mango: ["mango", "mangifera"],
  papaya: ["papaya", "carica"],
  wheat: ["wheat", "triticum"],
};

const DEFAULT_CTX = { crop: "unknown", spray: "not_sure", water: "not_sure", stage: "not_sure", note: "" };

function expectedCrop(name) {
  const c = name.split("-")[0];
  return c === "unknown" ? null : c;
}

function cropMatch(expected, guess) {
  if (!expected) return "n/a";
  const g = String(guess || "").toLowerCase();
  if (!g || g === "unknown") return "said unknown";
  return (SYNONYMS[expected] || [expected]).some((w) => g.includes(w)) ? "correct" : "wrong";
}

const sec = (ms) => (ms == null ? "" : (ms / 1000).toFixed(1));
const median = (xs) => {
  const v = xs.filter((x) => x != null).sort((a, b) => a - b);
  if (!v.length) return null;
  const m = Math.floor(v.length / 2);
  return v.length % 2 ? v[m] : (v[m - 1] + v[m]) / 2;
};

async function runOne(name, models) {
  const row = { photo: name, expected: expectedCrop(name) || "unknown" };
  const blob = await (await fetch(`eval/${name}.jpg`)).blob();
  const img = await fileToDownscaled(blob);
  const part = { inlineData: { data: img.base64, mimeType: img.mimeType } };
  const context = contextText(DEFAULT_CTX);
  try {
    const t0 = performance.now();
    const r = await withTimeout(models.prefer.generateContent([TRIAGE_PROMPT + context, part]), 120000, "First check");
    row.fieldMs = Math.round(performance.now() - t0);
    row.fieldSource = r.response.inferenceSource || "unknown";
    row.field = JSON.parse(extractJson(r.response.text()));
  } catch (e) {
    row.error = "first check: " + String(e.message || e);
    return row;
  }
  if (isUsable(row.field)) {
    try {
      const t0 = performance.now();
      const r = await withTimeout(models.cloud.generateContent([VERIFY_PROMPT + context, part]), 120000, "Cloud check");
      row.cloudMs = Math.round(performance.now() - t0);
      row.cloud = JSON.parse(extractJson(r.response.text()));
    } catch (e) {
      row.error = "cloud check: " + String(e.message || e);
    }
  }
  return row;
}

function derive(r) {
  const f = r.field || {};
  const c = r.cloud || null;
  const h = r.field ? headline(f, c, DEFAULT_CTX) : null;
  return {
    photo: r.photo,
    expected: r.expected,
    cropGuess: f.cropGuess || "",
    crop: r.field ? cropMatch(expectedCrop(r.photo), f.cropGuess) : "",
    firstRanOn: r.fieldSource === "on_device" ? "device (Nano)" : r.fieldSource ? "cloud" : "",
    firstSec: sec(r.fieldMs),
    firstExtent: f.extent || "",
    cloudExtent: c ? c.extent : r.field && !isUsable(f) ? "skipped" : "",
    cloudSec: sec(r.cloudMs),
    agreement: c ? agreement(f, c) : "",
    result: h ? h.severity : "",
    seen: f.symptoms || "",
    error: r.error || "",
  };
}

const COLS = ["photo", "expected", "cropGuess", "crop", "firstRanOn", "firstSec", "firstExtent", "cloudExtent", "cloudSec", "agreement", "result", "seen", "error"];

function summary(rows) {
  const d = rows.map(derive);
  const done = d.filter((r) => r.firstRanOn);
  const known = done.filter((r) => r.crop !== "n/a");
  const pair = done.filter((r) => r.agreement);
  const count = (xs, k, v) => xs.filter((r) => r[k] === v).length;
  const results = {};
  done.forEach((r) => { results[r.result] = (results[r.result] || 0) + 1; });
  return {
    photos: `${done.length} of ${rows.length}`,
    firstOnDevice: `${count(done, "firstRanOn", "device (Nano)")} of ${done.length}`,
    cropCorrect: `${count(known, "crop", "correct")} of ${known.length} (said unknown: ${count(known, "crop", "said unknown")}, wrong: ${count(known, "crop", "wrong")})`,
    agree: `${count(pair, "agreement", "match")} of ${pair.length} match, ${count(pair, "agreement", "small_diff")} differ in amount, ${count(pair, "agreement", "updated")} disagree`,
    retake: `${count(done, "result", "RETAKE")} of ${done.length}`,
    results: Object.entries(results).map(([k, v]) => `${k} ${v}`).join(", "),
    medianFirst: `${sec(median(rows.map((r) => r.fieldMs)))} s`,
    medianCloud: `${sec(median(rows.map((r) => r.cloudMs)))} s`,
  };
}

function toCsv(rows) {
  const esc = (v) => `"${String(v ?? "").replace(/"/g, '""')}"`;
  return [COLS.join(","), ...rows.map(derive).map((r) => COLS.map((k) => esc(r[k])).join(","))].join("\n");
}

function toMarkdown(rows, env) {
  const s = summary(rows);
  const d = rows.map(derive);
  const cell = (v) => String(v ?? "").replace(/\|/g, "/").replace(/\n/g, " ");
  return [
    "# Crop Doctor eval results",
    "",
    `Run: ${new Date().toISOString()} · Browser: ${env.browser} · On-device model: ${env.model}`,
    "",
    "Same pipeline as the app: first check with `PREFER_ON_DEVICE`, independent check with `ONLY_IN_CLOUD`,",
    "same prompts and headline rules. No context given, so crop is identified from the photo alone.",
    "This measures crop identification, model agreement, retakes and speed. It does **not** measure",
    "disease accuracy: the photos have no expert labels yet.",
    "",
    "## Summary",
    "",
    "| Measure | Result |",
    "|---|---|",
    `| Photos checked | ${s.photos} |`,
    `| First check ran on device (Gemini Nano) | ${s.firstOnDevice} |`,
    `| Crop identified correctly (named photos) | ${s.cropCorrect} |`,
    `| Nano vs cloud agreement | ${s.agree} |`,
    `| Asked for a retake | ${s.retake} |`,
    `| Results | ${s.results} |`,
    `| Median time, first check | ${s.medianFirst} |`,
    `| Median time, cloud check | ${s.medianCloud} |`,
    "",
    "## Every photo",
    "",
    "| " + COLS.join(" | ") + " |",
    "|" + COLS.map(() => "---").join("|") + "|",
    ...d.map((r) => "| " + COLS.map((k) => cell(r[k])).join(" | ") + " |"),
    "",
  ].join("\n");
}

function download(name, text, type) {
  const a = document.createElement("a");
  a.href = URL.createObjectURL(new Blob([text], { type }));
  a.download = name;
  a.click();
  URL.revokeObjectURL(a.href);
}

export default function Eval() {
  const [rows, setRows] = useState([]);
  const [running, setRunning] = useState(false);
  const [env, setEnv] = useState(null);
  const stop = useRef(false);

  async function run() {
    stop.current = false;
    setRunning(true);
    setRows([]);
    const status = await onDeviceStatus();
    const ua = navigator.userAgent;
    const browser = /Edg\//.test(ua) ? "Edge" : /Chrome\//.test(ua) ? "Chrome " + (ua.match(/Chrome\/(\d+)/) || [])[1] : "other";
    setEnv({ browser, model: status });
    const models = getModels();
    if (status === "downloadable" || status === "downloading") {
      await models.prefer.initializeDeviceModel(() => {});
    }
    const out = [];
    for (const name of PHOTOS) {
      if (stop.current) break;
      out.push(await runOne(name, models));
      setRows([...out]);
    }
    setRunning(false);
  }

  const s = rows.length ? summary(rows) : null;
  return (
    <div className="app eval">
      <header className="topbar">
        <div className="brand">
          <div className="logo">🌿</div>
          <div>
            <div className="title">Crop Doctor · Eval</div>
            <div className="sub">21 real field photos through the same hybrid pipeline as the app.</div>
          </div>
        </div>
        <div className="top-actions">
          <a className="btn-ghost" href="./">← Back to app</a>
        </div>
      </header>

      <section className="card eval-intro">
        <p>
          Each photo gets the first check (<code>PREFER_ON_DEVICE</code>, Gemini Nano when Chrome has it) and the
          independent cloud check (<code>ONLY_IN_CLOUD</code>). No context is given. This measures crop
          identification, agreement between the two models, retakes and speed. It does <b>not</b> measure disease
          accuracy, because the photos have no expert labels yet.
        </p>
        <div className="worker-row">
          <button className="btn-primary eval-run" onClick={run} disabled={running}>
            {running && <span className="spinner" />}
            {running ? `Running ${rows.length + 1} of ${PHOTOS.length}…` : "Run eval (21 photos, about 3–5 min)"}
          </button>
          {running && <button className="btn-ghost" onClick={() => (stop.current = true)}>Stop</button>}
          {!running && rows.length > 0 && (
            <>
              <button className="btn-ghost" onClick={() => download("eval-results.md", toMarkdown(rows, env), "text/markdown")}>Download RESULTS.md</button>
              <button className="btn-ghost" onClick={() => download("eval-results.csv", toCsv(rows), "text/csv")}>Download CSV</button>
            </>
          )}
        </div>
        {env && <p className="hint">Browser: {env.browser} · On-device model: {env.model}</p>}
      </section>

      {s && (
        <section className="card eval-summary">
          <dl className="facts">
            <div><dt>Photos checked</dt><dd>{s.photos}</dd></div>
            <div><dt>First check on device</dt><dd>{s.firstOnDevice}</dd></div>
            <div><dt>Crop correct</dt><dd>{s.cropCorrect}</dd></div>
            <div><dt>Median time</dt><dd>Nano {s.medianFirst} · cloud {s.medianCloud}</dd></div>
          </dl>
          <p className="hint">Agreement: {s.agree}. Retakes: {s.retake}. Results: {s.results}.</p>
        </section>
      )}

      {rows.length > 0 && (
        <div className="card eval-table-wrap">
          <table className="eval-table">
            <thead><tr>
              <th></th><th>Photo</th><th>Crop guess</th><th>Crop</th><th>First check</th><th>Extent (first / cloud)</th><th>Agree</th><th>Result</th><th>Seen</th>
            </tr></thead>
            <tbody>
              {rows.map(derive).map((r) => (
                <tr key={r.photo}>
                  <td><img src={`eval/${r.photo}.jpg`} alt="" /></td>
                  <td><b>{r.photo}</b></td>
                  <td>{r.cropGuess}</td>
                  <td className={"crop-" + r.crop.replace(" ", "-")}>{r.crop}</td>
                  <td>{r.firstRanOn} {r.firstSec && `· ${r.firstSec}s`}</td>
                  <td>{r.firstExtent} / {r.cloudExtent}{r.cloudSec && ` · ${r.cloudSec}s`}</td>
                  <td>{r.agreement}</td>
                  <td>{r.result}</td>
                  <td>{r.error ? <span className="err-line">{r.error}</span> : r.seen}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
