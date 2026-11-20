# Crop Doctor — #BuildHybridAI 2026

Structured crop-health case capture and follow-up for agricultural extension
workers. Snap a leaf photo in the field with no signal, get an instant
on-device triage, and have the case independently verified in the cloud when
back online. One report, two brains, built for the field.

Built with **Firebase AI Logic hybrid inference**: prompts run on-device
(Gemini Nano) when available and fall back to cloud Gemini otherwise. Every
result is labeled with the path that served it.

## Live app

**https://crop-doctor-d6c91.web.app** · Demo video: **https://youtu.be/mc_QEsn7B_Y**

Try it in one minute, no sign-up:

1. Open the link in **Google Chrome on desktop** (v139+). For the on-device
   path, enable `chrome://flags/#prompt-api-for-gemini-nano-multimodal-input`
   and restart Chrome. The header shows **Gemini Nano ready** when it works.
   Other browsers (including Edge) don't ship Gemini Nano, so everything runs
   on the cloud fallback and the app says so.
2. Tap a sample:
   - **Spotted**: damage case with a severity headline.
   - **Healthy**: both checks agree, shown green.
   - **New shoots**: the two checks may disagree (red young mango leaves
     are normal); the timeline keeps both answers.
   - **Not plant**: safety test, asks for a retake.
3. Set **Last spray → This week** and analyze again: the spray-safety rule
   raises it to high risk and asks for an expert first.
4. Switch to **On-device only** (Chrome with Nano): the photo is never
   uploaded unless you ask for the cloud double-check.

## Repo layout

- `crop-doctor-android/` — the Android app (primary submission). See its
  [README](crop-doctor-android/README.md) for setup and run instructions.
- `web/` — React web app (live at https://crop-doctor-d6c91.web.app) running
  the same hybrid pipeline with Gemini Nano in Chrome. See its
  [README](web/README.md).
- `eval/` — 21 real field photos used as the eval smoke
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

- Live app: https://crop-doctor-d6c91.web.app
- Demo video: https://youtu.be/mc_QEsn7B_Y
- Platform: Android + Web (Chrome)
- License: Apache 2.0 (see LICENSE)
