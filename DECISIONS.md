# DECISIONS

Decisions taken autonomously (the brief said not to ask). Limitations are listed at the end.

## Toolchain and structure
1. **Versions**: AGP 9.5.2 with built-in Kotlin, Gradle 9.6.1, compileSdk 37.2 (Compose 1.13 needs
   >= 37.1), targetSdk 37, minSdk 26, JDK 17 bytecode. Read from live Maven metadata (RESEARCH.md).
2. **Single Gradle module** with packages as layers. Multi-module adds build time and no value for a
   one-developer app.
3. **CI action**: `android-actions/setup-android@v3` with an explicit package list, because its
   default list still contains the removed `tools` package and fails. `gradle/actions/setup-gradle`
   provides the Gradle cache. The release is created with the preinstalled `gh` CLI and the built-in
   `GITHUB_TOKEN`.
4. **Debug-signed APK in the Release.** A release keystore would be a secret, which the brief
   forbids. Consequence: an update may need an uninstall first, because CI's debug key changes.
5. **No R8 / minification.** Shrinking MediaPipe (JNI + protobuf) without a device to verify on is
   an avoidable risk. Instead the APK was reduced from 119 MB to about 65 MB by shipping only the
   Material icons in use, dropping 32-bit x86 and compressing native libraries.
6. **Version name** `1.0.<GITHUB_RUN_NUMBER>` so APK, tag and in-app version always match.

## Local engine
7. **MediaPipe Tasks, not ML Kit**: ML Kit face-mesh / selfie-segmentation are still beta. Two
   bundled models give 478 landmarks, hair, skin and background. Models are committed to the repo
   (about 20 MB) so the build is reproducible and nothing is downloaded at runtime.
8. **Pure-Kotlin image maths on `IntArray`** instead of GPU shaders / RenderScript: slower, but
   fully unit-testable, no deprecated APIs, and fast enough because face edits only touch the face
   rectangle (a full preview render is tens of milliseconds on a desktop JVM).
9. **Fixed pipeline order inside a run of local ops** (skin, makeup, eyes, hair, accessories,
   background, reshape, colour, geometry). The stack keeps the user's order for undo and display,
   but rendering is order-independent so results never depend on slider history. Reshape runs after
   makeup and stickers so they move with the face.
10. **Accessories are drawn procedurally** (polygons, ellipses, strokes) rather than from bitmap
    stickers: any colour, any resolution, follows head roll, and no artwork licences.
11. **Categories**: the ten from the brief plus **Background** and **Adjust** (filters, crop,
    rotate), which the brief lists as local features but not as bottom-bar categories. Nose width
    lives under Face, teeth under Lips, lipstick under both Lips and Makeup. Age and Expression
    presets appear under Face and in the AI Edit grid.
12. **Tapping a colour / style tool applies it at a default strength** (lipstick, glasses, filters)
    so the effect is visible at once; plain sliders start at 0. Each tool has a remove button.
13. **Preview at 1080 px, export at original resolution within a memory budget** (a third of the
    Java heap at about 12 bytes per pixel, capped at 24 MP, with one retry at half size on OOM).
    A 50 MP photo is therefore exported at the largest size the device can process, not always 50 MP.
14. **EXIF**: orientation is applied on decode and the exported file is upright with no orientation
    tag. Other EXIF fields (GPS, camera) are intentionally not copied to the output.

## Cloud engine
15. **Gemini via stateless `generateContent`** rather than the newer Interactions API, so no
    server-side history of the user's photo is created. Default model `gemini-3.1-flash-lite-image`
    (free tier); four listed models plus a free-form model ID so new models need no app update.
16. **One identity lock for every prompt** (`PromptBuilder`): keep identity, face shape, skin tone,
    lighting, background; change only the requested attribute. Presets that must alter more (looks,
    passport photo, age) get an explicit, narrow exception clause instead of a different template.
17. **A cloud edit is materialised**: its result image is stored in the project and becomes the base
    for later local edits. Local edits made before it are baked into the upload.
18. **Upload size follows output quality** (1024 / 1536 / 2048 px long side, JPEG 92).
19. **Retry**: up to 3 attempts with 1.5 s exponential backoff for 5xx and 429; never for invalid
    key, blocked content, model not found, no network or timeouts. Timeouts: 20 s connect, 120 s
    read, 180 s per call. Cancelling the progress overlay cancels the HTTP call.
20. **Consent** is asked once before the first upload, stored in DataStore, and can be withdrawn in
    Settings. Declining sends nothing.
21. **API key storage**: AES-256-GCM with an Android Keystore key, ciphertext in a private
    preferences file; backups are disabled for the app. The Settings UI only ever shows the last
    four characters.

## App behaviour
22. **Photos are copied into app storage on import** so an edit can be reopened after the gallery
    permission grant expires. History rows hold the JSON stack; deleting a row deletes the copy.
23. **Share / Open-with target**: RoopAI accepts images from other apps (content:// URIs only).
24. **Editor is always dark** so colours are judged on a neutral backdrop; the rest of the app
    follows the theme setting.
25. **Robolectric smoke tests** launch the real app because no emulator can run in CI or in the
    development sandbox. They found one real bug (a snackbar cancelled by its own event handling).

## Known limitations
* Not run on a physical device during development (no emulator available). MediaPipe inference,
  CameraX, Keystore and the photo picker are standard API usage but untested at runtime.
* Offline reshape and makeup are 2D: extreme head turns or occluded faces reduce accuracy.
  Earrings are hidden on the far side when the head is turned; the cap is a flat sticker.
* Hair tint cannot realistically lighten very dark hair (it recolours while keeping shading);
  use the Cloud hair-colour presets for that.
* Background blur / replace use a 256 px segmentation mask, so fine hair edges are soft.
* After an AI edit the image has the resolution the model returns (typically about 1 to 2 MP),
  not the original resolution.
* AI results depend on the provider and model: identity preservation is requested in every prompt
  but cannot be guaranteed, and some edits may be declined by the provider's safety filters.
* Only one provider (Gemini) is implemented; the abstraction for more is in place.
* Export above the memory budget is downscaled (see 13). No RAW / HEIF output; JPEG or PNG only.
* The crop frame is edited on the un-zoomed image; face-picker boxes are hidden once the photo is
  rotated or cropped (the "Face 1 / 2" chips still work).
* English UI only.
