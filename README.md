## MyDayPlanner

A personal Android day planner / todo app built with Kotlin + Jetpack Compose.

The app is intentionally opinionated for a specific workflow (project-focused planning, daily carry-over tasks, and lightweight time tracking), but the codebase is small and easy to tweak.

## What it currently does

- Daily todo list persisted as plain JSON files in app-internal storage.
- Automatic carry-over of unfinished tasks from the most recent previous day.
- Per-task metadata: importance, project, effort estimate, and optional difficulty.
- Optional "push to tomorrow" flag.
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

The repository stores files under the app's internal files directory in a `days/` folder:

- `YYYY-MM-DD.json` → todo list for that day
- `YYYY-MM-DD.track.json` → day tracking/timer state

## Notes for contributors

- This repo is used as an AI-coding playground, so you may find pragmatic or experimental patterns.
- Keep changes small and explicit where possible.
- Prefer preserving existing JSON compatibility when touching models.
