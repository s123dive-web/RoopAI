# RESEARCH

_Date: 2026-11-23._

## 1. Competitor feature comparison

Feature sets below are compiled from the public product descriptions of each app. They are used
only to decide scope; no assets, names or UI are copied.

| Capability | FaceApp | Remini | FaceTune | YouCam Makeup | Lensa | Snapchat lenses | **RoopAI** |
|---|---|---|---|---|---|---|---|
| Skin smoothing / blemish | yes | via enhance | yes (core) | yes | yes ("Magic retouch") | live beauty | **Local** |
| Tone evening, dark circles, wrinkles | partial | no | yes | yes | yes | no | **Local** |
| Face reshape (slim, jaw, chin, forehead) | yes | no | yes (reshape) | yes | partial | live distortion | **Local** (mesh warp) |
| Eyes (brighten, size), brows | partial | no | yes | yes | yes | live | **Local** |
| Nose width, lip fullness | yes | no | yes | yes | no | live | **Local** |
| Teeth whitening | yes | no | yes (core) | yes | yes | no | **Local** |
| Makeup (foundation, blush, liner, shadow, lipstick) | presets | no | yes | yes (core) | no | lens presets | **Local**, colour picker each |
| Hair colour | yes (AI) | no | yes | yes | yes | lens | **Local** tint + **Cloud** recolour |
| Hairstyle / length / volume / hairline / bald | yes (AI) | no | AI hair | AI hairstyle | no | lens | **Cloud** |
| Beard add / remove / moustache / stubble | yes (AI, core) | no | no | beard styles | no | lens | **Cloud** |
| Age younger / older | yes (core) | no | no | AI aging | no | lens | **Cloud** |
| Expression (smile) / pose | yes | no | partial | partial | no | no | **Cloud** |
| Accessories (glasses, jewellery, headwear) | glasses | no | no | yes (AR) | no | yes (core) | **Local** stickers + **Cloud** |
| Background blur / replace | yes | yes | yes | yes | yes (core) | yes | **Local** |
| Filters, crop, rotate | yes | basic | yes | yes | yes | filters | **Local** |
| One-tap looks | yes | AI photos | AI looks | yes | AI avatars | lenses | **Cloud** (+ identity lock prompt) |
| Free-text AI edit | no | partial | partial | no | no | no | **Cloud** |
| Works offline | no | no | partial | partial | partial | partial | **Yes for every Local tool** |
| Photos leave device | yes | yes | yes for AI | yes for AI | yes for AI | yes | **Only with BYO key + consent** |
| Watermark / ads / subscription | yes | yes | yes | yes | yes | ads | **None** |

Take-aways:
* Classic retouch (skin, reshape, eyes, teeth, makeup) is geometry + colour maths over face
  landmarks and does not need a server. That is the Local engine.
* Semantic edits (beard, hairstyle, age, expression, full looks) need a generative model.
  They go to the Cloud engine with a bring-your-own-key provider.
* All the commercial apps upload photos for AI features without a clear per-user consent gate.
  RoopAI adds an explicit one-time consent and works fully with no key and no network.

## 2. Final feature list

### Local engine (offline, intensity slider + live preview each)
* **Skin**: smoothing, blemish reduction, tone evening, dark-circle reduction, wrinkle softening
* **Face**: slimming, jaw, chin, forehead, nose width (mesh warp)
* **Eyes**: brightening, eye size, eyebrow thickness, eyebrow shape (arch), eyebrow colour
* **Lips**: lip fullness, teeth whitening, lipstick
* **Makeup**: foundation, blush, eyeliner, kajal, eyeshadow, lipstick (colour picker + intensity)
* **Hair**: colour tint
* **Accessories**: bindi, earrings, nose pin, glasses, cap (landmark anchored, style/colour/size)
* **Background**: blur, replace (colour, gradient, own photo)
* **Adjust**: 10 colour filters, brightness, contrast, saturation, warmth, crop, rotate, flip, straighten

### Cloud engine (optional, bring-your-own-key, badged in UI)
* **Facial hair**: remove beard, full beard, short boxed beard, goatee, moustache only, stubble
* **Hair**: hairstyle change (several), longer, shorter, colour, hairline, volume, grey coverage, bald preview
* **Age**: younger, older
* **Expression**: smile, neutral, slight head turn
* **Accessories**: add glasses, remove glasses, turban/pagdi, jewellery set
* **Looks**: wedding, office, party, festive, passport photo
* **AI Edit**: preset grid + free-text edit

## 3. Library versions (verified live, not from memory)

Verified on 2026-11-23 by reading `maven-metadata.xml` on `dl.google.com/dl/android/maven2`,
`repo.maven.apache.org`, `plugins.gradle.org`, the SDK repository manifest
`dl.google.com/android/repository/repository2-3.xml`, and the AGP release notes on
developer.android.com.

| Component | Version | Source |
|---|---|---|
| Android Gradle Plugin | 9.5.2 (built-in Kotlin) | Google Maven |
| Gradle | 9.6.1 (AGP 9.5 minimum is 9.6.0) | AGP 9.5 release notes, services.gradle.org |
| JDK | 17 (AGP 9.5 minimum) | AGP 9.5 release notes |
| compileSdk / targetSdk | 37 (max API supported by AGP 9.5) | SDK manifest, AGP notes |
| Kotlin (compose + serialization plugins) | 2.4.21 | Maven Central |
| KSP | 2.3.14 | Gradle plugin portal |
| Hilt | 2.60.1 | Maven Central |
| Compose BOM | 2026.11.01 | Google Maven |
| core-ktx / activity-compose | 1.19.1 / 1.13.0 | Google Maven |
| lifecycle / navigation-compose | 2.11.1 / 2.10.2 | Google Maven |
| Room / DataStore | 2.8.5 / 1.3.0 | Google Maven |
| CameraX | 1.7.0 | Google Maven |
| Coil / OkHttp | 3.6.3 / 5.5.1 | Maven Central |
| kotlinx coroutines / serialization | 1.11.0 / 1.13.0 | Maven Central |
| MediaPipe tasks-vision | 1.2.3 | Google Maven |

ML Kit face-mesh and selfie-segmentation are still beta artifacts (16.0.0-beta3 / beta6), so
MediaPipe Tasks was chosen for the local engine.

### On-device models (bundled in `app/src/main/assets`, no runtime download)
* `face_landmarker.task` (float16, 3.6 MB) - 478 face landmarks, multi-face.
* `selfie_multiclass_256x256.tflite` (float32, 16 MB) - classes: background, hair, body skin,
  face skin, clothes, others. One model covers hair tint, background blur/replace and skin masks.

Both come from `storage.googleapis.com/mediapipe-models` (official MediaPipe model hosting).

## 4. Image-editing model names (verified live)

`ai.google.dev/gemini-api/docs/*` now redirects to `aistudio.google.com/docs/*`, which was not
reachable from the build environment. Model IDs and request shape were instead verified from
Google's own repositories on GitHub on 2026-11-23:

* `google-gemini/cookbook` -> `quickstarts/Get_Started_Nano_Banana.ipynb` lists the image models:
  * `gemini-3.1-flash-lite-image` - Nano-Banana 2 Lite, fastest and cheapest, has a free tier
  * `gemini-3.1-flash-image` - Nano-Banana 2, the standard model
  * `gemini-nano-banana-2.1` - Nano-Banana 2.1, higher fidelity
  * `gemini-3-pro-image-preview` - Nano-Banana Pro, flagship (preview), up to 4K
* `googleapis/python-genai` -> `models.py` / `_api_client.py` confirm the REST call still used by
  the SDK: `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`
  with header `x-goog-api-key`, image parts as `inline_data {mime_type, data}`, and
  `generationConfig.responseModalities`. The same notebook reads the result from
  `candidates[0].content.parts[].inline_data`.

The newer Interactions API also supports these models; `generateContent` was chosen because it is
stateless (no server-side interaction history of the user's photo is needed).

Default model in the app: `gemini-3.1-flash-lite-image` (works on the free tier). The model is
user-selectable in Settings, including a free-form custom model ID so new models work without an
app update.
