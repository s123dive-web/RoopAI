# CLAUDE.md - RoopAI

Full face-styling Android app. Hybrid engine: offline `LocalEngine` + optional bring-your-own-key
`CloudEngine`.

## Build commands
```
export ANDROID_HOME=/path/to/sdk      # needs platforms;android-37.2, build-tools 36+
./gradlew lint testDebugUnitTest assembleDebug   # exactly what CI runs
./gradlew testDebugUnitTest --tests '*WarpFieldTest*'
```
JDK 17+ required. APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Status
Scaffold + CI. (Updated as phases land - see bottom of file.)
