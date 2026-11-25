# Aegis Browser

A minimal, dark-first, zero-telemetry Android browser built with Kotlin, Jetpack Compose, real client-side End-to-End Encryption (E2EE), and request-level ad/tracker interception.

## Local Build & GitHub Actions APK Workflow

1. Run unit tests:
   `./gradlew testDebugUnitTest`
2. Build APKs:
   `./gradlew assembleDebug assembleRelease`
3. GitHub Actions CI runs tests and uploads `aegis-browser-debug-apk` and `aegis-browser-release-apk` artifacts.
