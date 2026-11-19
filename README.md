# Crop Doctor — #BuildHybridAI 2026

Structured crop-health case capture and follow-up for agricultural extension
workers. Snap a leaf photo in the field with no signal, get an instant
on-device triage, and have the case independently verified in the cloud when
back online. One report, two brains, built for the field.

Built with **Firebase AI Logic hybrid inference**: prompts run on-device
(Gemini Nano / Apple Foundation Models) when available and fall back to cloud
Gemini otherwise. Every result is labeled with the path that served it.

## Repo layout

- `crop-doctor-android/` — the Android app (primary submission). See its
  [README](crop-doctor-android/README.md) for setup and run instructions.
- `web/` — minimal Chrome proof page running the same hybrid pipeline on a
  laptop (Gemini Nano in Chrome). See its [README](web/README.md).
- `crop-doctor-android/eval/` — 21 real field photos used as the eval smoke
  set (preliminary testing, not agricultural validation).

## Hybrid design (30 seconds)

1. **Field check** (`PREFER_ON_DEVICE`): photo + context chips → structured
   observations JSON. Works offline on Nano hardware, free and private.
2. **Local rules engine**: observations + context → priority proposal with
   human-readable reasons. Danger signs escalate; info gaps ask for more.
3. **Cloud verification** (`ONLY_IN_CLOUD`): the photo is re-analyzed
   independently (the field result is never shown to it, anti-anchoring).
   Timeline shows match or updated assessment.
4. **Worker decides**: extension worker confirms/overrides priority, marks
   the answer delivered, logs feedback. Nothing auto-clears a case.

## Safety contract (enforced in code, tested)

- The model describes observations only. No disease names, no dosage, no
  chemical advice, no spray decisions — ever.
- "Looks healthy" appears only when the field is clean AND the cloud
  independently agrees AND zero danger flags exist. Offline-only cases can
  never show green.
- Unknown crops and missing context escalate to a human; they never guess.
- Every escalation explains its reason. Every priority needs worker confirm.
- **Demo only. No real-world agricultural use before a KVK/SAU agronomist
  reviews the rules.** See the rules engine and unit tests.

## Run it

- Android: open `crop-doctor-android` in Android Studio; follow its README
  (Firebase project, `google-services.json`, App Check debug token).
- Web: `cd web` + any localhost server; follow its README (web app config,
  reCAPTCHA key, Chrome flags, model download).

## Submission

- Demo video: (link added before deadline)
- Platform: Android (+ Web proof page)
- License: Apache 2.0 (see LICENSE)
