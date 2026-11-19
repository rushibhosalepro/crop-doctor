# #BuildHybridAI 2026 — Hackathon Details

> Source: https://firebasehybridaihackathon.devpost.com/
> Fetched: Nov 19, 2026 | Participants at fetch time: 3263

## Overview

- **Name:** #BuildHybridAI 2026
- **Tagline:** Build AI apps that run on-device and in the cloud with Firebase AI Logic.
- **Organizer:** Google (Sponsor: Google LLC) | **Managed by:** Devpost
- **Format:** Online, Public
- **Themes:** Machine Learning/AI, Mobile, Web
- **Contact:** keshya@devpost.com | Discord: https://discord.gg/Mfbaz3AaC → #hackathon-help (filter #BuildHybridAI tag)

## Core Challenge

Build apps using the **Firebase AI Logic Hybrid Inference SDK** — prompts run **on-device when a local model is available, fall back to cloud when not**.

Example: notes app that summarizes on-device offline, hands long docs to cloud.

- **New + existing apps eligible** (existing must be significantly updated during submission period).
- Individuals, teams, and organizations allowed.

## Timeline (PT)

| Phase | Dates |
|---|---|
| Submissions | Nov 11, 2026 1:30 PM PT → **Nov 30, 2026 2:00 PM PT (hard deadline)** |
| Judging | Dec 9/10 → Dec 16, 2026 (Dec 16 2:00 PM PT) |
| Winners announced | ~Dec 21, 2026 at 12–2 PM PT |

- Deadline is fixed. No extensions. After deadline submission locks; later repo commits not evaluated.
- Upload YouTube video early — processing can take hours.
- To keep building after deadline: clone repo to a new one, leave submitted version untouched.

## Eligibility

- Must be **above age of majority** where you live (≥20 in Taiwan). No minors, no parental consent path.
- Internet access required. No purchase necessary.
- **Excluded residences** (by residence, not citizenship): Brazil, Italy, Quebec, Crimea, Cuba, Iran, Syria, North Korea, Sudan, Belarus, Russia + any OFAC-sanctioned country / US export-controlled persons.
- **Ineligible:** Google / Devpost / Contest Entities employees, interns, contractors, office-holders + immediate family/household; government agency employees; conflicts of interest.
- Companies/orgs OK if employer knows/consents and no policy/work-for-hire violation. Projects funded/contracted/licensed by Google or Devpost are ineligible.

## What to Build

Gen AI app where **all AI inference goes through Firebase AI Logic**, combining on-device + cloud.

**Eligible platforms (pick ≥1):**
- **Web (Chrome):** Gemini Nano 4 via Firebase AI Logic — web app or Chrome extension
- **Android:** Gemini Nano 4 via Firebase AI Logic
- **iOS:** Apple Foundation Models API via Firebase AI Logic

No Windows/macOS native. Multi-platform preferred (strong Grand Prize consideration) but outstanding single-platform can still win.

**Tech requirements:**
- Firebase AI Logic **required** (imported + actually called at runtime, not just README mention).
- **App Check required** (debug tokens accepted; NEVER commit debug tokens, service keys, .env).
- Encouraged: Remote Config, Firestore, Authentication. See production checklist.
- Any IDE/AI coding tool allowed (Antigravity, Android Studio, Claude Code, Codex, Cursor). Firebase Agent Skills recommended: https://firebase.google.com/docs/ai-assistance/agent-skills
- Third-party APIs/SDKs/open-source OK if licensed + you build on them (not repackage). Don't call other model APIs directly — inference via AI Logic.
- Inference modes per feature: `PREFER_ON_DEVICE`, prefer cloud, on-device-only, cloud-only (separate model instance each). E.g. sensitive data on-device-only.
- Gemini Nano 4 on-device: Android + Chrome only (not iPhone). Cloud: Gemini models supported by AI Logic.
- Chrome: enable `chrome://flags` → "Gemma 4 for Built-in AI" (Prompt API).
- Android devices e.g. Pixel 10/11, Galaxy S26+, Z Fold8/Flip8. Without Nano, `PREFER_ON_DEVICE` falls back to cloud online.
- Offline-ready pattern (Android): check model status at startup, background-download if needed — see FriendlyMeals sample.

## What to Submit

1. **Platform selection:** ≥1 platform (organizers may reassign category).
2. **Text description:** how you used Firebase AI Logic + features/functionality + value prop. All in English, free of charge.
3. **Code repo:** Public **GitHub or GitLab**, open-source license file visible in About (Apache preferred, any OSI OK). Must include all source/assets/run instructions + AI Logic at runtime.
4. **Demo video:** ≤3 min (only first 3 min judged), public on **YouTube**, linked in form, English or English subtitles, showing app working on built platform(s). No third-party logos/marks you lack rights to.
   - **First 30 sec:** who it's for, problem, how AI Logic makes it work (advice from Cynthia Jiang, Firebase). Judges may judge from video alone.
5. **Existing apps:** summary of what was built Nov 11–30 + core value pre-existing code brings (+ optional usage stats).
6. **Strongly encouraged:** hosted testable URL (web UI / extension / test build). If login needed, include test credentials. No prod keys/secrets/PII.
7. Multiple submissions OK if substantially different.

## Prizes ($30,000 total)

| Prize | Cash | Extras |
|---|---|---|
| Grand Prize (1) | $10,000 | Interview + Featured blog post + Social mention |
| 1st Runner-Up (1) | $7,000 | Interview + Featured blog post + Social mention |
| 2nd Runner-Up (1) | $4,000 | Social mention |
| Best iOS App (1) | $3,000 | Social mention |
| Best Android App (1) | $3,000 | Social mention |
| Best Web/Chrome App (1) | $3,000 | Social mention |

- One project can win **one grand prize + up to one "Best of" prize**.
- Paid to individual / team Representative / org after forms returned within 10 business days; delivered within 60 days. W-9 (US) / W-8BEN (non-US). Winners pay taxes + transfer fees. Rep splits among team.
- No scores/feedback shared. Judges' decisions final.

## Judging

- **Stage 1:** pass/fail completeness + reasonably addresses challenge.
- **Stage 2:** 1–5 on 4 equally weighted criteria (ties broken in order):
  1. **Technological Implementation** — build quality + effective hybrid inference use
  2. **Design** — complete coherent product, not just PoC
  3. **Potential Impact** — credible specific case for real problem + real audience
  4. **Quality of the Idea** — creative, non-obvious use + problem-space understanding
- May use expert panels + AI-assisted analysis.

## Google Quota Accounts (optional, for billing-required models)

Free tier covers text inference (check model list for "billing required" vs not). For image gen, TTS etc.:

1. Register on Devpost → 2. Submit https://forms.gle/uPcDhaYW3iVo2AYw5 → 3. Devpost eligibility check → 4. ID + selfie verification → 5. Approval email → 6. Sign-in details Thursday → 7. Incognito sign-in, create project, add Firebase, follow AI Logic docs.
- Valid **4 days from issue**. 1 per entrant/project/sprint (individual or team Rep). Can re-apply next sprint for new project.
- Not guaranteed, at Google's discretion. You pay beyond quota / after 4 days.
- Data under Google Privacy Policy.

| Sprint | Request by | Account sent |
|---|---|---|
| Sprint 1 | Wed Nov 11, 12:00 AM PT | Thu Nov 12 |
| Sprint 2 | Wed Nov 18, 10:00 AM PT | Thu Nov 19 |
| Sprint 3 (final) | Wed Nov 25, 10:00 AM PT | Fri Nov 27 |

New window opens each Monday. Issues → Discord. Billing/account blocks → https://firebase.google.com/support (Devpost can't override).

## Key Resources

- Hybrid docs: https://firebase.google.com/docs/ai-logic/hybrid
- AI Logic overview: https://firebase.google.com/products/firebase-ai-logic
- Hybrid inference explainer: https://firebase.blog/posts/2025/10/understand-ai-logic-hybrid-inference/
- Hybrid prompting: https://developer.chrome.com/docs/ai/firebase-ai-logic
- Web get-started: https://firebase.google.com/docs/ai-logic/hybrid/web/get-started
- Android video: https://www.youtube.com/watch?v=UHYXjmWW0PQ
- iOS get-started: https://firebase.google.com/docs/ai-logic/hybrid/ios/get-started
- Apple Foundation Models: https://firebase.google.com/docs/ai-logic/apple-foundation-models-framework/get-started?api=dev
- Gemini Nano direct access (Android): https://developers.google.com/ml-kit/genai/prompt/android/get-started
- Production checklist: https://firebase.google.com/docs/ai-logic/production-checklist
- Models list: https://firebase.google.com/docs/ai-logic/models
- Agent Skills: https://firebase.google.com/docs/ai-assistance/agent-skills
- Build Session recording (Cynthia Jiang, Nov 12): https://youtu.be/lEsZMYkftiU
- Sample code (Android): https://github.com/FirebaseExtended/FriendlyMeals-Android (iOS/web TBD on Resources page)
- Inspiration: https://firebase.blog/posts/2026/09/ai-logic-text-to-speech/ , https://developer.android.com/blog/posts/gemini-and-firebase-ai-logic-enabled-karrot-to-increase-sales-with-a-translation-feature
- Free-tier ideas: realtime voice/camera coach (Gemini Live API + function calling); PDF reader (gemini-3.8-flash + gemini-3.1-flash-tts-preview); multiplayer AI tabletop GM (gemini-3.5-flash-lite + TTS preview)
- Community: https://firebase.google.com/community | firebase-talk: https://groups.google.com/g/firebase-talk

## FAQs / Gotchas

- 429/503/model-not-found → verify model name, retry 503 with backoff, check billing requirement.
- App must at least support English; all materials English or with translations.
- Delete duplicate drafts via My Projects; withdraw via #hackathon-help; unregister via link in hackathon emails.
- If entering for employer: get consent incl. prize receipt.
