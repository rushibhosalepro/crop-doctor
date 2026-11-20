# Crop Doctor: Android app

Structured crop-health case capture and follow-up for extension workers.
**Not** a diagnosis engine: the model describes, rules constrain, the trained
human decides. Full product reasoning: `../crop-doctor-spec.md`.

## What the MVP does

- Snap 1 leaf photo, tap context chips (all skippable), optional note.
- Field observations via Firebase AI Logic (`PREFER_ON_DEVICE`, offline on
  Nano devices) + local rules engine proposes priority with reasons.
- Independent cloud review (`ONLY_IN_CLOUD`, photo re-analyzed without seeing
  the field result), timeline shows match or updated assessment.
- Worker confirms/overrides priority, marks answer delivered, leaves feedback.
- History in Firestore. Escalation card + helpline on priority cases.

## Prereqs

- Android Studio Ladybug or newer (bundled JDK 17), Android SDK installed.
- A Google account for the Firebase console.
- This machine has no Android tooling. Open this folder in Android Studio.

## Firebase console setup (do once, ~20 min)

1. Create a project at console.firebase.google.com.
2. Run the **Build with AI Logic** guided setup, choose **Gemini Developer API**.
3. Register one Android app with package `com.cropdoctor.app`.
   Download `google-services.json` and put it in `app/`.
   It is git-ignored. NEVER commit it.
4. **App Check:** enforce for Firebase AI Logic. Run the app once, copy the
   debug token from logcat (`DebugAppCheckProvider`), register it under
   App Check > Apps > Manage debug tokens. NEVER commit the token.
5. **Authentication:** enable Anonymous sign-in.
6. **Firestore:** create database. For the hackathon use test mode, then
   tighten to authenticated users before any real use.

## Shareable debug APK with a predefined App Check token

A normal debug build makes a new random App Check token on every phone, so a
judge's phone is rejected until someone registers its token. A predefined
token fixes this: one token, registered once, baked into the debug APK.

1. Make a token: any random UUID. In PowerShell: `[guid]::NewGuid()`.
2. Register it: Firebase console → App Check → Apps → the Android app
   (`com.cropdoctor.app`) → ⋮ → **Manage debug tokens** → Add, name it
   `judges-apk`, paste the UUID.
3. Add it to `crop-doctor-android/local.properties` (git-ignored):
   ```
   appcheck.debugToken=PASTE-THE-UUID
   ```
   `app/build.gradle` turns this into the string resource
   `firebase_app_check_debug_token`, which the App Check debug provider
   reads in debuggable builds only (release builds ignore it). Logcat shows
   "Debug token loaded from R.string.firebase_app_check_debug_token".
4. Build: Android Studio → Build → Build App Bundle(s) / APK(s) → **Build
   APK(s)**. Output: `app/build/outputs/apk/debug/app-debug.apk`.
5. Share the APK privately (submission form or a private Drive link), never
   in the public repo or a GitHub release.
6. After judging ends, delete the token in Manage debug tokens. Every copy
   of the APK stops working at once.

Risk to know: anyone with the APK can extract the token and call your
Firebase AI Logic quota until you delete it. That's why it is shared
privately and revoked after judging. Never commit `local.properties`.

## Run

1. Open `crop-doctor-android` in Android Studio, let Gradle sync.
   First sync downloads a lot, so allow 10+ minutes on slow networks.
2. Run unit tests: `./gradlew :app:testDebugUnitTest` (rules engine safety).
3. Run the app on a real phone. Emulator works for UI/cloud but has no Nano.
4. On first launch the status pill shows the on-device model state:
   AVAILABLE (Nano ready), DOWNLOADABLE/DOWNLOADING, or UNAVAILABLE.

## Demo (airplane mode)

- ON: analyze a photo. On a Nano device you get field observations offline;
  verification shows PENDING. On other phones triage fails honestly with an
  error. The case flow, rules, and queue still work.
- OFF: press Verify (or it auto-runs). Timeline shows match or update.

## Nano phone hunt (India)

Your own phones are ruled out (Narzo 50A, C53: no AICore). Borrow one of:
Pixel 9/10, OnePlus 13/15, Xiaomi 15, POCO F7 Ultra/X7 Pro, iQOO 13,
realme GT 7 Pro, Galaxy S25/S26. One day is enough for video + eval set.

## Known MVP limits

- Photos stay on the phone (Firestore holds metadata only). Firebase Storage
  is the next step, not this MVP.
- Bundled Spotted/Healthy samples are real field photos from the root `eval/` folder.
  The Not-plant sample is still a placeholder until a real negative photo
  is added; the eval set itself is real photos only.
- No cloud follow-up chat (first on the cut list). One-shot review only.
- JSON is prompt-shaped + parsed, not KSP `@Generable` schemas yet. The
  parser falls back to `unclear`, never to a guess.
- If Android Studio flags any `firebase-ai` import, tell the developer:
  the SDK is experimental and symbols drift. The versions here match the
  Firebase hybrid docs of Nov 2026.
