# Crop Doctor — #BuildHybridAI 2026 Spec

> Solo build. Android app. One core feature: offline leaf check in the field.
> Source of truth for rules: `firebase-hybrid-ai-hackathon-details.md`.
> v2: judge feedback folded in (triage framing, safety design, context intake,
> independent cloud verification, eval plan, cut list).

## Idea in one line

Crop Doctor helps extension workers manage uncertain crop-health cases in
the field: record the evidence, flag immediate precautions, and ensure the
case is followed up when expert verification becomes possible.
One report, two brains, built for the field.

## Problem and user

- End beneficiary: small farmers with poor or no network in the field.
- v1 user (honest reach): agri extension workers and agri-input shop owners
  with better phones, who advise dozens of farmers and need offline triage
  in the field. Direct-to-farmer is the roadmap, not the v1 claim.
- Problem: crop disease spreads fast, but advice apps need internet and cost
  data/money per check.
- Promise: free instant triage offline, verified detail when online.

## Core feature (the only thing that must work)

Offline leaf check — triage, never diagnosis:

1. Open app, snap 1 leaf photo. Client-side sharpness check prompts a retake
   for blurry photos before any AI call.
2. Optional context chips (never blocking): watered when, last fertilizer,
   last spray/pesticide, weather lately, crop stage. Each has "not sure".
   Skipping still gives a result; "not sure" lowers confidence.
3. On-device triage runs offline (Nano via AI Logic) and returns structured
   JSON: verdict enum, confidence, crop, symptom, severity, 1-line next step.
4. Report saves to history immediately, with an "offline triage" badge.
5. Back online, cloud analyzes the photo + context INDEPENDENTLY first
   (no Nano JSON shown, anti-anchoring), then compares with Nano's triage.
6. UI shows a timeline: "Field triage (offline)" then "Verified analysis
   (online)" with explicit agreement or "updated assessment" on conflict.
   Both kept; cloud never silently overwrites.
7. High severity triggers an escalation card: "show this to an extension
   officer before spraying" + Kisan helpline number on every result.
8. "Was this right?" feedback button logs corrections for the README.
9. Disclaimer on every result: "AI guess — confirm with a local expert."

## Hybrid split (online + offline)

| | Offline | Online |
|---|---|---|
| Model | Nano on-device | Gemini cloud |
| Context | fixed chips in UI | adaptive chat follow-ups |
| Analysis | first triage | independent, then compare |
| Output | verdict JSON | verified plan + chat |
| Cost | free, private | cloud quota |

Rules from the docs:

- On-device is single-turn text generation only, no chat. One Bitmap image max.
  Image input on Android confirmed in Firebase docs; on-device check pending.
- Chat and long reasoning live in cloud only. This is what makes hybrid honest.
- Use a separate model instance per inference mode (`PREFER_ON_DEVICE` primary
  with automatic fallback — not a manual `if (online)` branch).
- Check model status at startup, background-download if needed (FriendlyMeals
  pattern), so the field check works before the farmer needs it.
- If Nano is missing, `PREFER_ON_DEVICE` falls back to cloud when online.
  Every result is labeled with which path served it.
- Privacy claim must be precise: photos stay on the phone until sync, then
  upload for verification. Stretch: "keep on device only" toggle using
  on-device-only mode (named in hackathon rules).

## Output schema (both models share it)

- verdict: `disease_suspected / healthy / unclear / not_plant`
- confidence: low / medium / high ("not sure" context lowers it)
- crop, symptom, severity (low/medium/high), next_step (1 line)
- context: { watered, fertilizer, spray, weather, crop_stage }
- Inventing a disease is a failure. `unclear` and `not_plant` are first-class
  results, demoed in the video (healthy leaf + non-plant photo).

## Safety design (a disclaimer alone is not enough)

- No exact dosage numbers from any model, ever. Cloud gives treatment OPTIONS
  to discuss with an expert.
- Spray history is a safety input: flag "possible spray damage" as a verdict
  candidate; never recommend another chemical on top of a recent spray.
- High severity always shows the escalation card before treatment options.
- Helpline on every result: Kisan Call Centre 1800-180-1551 (toll-free,
  6 AM-10 PM, local languages; verified Nov 2026, now also called KHC).

## Safety Contract (non-negotiable, added v3)

**Status:** required for hackathon build. **Deployment:** demo only; no
real-world agricultural use before expert review.

Governing principle: the model describes, rules constrain, the trained human
decides. This is structured crop-health case capture and follow-up — not a
diagnosis or treatment system.

Model may return only: visible symptoms + location, approximate extent,
image-quality uncertainty, explicit `unclear` / `not_plant`. Never severity
as diagnosis, urgency as treatment, disease identity, or treatment.

Rules engine: every agronomic rule carries source + applicability +
validation status, traceable to recognised guidance or documented expert
review. `UNVALIDATED` rules may escalate only — never reassure, recommend
treatment, or clear a case. No universal damage threshold presented as
authoritative. The worker confirms or overrides every priority proposal.

Prohibited outputs (never autonomous): confirmed disease/pest identity,
chemical selection, dosage, spray decisions, "safe to leave" clearance,
destroy/retain calls. No chemical wording ships without an agronomist
approving its exact text + conditions.

Escalation: unknown crop, insufficient info, and uncertain observations are
explicit states. Recent spray is recorded context + mandatory expert review.
No routine/low-priority auto-clearance. Every escalation explains its reason.

Accountability: the worker owns final priority and farmer communication;
the app records decisions, overrides, and verifications.

Testing: separate suites for model observations (healthy/diseased/blurry /
non-plant), rules unit tests, prohibited-output controls, and under/over-
escalation rates reported separately. The 30-photo set is preliminary
testing, never agricultural validation.

Deployment gate: no real-world use until a KVK/SAU agronomist reviews
rules, sources, and user-facing wording. The demo validates architecture +
workflow only.

## Pilot success metric

Primary: verified follow-up rate = uncertain cases where the farmer gets an
expert-verified answer within 7 days of the visit. Baseline from a 2-week
worker diary before the pilot; target >=75% in pilot (raw counts, n=20
directional). Requires the mark-delivered button. Guardrails: worker time
per case, priority override rate, farmer-acted-before-answer rate. Kill
criteria: workers don't capture in field, or answers arrive after farmers
already acted.

## Eval plan (smoke test, not a benchmark)

- ~30 labeled photos: diseased, healthy, blurry, non-plant. Run Nano, commit
  the agreement table to the repo. Present as smoke test, no accuracy claims.
- 5-10 photos WITH context pairs (same yellow leaf, "watered daily" vs
  "never watered") to show the answer changes with context.
- Blocker: eval needs a real Nano device. Device question still open.

## Evidence log (impact needs proof)

- Before deadline: 2 short interviews (1 farmer, 1 agri-shop owner).
- Log in README: name, date, one quote, one finding each. No fake personas.

## Why this convinces judges

- Technological: one report, graceful offline-to-online degradation, correct
  Nano limits use (single-turn, one image, JSON), independent verification
  instead of anchoring, App Check on.
- Design: 30-second photo-first flow, verdict + confidence honesty, timeline
  UI, severity colors/icons readable with low literacy.
- Impact: specific users, free offline checks, safety-by-design, 2 interviews.
- Idea: the pipeline is the idea — offline triage with explicit uncertainty,
  verified in cloud, context-aware. Not a chat wrapper.

## Tech stack

- Android (Kotlin), Firebase AI Logic SDK v17.10.0+ (BoM v34.10.0+).
- On-device: ML Kit Prompt API with Gemini Nano (beta, specific devices).
- Cloud: Gemini via AI Logic (Gemini Developer API).
- Required: App Check (debug tokens for dev, never committed).
- Encouraged: Anonymous Auth, Firestore history, Remote Config for tuning.
- Fixed UI strings in Hindi/Marathi via static translation (stretch, no voice).
- All AI inference goes through AI Logic. No direct model API calls.

## Scope lock (solo, ~11 days left)

In: leaf check flow, context chips, offline observations + worker-confirmed
priority, independent cloud verification + timeline, escalation + helpline,
history, model pre-download, sharpness check, feedback button,
mark-delivered button, airplane-mode demo, eval table, 2 interviews.
Out: user accounts with passwords, community radar, maps, multi-image,
voice, dosage calculator.

Cut list if Nov 26 arrives and core slips (cut top first, never the bottom):
1. Cloud follow-up chat (keep one-shot cloud plan) — cut first.
2. Hindi/Marathi labels.
3. Feedback button (keep a paper log of corrections instead).
4. Context eval pairs (keep the 30-photo smoke test).
NEVER cut: verdict/confidence schema, escalation + helpline, healthy and
non-plant handling, eval table, airplane-mode demo.

## Demo video script (max 3 min)

- First 30 sec: who (extension workers/farmers, no network), problem
  (slow advice), how AI Logic fixes it (Nano offline triage, cloud verify).
- 0:30-1:30: airplane mode ON, snap photo, show verdict JSON + badge.
- 1:30-2:10: airplane mode OFF, independent verification + timeline,
  one agreement and one "updated assessment" if available.
- 2:10-2:40: healthy leaf and non-plant photo handling.
- 2:40-3:00: history sync, App Check note, closing line.

## Build plan (Nov 19-30)

- Nov 19-20: Android project + Firebase + App Check + model status check.
  Settle the Nano device question here or replan.
- Nov 21-23: camera + chips + on-device verdict JSON + history save.
- Nov 24-26: cloud verification + timeline + escalation + helpline.
  CUT DECISION on Nov 26 by the list above.
- Nov 27-28: polish, error states, eval table, interviews, README.
- Nov 29: record video early (processing takes hours), test on real device.
- Nov 30 before 2 PM PT: submit, freeze repo. New repo if building further.

## Risks and fallbacks

- Biggest risk: no Nano test device. OWN PHONES RULED OUT (Nov 19): realme
  Narzo 50A (RMX3430) and realme C53 (RMX3762) have no AICore. Hunt list for
  India: Pixel 9/10, OnePlus 13/15, Xiaomi 15, POCO F7 Ultra/X7 Pro, iQOO 13,
  realme GT 7 Pro, Galaxy S25/S26. Borrow 1-2 days for video + eval.
  Deadline Nov 20, else replan to web/kiosk. Fallback stays labeled so
  judges see the design even if the demo is cloud-only.
- Second risk: scope creep. Fix: cut list above, chat dies first.
- Third risk: secrets in repo. Fix: no keys, tokens, or .env committed.
- Verified: Nano single-image input (Firebase docs), helpline 1800-180-1551
  (multiple 2026 sources). Pending: on-device Nano behavior, 2 interviews.

## Submission checklist

- Public GitHub repo, open-source license, run instructions, AI Logic at runtime.
- README includes: eval smoke-test table, 2-interview log, privacy statement.
- Text description: AI Logic use + features + value prop, in English.
- YouTube video under 3 min, public, English, shows app on Android.
- Platform selected: Android. Test build or APK notes if login needed.
- No prod keys, no PII, English supported at minimum.
