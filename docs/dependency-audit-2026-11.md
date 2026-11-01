# Dependency review — 2026-11-01

This is a targeted **inventory**, not a verified Gradle dependency-upgrade run.
The installed Android SDK and Gradle build were not available in this environment.
The DWD CAP warning change intentionally does **not** upgrade the UI/toolchain stack.

| Dependency | In project | Recommendation |
| --- | --- | --- |
| Android Gradle Plugin | 9.4.0 | Leave as-is; already recent. |
| Gradle wrapper | 9.6.0 | Leave as-is; validate AGP compatibility when changing either. |
| Kotlin + serialization plugin | 2.2.10 | Upgrade together, separately from weather work. |
| Compose BOM | 2024.09.00 | Highest-priority UI-stack upgrade candidate; very old. Test all Material 3 screens. |
| Activity Compose | 1.8.0 | Candidate 1.12.4+ (released February 2026); test lifecycle and drawer navigation. |
| lifecycle-runtime-ktx | 2.6.1 | Candidate 2.10.0+; coordinate with viewmodel-compose, currently 2.9.2. |
| Core KTX | 1.10.1 | Candidate 1.17.x; 1.18.0 is available but expects compileSdk 36.1 (project: 36). |
| WorkManager | 2.11.0 | Leave for now; verify worker behaviour before changing. |
| DocumentFile | 1.1.0 | Low urgency. |
| SnakeYAML | 2.7 | Check current release and security advisories before upgrade; used for config parsing. |
| Garmin Connect IQ companion SDK | 2.4.0 | Verify compatibility with Garmin; do not blindly upgrade. |
| kxml2 (tests) | 2.3.0 | Only used for JVM XML parser tests. |

**Suggested follow-up PR:** Upgrade Compose BOM, Activity, lifecycle and Core together in a
separate change; then run `./gradlew testDebugUnitTest assembleDebug` and test the
home screen, drawer, routine tracking, and Garmin sync on device.

Source release notes:
- https://developer.android.com/jetpack/androidx/releases/activity
- https://developer.android.com/jetpack/androidx/releases/lifecycle
- https://developer.android.com/jetpack/androidx/releases/core
- https://developer.android.com/jetpack/androidx/releases/compose
