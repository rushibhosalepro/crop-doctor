# Crop Doctor eval results

Run: 2026-11-20T08:34:04.386Z · Browser: Chrome 157 · On-device model: available

Same pipeline as the app: first check with `PREFER_ON_DEVICE`, independent check with `ONLY_IN_CLOUD`,
same prompts and headline rules. No context given, so crop is identified from the photo alone.
This measures crop identification, model agreement, retakes and speed. It does **not** measure
disease accuracy: the photos have no expert labels yet.

Re-run it yourself: https://crop-doctor-d6c91.web.app/?eval (Chrome with Gemini Nano).

## Summary

| Measure | Result |
|---|---|
| Photos checked | 21 of 21 |
| First check ran on device (Gemini Nano) | 21 of 21 |
| Crop identified correctly (named photos) | 2 of 16 (said unknown: 5, wrong: 9) |
| Nano vs cloud agreement | 8 of 20 match, 12 differ in amount, 0 disagree |
| Asked for a retake | 1 of 21 |
| Results | RETAKE 1, MEDIUM 11, HIGH 4, LOW 5 |
| Median time, first check | 3.6 s |
| Median time, cloud check | 3.8 s |

## What this shows

- **On-device works.** All 21 first checks ran on Gemini Nano in Chrome. Median time was 3.6 s.
  The very first photo took 25.1 s while the model loaded; after that every photo took 3.4 to 3.9 s.
- **The two checks agree on the basics.** They never disagreed on whether the photo shows a plant.
  The amount of damage matched exactly on 8 of 20 photos and was within one level on 17 of 20.
- **Nano is the more cautious one.** When the amounts differed, Nano rated the damage worse than the
  cloud in 11 of 12 cases. That is the safer direction before a spray decision, and the app shows the
  more careful result, but it also means results lean toward caution.
- **Crop guessing from the photo alone is poor.** Nano named the right crop for only 2 of 16 photos
  (both maize). It called all three chili photos "tomato" and the cotton photos "vine" and "grape".
  The crop should come from the user's tap, not from the model.
- **No photo came out "healthy".** Every usable photo got low, medium or high risk, including
  `mango-02`, which the app uses as its "Healthy" sample. Field leaves often do have some marks, but
  without expert labels we can't tell real marks from over-caution. Expert labels are the next step.
- One retake (`chikoo-01`): Nano judged that photo too poor to check.

## Every photo

| photo | expected | cropGuess | crop | firstRanOn | firstSec | firstExtent | cloudExtent | cloudSec | agreement | result | seen | error |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| chikoo-01 | chikoo | unknown | said unknown | device (Nano) | 25.1 | small | skipped |  |  | RETAKE | holes and wavy edges on leaves |  |
| chikoo-02 | chikoo | olive | wrong | device (Nano) | 3.5 | moderate | small | 3.9 | small_diff | MEDIUM | irregular gray patches, cut-out spots on leaf |  |
| chili-01 | chili | tomato | wrong | device (Nano) | 3.7 | moderate | moderate | 3.5 | match | MEDIUM | Spots on leaves, some leaves curled; some leaves damaged. |  |
| chili-02 | chili | tomato | wrong | device (Nano) | 3.6 | large | moderate | 5.1 | small_diff | HIGH | Yellow spots, leaf discoloration, some small brown spots. |  |
| chili-03 | chili | tomato | wrong | device (Nano) | 3.6 | moderate | small | 3.8 | small_diff | MEDIUM | leaf spots, some leaf edges curled |  |
| cotton-01 | cotton | vine | wrong | device (Nano) | 3.6 | moderate | moderate | 12.1 | match | HIGH | Yellow patches, mottled patterns, some damaged areas |  |
| cotton-02 | cotton | grape | wrong | device (Nano) | 3.6 | moderate | moderate | 4.2 | match | MEDIUM | Spots and discoloration on leaf veins, some leaf edge damage |  |
| fenugreek-01 | fenugreek | Legume | wrong | device (Nano) | 3.9 | moderate | none | 2.9 | small_diff | MEDIUM | Yellowing on new leaves, some spots, some are stunted |  |
| maize-01 | maize | corn | correct | device (Nano) | 3.5 | moderate | small | 3.2 | small_diff | MEDIUM | Yellow patches on leaves. Small spots on some leaves. |  |
| maize-02 | maize | corn | correct | device (Nano) | 3.4 | small | small | 3.2 | match | LOW | Small yellow spots on leaves, some look pale |  |
| mango-01 | mango | unknown | said unknown | device (Nano) | 3.7 | moderate | none | 3.2 | small_diff | MEDIUM | Leaves turning reddish underneath, some spots visible. |  |
| mango-02 | mango | unknown | said unknown | device (Nano) | 3.7 | small | moderate | 18.5 | small_diff | HIGH | brown areas, some yellowing, small spots visible |  |
| papaya-01 | papaya | unknown | said unknown | device (Nano) | 3.6 | moderate | small | 3.7 | small_diff | MEDIUM | Leaf edges look nibbled, holes visible on leaves. |  |
| papaya-02 | papaya | pumpkin | wrong | device (Nano) | 3.5 | moderate | small | 5.5 | small_diff | MEDIUM | Small holes and yellow areas on leaves |  |
| unknown-01 | unknown | unknown | n/a | device (Nano) | 3.7 | small | none | 3.8 | small_diff | LOW | Small yellow spots on leaves visible. |  |
| unknown-02 | unknown | leafy plant | n/a | device (Nano) | 3.6 | moderate | moderate | 4.1 | match | MEDIUM | Torn edges, brown spots, uneven areas. |  |
| unknown-03 | unknown | tree | n/a | device (Nano) | 3.7 | large | small | 3.5 | small_diff | HIGH | Holes in leaves, leaves damaged. |  |
| unknown-04 | unknown | unknown | n/a | device (Nano) | 3.6 | small | small | 3.1 | match | LOW | holes in leaves, some spots visible |  |
| unknown-05 | unknown | soybean | n/a | device (Nano) | 3.6 | moderate | small | 4.2 | small_diff | MEDIUM | Small brown spots and mottled leaves are visible. |  |
| wheat-01 | wheat | grass | wrong | device (Nano) | 3.7 | small | small | 3.7 | match | LOW | Yellowish patches on leaves, some leaves appear thin |  |
| wheat-02 | wheat | unknown | said unknown | device (Nano) | 3.6 | small | small | 4.4 | match | LOW | Yellowing patches on leaves, small holes visible. |  |
