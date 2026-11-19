# Crop Doctor — Web proof page (React + Chrome + Gemini Nano)

Minimal React app proving the same hybrid pipeline as the Android app,
runnable on any laptop with Chrome. Purpose: demonstrate real on-device
inference (Wi-Fi off) when no Nano phone is available, and make testing easy.

## Prereqs

- Node.js 18 or higher (`node --version`).
- Chrome on desktop, version 139 or higher (`chrome://settings/help`).
- Enable the on-device model: open `chrome://flags`, search for
  "prompt" / "Gemini Nano" / "Gemma", and enable the Prompt API
  ("Gemma 4 for Built-in AI") and the multimodal-input flag. Restart Chrome.

## Firebase setup (one time, ~30 min)

1. Firebase console → Project Overview → Add app → **Web** (`</>`).
   Copy the `firebaseConfig` object into `src/firebase.js`.
2. Create a reCAPTCHA Enterprise key: Cloud console (same project) →
   Fraud Defense → Create key → Website → add domain
   `crop-doctor-d6c91.web.app` (do NOT add localhost).
   Then do BOTH: (a) paste the site key as `RECAPTCHA_SITE_KEY` in
   `src/firebase.js` (site keys are public, safe to commit); (b) register
   it in Firebase console → App Check → Apps → your web app.
   Localhost note: with the debug flag in `src/firebase.js`, no captcha runs
   locally; the key only matters for the hosted site.
3. Firebase console → App Check → Apps → find the web app → register it
   with that key if asked.
4. Run the dev server below, open the page, open devtools console, copy the
   `AppCheck debug token` line, and register it under
   App Check → Apps → your web app → Manage debug tokens.
   NEVER commit the token. It stays in your browser only.

## Run

```sh
cd web
npm install
npm run dev
```

Open the printed `http://localhost:3000` URL in Chrome.

For a clean demo build: `npm run build`, then serve `dist/`
(`npx serve dist`) and open that URL instead.

## Demo script (the money shot)

1. Click **Prepare on-device model**, wait for 100% (first download takes
   minutes; one time only).
2. Tap a sample. Badge should read **Ran ON THIS DEVICE**.
3. Turn laptop Wi-Fi OFF. Tap a sample again. It still works.
   That is the on-device proof for the video.
4. Turn Wi-Fi on. Cloud verification appears with its own badge.

## Hosting (public demo URL for judges)

```sh
npm install -g firebase-tools
firebase login
cd web && npm run build && cd ..
firebase deploy --only hosting
```

You get `https://crop-doctor-d6c91.web.app`. Before sharing it:

1. Add that domain to your reCAPTCHA Enterprise key's allowed domains
   (Cloud console). Do NOT add localhost there — localhost uses the debug
   token flow instead.
2. Open the URL in Chrome, Prepare the model once, tap a sample.
3. Judges pass App Check transparently (invisible captcha). Nothing for
   them to register.

Redeploy after any change: `cd web && npm run build && cd .. && firebase deploy --only hosting`.

## Troubleshooting

- `npm install` fails: check Node 18+ and re-run.
- App Check errors: wrong/absent debug token, or config pasted from the
  Android app instead of a Web app. Both must be the web app's.
- `ONLY_ON_DEVICE` waits for a pending download instead of failing fast.
  Always Prepare first.
- Only Chrome on desktop works. Mobile Chrome and other browsers fall back
  to cloud or fail — that is expected, not a bug.
