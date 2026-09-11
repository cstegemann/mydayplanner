## MyDayPlanner

A personal Android day planner / todo app built with Kotlin + Jetpack Compose.

The app is intentionally opinionated for a specific workflow (project-focused planning, daily carry-over tasks, and lightweight time tracking), but the codebase is small and easy to tweak.

## What it currently does

- Daily todo list persisted as plain JSON files in a user-selected shared folder.
- Automatic carry-over of unfinished tasks from the most recent previous day.
- Per-task metadata: importance, project, effort estimate, and optional difficulty.
- Deferred tasks that can be pushed back 1, 2, 3, 5, or 7 days.
- Live-track choices loaded from an Obsidian markdown file.
- Project timer/tracking with per-day totals.
- Basic history view for recent days.

## Tech stack

- Kotlin
- Jetpack Compose (Material 3)
- Kotlin coroutines + StateFlow
- kotlinx.serialization
- Gradle (Android application module in `app/`)

## Local development

### Requirements

- JDK 17+
- Android SDK installed
- `local.properties` configured with a valid `sdk.dir`

Example `local.properties`:

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
```

On Linux, it is commonly something like:

```properties
sdk.dir=/home/<you>/Android/Sdk
```

### Build / test

```bash
./gradlew assembleDebug
./gradlew test
```

If Gradle fails very early with a message like `What went wrong: 25.0.2`, that typically indicates missing or misconfigured Android SDK / build-tools on the machine.

## Data layout (runtime)

Choose the Obsidian special folder from the banner in the app. Android's system folder picker grants persistent access without requiring a fragile hard-coded path. The app reads `_live-tracks.md` from the selected folder and stores state under its `mydayplanner/` child folder:

- `YYYY-MM-DD.json` → todo list for that day
- `YYYY-MM-DD.track.json` → day tracking/timer state

When a folder is selected for the first time, local JSON files are copied only if the corresponding shared file does not already exist. The shared copy wins, avoiding destructive migration. Once configured, shared storage is canonical; if it is temporarily unavailable, the app displays a warning instead of silently creating a divergent local copy.

Some Android document providers add suffixes such as ` (1)` or append a second `.json` extension. The app recognizes those files as their original logical day file and reuses them instead of creating further duplicates; it does not automatically delete existing files, to avoid data loss.

## Notes for contributors

- This repo is used as an AI-coding playground, so you may find pragmatic or experimental patterns.
- Keep changes small and explicit where possible.
- Prefer preserving existing JSON compatibility when touching models.

## Garmin watch snapshot

`buildWatchSnapshot` now reduces the planner state and placeholder weather into the
small, integer-only payload expected by the watch face. It includes learning,
physical, and time-weighted task progress; remaining scheduled minutes; capped
warning/replan counts; fixed test weather; and a Unix timestamp. Deferred and meta
tasks are not considered scheduled work. Learning and physical routine progress use
the `learning` and `physical` area IDs from the planner configuration.

The Connect IQ transport is intentionally not wired in yet. Completing it requires:

1. The watch face's Connect IQ application UUID (this is not the Android package ID).
2. Garmin's Android Connect IQ Mobile SDK artifact/AAR added to the local build.
3. A transport initialized with that UUID which sends the serialized snapshot with
   `sendMessage` after planner-state changes, app resume, and reconnect.
4. Matching Monkey C handling in the watch face, including acknowledgement/error
   handling and testing through Garmin Connect Mobile on the paired FR55.

Keeping snapshot construction independent of the proprietary transport makes the
payload testable now and leaves DWD weather as a drop-in replacement for
`ReducedWeather.Fake` later.
