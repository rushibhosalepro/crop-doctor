import { agreement, isUsable, shown } from "./ai.js";
import { headline, actionFor, badgeFor, TONES, priorityLabel } from "./headline.js";
import { tx, STR, LOCALES } from "./i18n.js";

const HELPLINE = "1800-180-1551";
const HELPLINE_TEL = "tel:18001801551";

/** Honest provenance chip: says where a step really ran, or why it didn't. */
function PathChip({ step, t }) {
  if (!step) return null;
  if (step.state === "running") return <span className="chip-path path-run"><span className="dot-spin" /> {t("path_checking")}</span>;
  if (step.state === "waiting") return <span className="chip-path path-wait">{t("path_waiting")}</span>;
  if (step.state === "skipped") return <span className="chip-path path-slate">{t("path_skipped")}</span>;
  if (step.state === "error") return <span className="chip-path path-red">{t("path_failed")}</span>;
  if (step.state === "local") return <span className="chip-path path-device">{t("path_local")}</span>;
  const time = step.ms ? ` · ${(step.ms / 1000).toFixed(1)}s` : "";
  return step.source === "on_device"
    ? <span className="chip-path path-device">{t("path_device")}{time}</span>
    : <span className="chip-path path-cloud">{t("path_cloud")}{time}</span>;
}

function stepState(c, which) {
  const s = c[which];
  if (s && s.obs) return { state: "done", source: s.source, ms: s.ms };
  if (s && s.error) return { state: "error" };
  if (which === "cloud" && c.field && c.field.obs && !isUsable(c.field.obs)) return { state: "skipped" };
  if (which === "cloud" && c.localOnly && c.field && c.field.obs) return { state: "local" };
  if (c.status === "running") {
    const fieldDone = c.field && c.field.obs;
    if (which === "field" || fieldDone) return { state: "running" };
  }
  if (c.status === "waiting") return { state: "waiting" };
  return null;
}

function Banner({ c, lang, t }) {
  const fieldObs = c.field && c.field.obs;
  if (!fieldObs) {
    const waiting = c.status === "waiting";
    return (
      <div className={"banner tone-" + (c.status === "error" ? "red" : "amber")}>
        <span className="sev">{c.status === "error" ? t("banner_failed") : waiting ? t("banner_waiting") : t("banner_checking")}</span>
        <h2>{c.status === "error" ? t("banner_failed_h") : waiting ? t("banner_waiting_h") : t("banner_checking_h")}</h2>
        <p>
          {c.status === "error"
            ? c.error
            : waiting
              ? t("banner_waiting_p")
              : t("banner_checking_p")}
        </p>
      </div>
    );
  }
  const cloudObs = c.cloud && c.cloud.obs;
  const h = headline(fieldObs, cloudObs, c.ctx, lang, c.field.lang);
  const tone = TONES[h.severity];
  const workerSet = c.worker.priority;
  return (
    <div className={"banner tone-" + tone}>
      <span className="sev">
        {badgeFor(h.severity, lang)}
        {h.severity === "HIGH" && t("expert_soon")}
      </span>
      <h2>{h.issue}</h2>
      <p>{actionFor(h.severity, lang)}</p>
      {h.reasons.length > 0 && (
        <ul className="why">
          {h.reasons.map((r) => <li key={r}>{r}</li>)}
        </ul>
      )}
      <div className="banner-actions">
        {h.severity === "HIGH" ? (
          <a className="btn-call" href={HELPLINE_TEL}>{t("call_kcc")}{HELPLINE}</a>
        ) : (
          <span className="call-quiet">
            {t("not_sure_kcc")}<a href={HELPLINE_TEL}>{HELPLINE}</a>
          </span>
        )}
        {workerSet && <span className="worker-tag">{t("confirmed")}{priorityLabel(workerSet, lang)}</span>}
      </div>
    </div>
  );
}

function FieldStep({ c, lang, t, onRetryField }) {
  const st = stepState(c, "field");
  const o = c.field && c.field.obs;
  return (
    <li className="tl-step">
      <span className="tl-dot dot-field" />
      <div className="tl-head">
        <h3>{t("step1_title")} <small>{t("step1_sub")}</small></h3>
        <PathChip step={st} t={t} />
      </div>
      {o && (
        <>
          <dl className="facts">
            <div><dt>{t("fact_crop")}</dt><dd>{c.ctx.crop !== "unknown" && c.ctx.crop !== "other" ? (STR[lang].crops[c.ctx.crop] || c.ctx.crop) : o.cropGuess && o.cropGuess !== "unknown" ? `${o.cropGuess} ${t("fact_guess")}` : t("fact_unknown")}</dd></div>
            <div><dt>{t("fact_where")}</dt><dd>{shown(o, "location", lang, c.field.lang) || t("fact_not_stated")}</dd></div>
            <div><dt>{t("fact_howmuch")}</dt><dd>{o.extent || t("extent_unknown_word")}</dd></div>
            <div><dt>{t("fact_photo")}</dt><dd>{o.photoQuality === "poor" ? t("photo_unclear") : t("photo_clear")}</dd></div>
          </dl>
          <p className="seen"><b>{t("seen")}</b> {shown(o, "symptoms", lang, c.field.lang) || t("seen_nothing")}</p>
          {o.notes && <p className="note-line">{shown(o, "notes", lang, c.field.lang)}</p>}
        </>
      )}
      {st && st.state === "error" && (
        <p className="err-line">
          {c.field.error} <button className="link" onClick={onRetryField}>{t("retry")}</button>
        </p>
      )}
      {st && st.state === "done" && c.field.source !== "on_device" && (
        <p className="hint">{t("no_device_note")}</p>
      )}
    </li>
  );
}

function AgreePill({ c, t }) {
  const words = (e) => t("extent_" + (e || "unknown"));
  const f = c.field && c.field.obs;
  const k = c.cloud && c.cloud.obs;
  if (!f || !k) return null;
  const level = agreement(f, k);
  const both = f.extent === "none" && k.extent === "none";
  if ((both || level === "match") && k.priority === "prioritize_review")
    return <li className="agree warn">{t("agree_match")}{words(f.extent)}{t("agree_match_expert")}</li>;
  if (both || level === "match")
    return <li className="agree ok">{t("agree_match")}{words(f.extent)}</li>;
  if (level === "small_diff")
    return (
      <li className="agree warn">
        {t("agree_diff")}{words(f.extent)}{t("agree_cloud")}{words(k.extent)}{t("agree_diff_end")}
      </li>
    );
  return (
    <li className="agree bad">
      {t("agree_updated")}
    </li>
  );
}

function CloudStep({ c, lang, t, onRetry, onToggleCheck }) {
  const st = stepState(c, "cloud");
  const k = c.cloud && c.cloud.obs;
  return (
    <li className="tl-step">
      <span className="tl-dot dot-cloud" />
      <div className="tl-head">
        <h3>{t("step2_title")}</h3>
        <PathChip step={st} t={t} />
      </div>
      {!st && <p className="hint">{t("cloud_runs_after")}</p>}
      {st && st.state === "skipped" && <p className="hint">{t("cloud_skipped")}</p>}
      {st && st.state === "local" && (
        <p className="hint">
          {t("cloud_local")}
          <button className="link" onClick={onRetry}>{t("cloud_local_btn")}</button>
        </p>
      )}
      {st && st.state === "waiting" && <p className="hint">{t("cloud_saved")}</p>}
      {st && st.state === "error" && (
        <p className="err-line">
          {c.cloud.error} <button className="link" onClick={onRetry}>{t("retry")}</button>
        </p>
      )}
      {k && (
        <>
          <p className="review">{shown(k, "reviewNote", lang, c.cloud.lang)}</p>
          {(k.questionsForExpert || []).length > 0 && (
            <div className="checklist">
              <div className="label">{t("checklist")}</div>
              {shown(k, "questionsForExpert", lang, c.cloud.lang).map((q, i) => (
                <label key={i}>
                  <input type="checkbox" checked={!!c.worker.checks[i]} onChange={() => onToggleCheck(i)} />
                  <span>{q}</span>
                </label>
              ))}
            </div>
          )}
          <p className="hint">{t("cloud_suggests")}<b>{priorityLabel(k.priority, lang)}</b></p>
        </>
      )}
    </li>
  );
}

function WorkerBar({ c, lang, t, onWorker }) {
  if (!c.field || !c.field.obs || !isUsable(c.field.obs)) return null;
  const w = c.worker;
  return (
    <div className="worker">
      <div className="worker-row">
        <span className="label">{t("worker_decision")}</span>
        <div className="seg decision">
          <button className={w.priority === "prioritize_review" ? "on" : ""} onClick={() => onWorker({ priority: "prioritize_review" })}>
            {w.priority === "prioritize_review" ? t("worker_check") : ""}{t("worker_expert")}
          </button>
          <button className={w.priority === "standard_followup" ? "on" : ""} onClick={() => onWorker({ priority: "standard_followup" })}>
            {w.priority === "standard_followup" ? t("worker_check") : ""}{t("worker_routine")}
          </button>
        </div>
        {w.priority && <span className="confirmed">{t("worker_confirmed")}{priorityLabel(w.priority, lang)}</span>}
        <button className={"btn-ghost" + (w.delivered ? " done" : "")} onClick={() => onWorker({ delivered: !w.delivered })}>
          {w.delivered ? t("worker_told") : t("worker_mark")}
        </button>
      </div>
      <div className="worker-row">
        <span className="label">{t("worker_was_right")}</span>
        <button className={"btn-ghost" + (w.feedback === "yes" ? " done" : "")} onClick={() => onWorker({ feedback: "yes" })}>{t("worker_yes")}</button>
        <button className={"btn-ghost" + (w.feedback === "flag" ? " flagged" : "")} onClick={() => onWorker({ feedback: "flag" })}>{t("worker_no")}</button>
      </div>
      {w.feedback === "flag" && (
        <input
          type="text"
          className="flag-note"
          placeholder={t("worker_flag_ph")}
          value={w.flagNote || ""}
          onChange={(e) => onWorker({ flagNote: e.target.value })}
        />
      )}
    </div>
  );
}

export default function CaseFile({ c, lang = "en", onRetry, onRetryField, onWorker }) {
  const t = (key) => tx(lang, key);
  if (!c) {
    return (
      <div className="empty">
        <div className="empty-icon">🌿</div>
        <h2>{t("empty_title")}</h2>
        <p>{t("empty_p")}</p>
        <ol>
          <li>{t("empty_li1")}</li>
          <li>{t("empty_li2")}</li>
          <li>{t("empty_li3")}</li>
        </ol>
      </div>
    );
  }
  const onToggleCheck = (i) => onWorker({ checks: { ...c.worker.checks, [i]: !c.worker.checks[i] } });
  return (
    <div className="casefile">
      <Banner c={c} lang={lang} t={t} />
      <section className="card timeline-card">
        <div className="tl-title">
          <span>{t("timeline")}</span>
          <span className="case-id">{t("case_id")}{c.id} · {new Date(c.createdAt).toLocaleString(LOCALES[lang])}</span>
        </div>
        <ol className="timeline">
          <FieldStep c={c} lang={lang} t={t} onRetryField={onRetryField} />
          <AgreePill c={c} t={t} />
          <CloudStep c={c} lang={lang} t={t} onRetry={onRetry} onToggleCheck={onToggleCheck} />
        </ol>
        <WorkerBar c={c} lang={lang} t={t} onWorker={onWorker} />
      </section>
    </div>
  );
}
