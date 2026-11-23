# DECISIONS

Decisions taken autonomously (the brief said not to ask). Newest at the bottom.

1. **Toolchain**: AGP 9.5.2 with built-in Kotlin, Gradle 9.6.1, compileSdk 37.2 (Compose 1.13
   requires >= 37.1), targetSdk 37, minSdk 26, JDK 17 bytecode. All versions were read from the
   live Maven metadata (see RESEARCH.md), not from memory.
2. **Single Gradle module** (`:app`) with packages as layers. A multi-module split adds build
   time and no value for a one-developer app; packages keep the same boundaries.
3. **MediaPipe Tasks over ML Kit**: ML Kit face-mesh and selfie-segmentation are still beta.
   MediaPipe Face Landmarker (478 points) + the multiclass selfie segmenter give landmarks, hair,
   skin and background from two bundled models.
4. **Models are committed to the repo** under `app/src/main/assets` (about 20 MB) instead of being
   downloaded in CI, so the build is reproducible and the APK never downloads at runtime.
5. **All image maths is pure Kotlin on `IntArray`** (`core/imaging`). It is slower than RenderScript
   / GPU shaders but is fully unit-testable on the JVM, has no deprecated dependencies and is fast
   enough because face edits only touch the face rectangle.
6. **Debug-signed APK in the Release**. A release keystore would be a secret; the brief forbids
   secrets in the repo and only allows the built-in `GITHUB_TOKEN`. The debug APK installs fine
   (enable "install unknown apps"). Because CI's debug key differs per runner, upgrading across
   releases may require uninstalling first.
7. **Version name** = `1.0.<GITHUB_RUN_NUMBER>` so the APK, tag and in-app version always match.
