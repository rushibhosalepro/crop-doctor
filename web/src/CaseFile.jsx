import { agreement, isUsable } from "./ai.js";
import { headline, ACTIONS, BADGES, TONES, priorityLabel } from "./headline.js";

const HELPLINE = "1800-180-1551";
const HELPLINE_TEL = "tel:18001801551";

/** Honest provenance chip: says where a step really ran, or why it didn't. */
function PathChip({ step }) {
  if (!step) return null;
  if (step.state === "running") return <span className="chip-path path-run"><span className="dot-spin" /> Checking…</span>;
  if (step.state === "waiting") return <span className="chip-path path-wait">⏳ Waiting for signal</span>;
  if (step.state === "skipped") return <span className="chip-path path-slate">Skipped</span>;
  if (step.state === "error") return <span className="chip-path path-red">Failed</span>;
  return step.source === "on_device"
    ? <span className="chip-path path-device">● On this device · Gemini Nano</span>
    : <span className="chip-path path-cloud">☁ Cloud · Gemini</span>;
}

function stepState(c, which) {
  const s = c[which];
  if (s && s.obs) return { state: "done", source: s.source };
  if (s && s.error) return { state: "error" };
  if (which === "cloud" && c.field && c.field.obs && !isUsable(c.field.obs)) return { state: "skipped" };
  if (c.status === "running") {
    const fieldDone = c.field && c.field.obs;
    if (which === "field" || fieldDone) return { state: "running" };
  }
  if (c.status === "waiting") return { state: "waiting" };
  return null;
}

function Banner({ c }) {
  const fieldObs = c.field && c.field.obs;
  if (!fieldObs) {
    const waiting = c.status === "waiting";
    return (
      <div className={"banner tone-" + (c.status === "error" ? "red" : "amber")}>
        <span className="sev">{c.status === "error" ? "CHECK FAILED" : waiting ? "SAVED · WAITING FOR SIGNAL" : "CHECKING"}</span>
        <h2>{c.status === "error" ? "Couldn't check this photo" : waiting ? "Case saved on this device" : "Looking at the leaf…"}</h2>
        <p>
          {c.status === "error"
            ? c.error
            : waiting
              ? "No signal and no on-device model in this browser. The check runs by itself when you're back online."
              : "This takes a few seconds."}
        </p>
      </div>
    );
  }
  const cloudObs = c.cloud && c.cloud.obs;
  const h = headline(fieldObs, cloudObs, c.ctx);
  const tone = TONES[h.severity];
  const workerSet = c.worker.priority;
  return (
    <div className={"banner tone-" + tone}>
      <span className="sev">
        {BADGES[h.severity]}
        {h.severity === "HIGH" && " · EXPERT REVIEW SOON"}
      </span>
      <h2>{h.issue}</h2>
      <p>{ACTIONS[h.severity]}</p>
      {h.reasons.length > 0 && (
        <ul className="why">
          {h.reasons.map((r) => <li key={r}>{r}</li>)}
        </ul>
      )}
      <div className="banner-actions">
        <a className="btn-call" href={HELPLINE_TEL}>📞 Call Kisan Call Centre {HELPLINE}</a>
        {workerSet && <span className="worker-tag">Worker set: {priorityLabel(workerSet)}</span>}
      </div>
    </div>
  );
}

function FieldStep({ c }) {
  const st = stepState(c, "field");
  const o = c.field && c.field.obs;
  return (
    <li className="tl-step">
      <span className="tl-dot dot-field" />
      <div className="tl-head">
        <h3>1. First check <small>(field triage)</small></h3>
        <PathChip step={st} />
      </div>
      {o && (
        <>
          <dl className="facts">
            <div><dt>Crop</dt><dd>{c.ctx.crop !== "unknown" && c.ctx.crop !== "other" ? c.ctx.crop : o.cropGuess && o.cropGuess !== "unknown" ? `${o.cropGuess} (guess)` : "Unknown"}</dd></div>
            <div><dt>Where</dt><dd>{o.location || "—"}</dd></div>
            <div><dt>How much</dt><dd>{o.extent || "unknown"}</dd></div>
            <div><dt>Photo</dt><dd>{o.photoQuality === "poor" ? "Unclear" : "Clear enough"}</dd></div>
          </dl>
          <p className="seen"><b>Seen:</b> {o.symptoms || "nothing"}</p>
          {o.notes && <p className="note-line">{o.notes}</p>}
        </>
      )}
      {st && st.state === "error" && <p className="err-line">{c.field.error}</p>}
      {st && st.state === "done" && c.field.source !== "on_device" && (
        <p className="hint">No on-device model in this browser, so this check ran in the cloud.</p>
      )}
    </li>
  );
}

function AgreePill({ c }) {
  const f = c.field && c.field.obs;
  const k = c.cloud && c.cloud.obs;
  if (!f || !k) return null;
  const level = agreement(f, k);
  const both = f.extent === "none" && k.extent === "none";
  if (both || level === "match") return <li className="agree ok">✓ Both checks agree{both ? ": no problem seen" : ""}</li>;
  if (level === "small_diff")
    return <li className="agree warn">≈ Mostly agree. First check: {f.extent}, cloud: {k.extent}. The worse one is used.</li>;
  return <li className="agree bad">⚠ Updated assessment. The cloud sees this differently. Both are kept.</li>;
}

function CloudStep({ c, onRetry, onToggleCheck }) {
  const st = stepState(c, "cloud");
  const k = c.cloud && c.cloud.obs;
  return (
    <li className="tl-step">
      <span className="tl-dot dot-cloud" />
      <div className="tl-head">
        <h3>2. Independent cloud double-check</h3>
        <PathChip step={st} />
      </div>
      {!st && <p className="hint">Runs after the first check.</p>}
      {st && st.state === "skipped" && <p className="hint">Not needed: the photo isn't a usable leaf photo.</p>}
      {st && st.state === "waiting" && <p className="hint">Saved. Runs automatically when the connection is back.</p>}
      {st && st.state === "error" && (
        <p className="err-line">
          {c.cloud.error} <button className="link" onClick={onRetry}>Retry</button>
        </p>
      )}
      {k && (
        <>
          <p className="review">{k.reviewNote}</p>
          {(k.questionsForExpert || []).length > 0 && (
            <div className="checklist">
              <div className="label">Check on the spot</div>
              {k.questionsForExpert.map((q, i) => (
                <label key={i}>
                  <input type="checkbox" checked={!!c.worker.checks[i]} onChange={() => onToggleCheck(i)} />
                  <span>{q}</span>
                </label>
              ))}
            </div>
          )}
          <p className="hint">Cloud suggests: <b>{priorityLabel(k.priority)}</b></p>
        </>
      )}
    </li>
  );
}

function WorkerBar({ c, onWorker }) {
  if (!c.field || !c.field.obs || !isUsable(c.field.obs)) return null;
  const w = c.worker;
  return (
    <div className="worker">
      <div className="worker-row">
        <span className="label">Your decision</span>
        <div className="seg">
          <button className={w.priority === "prioritize_review" ? "on" : ""} onClick={() => onWorker({ priority: "prioritize_review" })}>
            Expert soon
          </button>
          <button className={w.priority === "standard_followup" ? "on" : ""} onClick={() => onWorker({ priority: "standard_followup" })}>
            Routine
          </button>
        </div>
        <button className={"btn-ghost" + (w.delivered ? " done" : "")} onClick={() => onWorker({ delivered: !w.delivered })}>
          {w.delivered ? "✓ Told the farmer" : "Mark told to farmer"}
        </button>
      </div>
      <div className="worker-row">
        <span className="label">Was this right?</span>
        <button className={"btn-ghost" + (w.feedback === "yes" ? " done" : "")} onClick={() => onWorker({ feedback: "yes" })}>👍 Yes</button>
        <button className={"btn-ghost" + (w.feedback === "flag" ? " flagged" : "")} onClick={() => onWorker({ feedback: "flag" })}>⚑ Wrong</button>
      </div>
      {w.feedback === "flag" && (
        <input
          type="text"
          className="flag-note"
          placeholder="What was wrong? (saved with the case)"
          value={w.flagNote || ""}
          onChange={(e) => onWorker({ flagNote: e.target.value })}
        />
      )}
    </div>
  );
}

export default function CaseFile({ c, onRetry, onWorker }) {
  if (!c) {
    return (
      <div className="empty">
        <div className="empty-icon">🌿</div>
        <h2>No case open</h2>
        <p>Pick a photo or tap a sample. The result shows here: what's wrong, how serious, and what to do.</p>
        <ol>
          <li>First check, on this device when it can, otherwise in the cloud</li>
          <li>Independent cloud double-check</li>
          <li>You confirm, and the case is saved to History</li>
        </ol>
      </div>
    );
  }
  const onToggleCheck = (i) => onWorker({ checks: { ...c.worker.checks, [i]: !c.worker.checks[i] } });
  return (
    <div className="casefile">
      <Banner c={c} />
      <section className="card timeline-card">
        <div className="tl-title">
          <span>Hybrid check timeline</span>
          <span className="case-id">Case {c.id} · {new Date(c.createdAt).toLocaleString()}</span>
        </div>
        <ol className="timeline">
          <FieldStep c={c} />
          <AgreePill c={c} />
          <CloudStep c={c} onRetry={onRetry} onToggleCheck={onToggleCheck} />
        </ol>
        <WorkerBar c={c} onWorker={onWorker} />
      </section>
    </div>
  );
}
