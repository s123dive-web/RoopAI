# CLAUDE.md - RoopAI

Full face-styling Android app (FaceApp / FaceTune / YouCam class). Package `com.suresh.roopai`,
single `:app` module, Kotlin, Compose, Material 3, single activity, MVVM, Hilt, Room, DataStore,
Coil, CameraX, MediaPipe Tasks, OkHttp.

## Build commands

```
export ANDROID_HOME=/path/to/sdk          # needs platforms;android-37.2 and build-tools 36+
./gradlew lint testDebugUnitTest assembleDebug     # exactly what CI runs
./gradlew testDebugUnitTest --tests '*LocalPipelineTest*'
```

JDK 17+ (CI: Temurin 17). APK: `app/build/outputs/apk/debug/app-debug.apk`.
CI: `.github/workflows/android.yml` - on push to `main`: lint, unit tests, assemble, upload
artifact, create release `v1.0.<run_number>` with `RoopAI-v1.0.<run_number>.apk`.

Developer aids (off by default):
* `-Droopai.visual.image=.. -Droopai.visual.json=.. -Droopai.visual.out=..` runs
  `VisualHarnessTest`: renders every offline tool on a real photo to JPEGs.
* `-Droopai.visual.out=<dir> -Drobolectric.pixelCopyRenderMode=hardware` makes `AppSmokeTest`
  save screenshots (this is how `docs/screenshots` was produced).

## Architecture: hybrid engine

```
ui (Compose screens + ViewModels)
  └─ EditorGateway (ui/editor)            one seam between the editor ViewModel and everything below
       ├─ LocalEngine  : FaceEditEngine    offline; MediaPipe analysis + pure-Kotlin LocalPipeline
       ├─ CloudEngine  : FaceEditEngine    optional; ImageEditProvider (Gemini) + retry + consent
       ├─ ProjectStore / HistoryRepository files + Room
       └─ MediaSaver                       MediaStore (Pictures/RoopAI) + share cache
```

**Edit model** (`edit/`): a project = untouched original file + `EditStack` (ordered `EditOp`s +
chosen face index), serialized as JSON in Room. `EditHistory` gives undo / redo over immutable
snapshots and coalesces slider drags into one step. Three op kinds:
* `Adjust(tool, amount, color?, variant?)` - any offline tool.
* `Geometry(quarterTurns, flips, straighten, crop)` - one per segment.
* `CloudEdit(presetId, prompt, resultFile)` - a generative edit; its result image is cached in the
  project folder, so re-rendering never calls the network again.

**Rendering**: the stack is split at cloud edits. Only the last segment needs rendering: base =
last cloud result (or the original), then that segment's local ops through `LocalPipeline` in a
fixed order: skin -> makeup -> eyes/teeth -> hair -> accessories -> background -> reshape (mesh
warp) -> colour -> geometry. Preview renders at 1080 px long side; export renders from the
full-resolution file within a memory budget.

**All image maths is pure Kotlin on `IntArray`** (`core/imaging`) and face-local (ROI masks), so it
is unit-testable on the JVM and resolution independent (every radius is a fraction of face width).

## Module map (`app/src/main/java/com/suresh/roopai`)

| Path | What |
|---|---|
| `core/imaging/` | `PixelBuffer`, `Colors`, `Blur`, `RoiMask`/`LowResMask`, `WarpField`, `Retouch`, `ColorFilters`, `Geometry`. No Android imports. |
| `edit/` | `Tool` + `Category` catalogue, `EditOp`, `EditStack`, `EditHistory`, `FaceEditEngine`, `EditException`. |
| `engine/local/` | `FaceMesh` (landmark indices), `FaceGeom`, `LocalPipeline`, `Accessories`, `Backdrops` (pure Kotlin); `FaceAnalyzer` (MediaPipe), `LocalEngine` (Android). |
| `engine/cloud/` | `ImageEditProvider`, `ProviderRegistry`, `GeminiProvider`, `CloudPresets`, `PromptBuilder`, `CloudEngine`, `RetryPolicy`. |
| `data/` | `image/BitmapIO` (EXIF, downsampled decode, atomic writes), `ProjectStore`, `HistoryRepository`, `MediaSaver`, `db/` (Room v1, schema in `app/schemas`), `settings/` (`SettingsRepository` on DataStore, `KeystoreSecretStore`). |
| `di/AppModule.kt` | Hilt bindings. The single `OkHttpClient` is only handed to `GeminiProvider`. |
| `ui/` | `home` (Home + `LibraryViewModel`), `camera`, `editor` (screen, ViewModel, gateway, canvas, colour picker), `history`, `settings`, `theme`, `icons`, `common`. |
| `assets/` | `face_landmarker.task`, `selfie_multiclass_256x256.tflite` (bundled, never downloaded). |

## Conventions

* No analytics, ads, trackers. Network only inside `engine/cloud`, only after key + consent.
* Never log or persist the API key outside `SecretStore`; never put it in a URL (header only).
* New offline tool: add a `Tool` enum entry (names are persisted - never rename), implement it in
  `LocalPipeline` (or `Accessories`), add assertions to `LocalPipelineTest`. The UI picks it up.
* New cloud preset: add to `CloudPresets.ALL`; `PromptBuilderTest` enforces the identity lock.
* New provider: implement `ImageEditProvider`, add it to `ProviderRegistry` in `AppModule`.
* Expected failures are `EditException` subclasses with user-readable messages.
* File writes go through `AtomicFiles`. Bitmaps created for export / upload are recycled by their
  creator; engines never recycle their input.
* Room schema changes: bump `RoopDatabase.VERSION`, add a `Migration` to `MIGRATIONS`, commit the
  new JSON under `app/schemas`. No destructive fallback.
* Tests first for image maths. Pure logic -> plain JUnit; Android graphics / UI -> Robolectric
  (`sdk=35`, native graphics). ViewModels are tested against fakes of `EditorGateway` etc.

## Status

All phases of the brief are implemented: research, scaffold + CI, image pipeline + edit stack +
tests, LocalEngine (all categories), editor UI, CloudEngine (Gemini), settings, consent, history,
error states, README. 186 unit and Robolectric tests. See DECISIONS.md for limitations.

Not verifiable in CI (no emulator available there): real-device behaviour of MediaPipe native
inference, CameraX capture, Keystore and the system photo picker. The code paths compile, the
models were verified with the same MediaPipe version on desktop, and everything around them is
covered by Robolectric tests.
