# Crop Doctor: #BuildHybridAI 2026

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
   - **Green leaf**: a mature leaf that looks fine to the eye. In the eval
     both checks still found small marks on it, so it may not show green.
   - **New shoots**: the two checks may disagree (red young mango leaves
     are normal); the timeline keeps both answers.
   - **Not plant**: safety test, asks for a retake.
3. Set **Last spray → This week** and analyze again: the spray-safety rule
   raises it to high risk and asks for an expert first.
4. Switch to **On-device only** (Chrome with Nano): the photo is never
   uploaded unless you ask for the cloud double-check.
5. Switch the language to **मराठी** or **हिन्दी**. All of the app's own text
   changes; the AI's free-text descriptions stay in English.

## Who actually gets the offline check

Gemini Nano runs only on capable hardware: Chrome on a capable desktop or
laptop, and recent high-end Android phones. Most farmers' mid-range phones
don't have it. So:

- **Offline on-device check:** extension workers and agri-shop owners with a
  capable laptop or a newer phone. One of them advises many farmers, so one
  capable device covers many fields.
- **Every other phone:** the photo and answers are still saved with no
  signal, and the check runs in the cloud as soon as signal returns. The
  farmer gets the answer later, but still before spending money on a spray.

On-device is not faster (median 3.6 s on device vs 3.8 s in the cloud in
the eval). Its value is working with no signal, keeping the photo private,
and costing nothing per check.

## Repo layout

- `web/`: React web app, **the main platform** (live at
  https://crop-doctor-d6c91.web.app). Tested with real on-device Gemini Nano
  in Chrome, online and offline. See its [README](web/README.md).
- `crop-doctor-android/`: the Android app, with the same prompts and rules.
  On the phones I had, it runs on the cloud fallback, and the status pill
  shows which path ran. Its on-device path uses the same Firebase AI Logic
  setup that ran on-device on the web 21 of 21 times, but I haven't had a
  Gemini Nano phone to run it on yet.
  See its [README](crop-doctor-android/README.md).
- `eval/`: 21 real field photos used as the eval smoke
  set (preliminary testing, not agricultural validation).
  Results: [eval/RESULTS.md](eval/RESULTS.md). All 21 first checks ran on
  Gemini Nano (median 3.6 s), the two checks agreed on damage within one
  level for 17 of 20, and crop guessing from the photo alone was poor
  (2 of 16), so the crop should come from the user's tap.

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
  chemical advice, no spray decisions, ever.
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
- Platform: Web (Chrome), main; Android
- License: Apache 2.0 (see LICENSE)
