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

Choose the Obsidian special folder from the banner in the app. Android's system folder picker grants persistent access without requiring a fragile hard-coded path. The app reads `todo_config.md` from the selected folder and stores state under `mydayplanner/YYYY/MM/` (full dates remain in filenames):

- `YYYY-MM-DD.json` → todo list for that day
- `YYYY-MM-DD.track.json` → day tracking/timer state
- `YYYY-MM-DD.routines.json` → routine progress

For example, todos for October 23 live at `mydayplanner/2026/10/2026-10-23.json`, next to that day's `.track.json` and `.routines.json` files. The app creates missing year/month folders when writing new days.

Folder selection does not migrate, rename, or copy existing files. Move existing flat daily files into their year/month folders on the PC while the app is stopped, then let sync finish before starting the updated app. Flat files and backup directories are not included in the new index. The persisted folder permission remains unchanged. Once configured, shared storage is canonical; an unavailable shared folder never falls back to local storage.

The app caches directory URIs and builds a logical-name → URI/metadata index with one children query per directory (state root, each year, each month). Warm reads open the indexed URI directly; writes update that index without rescanning. Creating a previously unknown year/month checks only its parent and caches the new directory. When multiple provider names normalize to the same logical name, the newest modification timestamp wins (the canonical name breaks ties). The explicit **Refresh** action reloads both the index and today's state after external sync or edits. Failed stream operations invalidate the cached directory/index and retry once.

Todo estimates have one runtime field, `estimateMinutes`, which is also the only estimate field written to JSON. Old records containing only `timePredicted` remain readable; if both fields are present, `estimateMinutes` wins, regardless of JSON field order. Records without either field retain the 15-minute default. Existing day files are not rewritten in bulk: the legacy field is dropped only when those todos are normally saved.

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

The Android app uses Garmin's Connect IQ Mobile SDK and watch-face UUID
`36629bca-a6fa-4be8-a2b2-4a8a870c7415`. It sends the snapshot map directly after
planner-state changes, app resume, and device reconnect. The watch face must have
matching Monkey C app-message handling and must be installed through Garmin Connect
Mobile on the paired device.

Keeping snapshot construction independent of the proprietary transport makes the
payload testable now and leaves DWD weather as a drop-in replacement for
`ReducedWeather.Fake` later.

The Garmin screen shows one combined session history for automatic and manual sync
attempts, newest first. Each attempt includes its trigger, captured payload, timed
device/status/app lookup steps, and send callbacks or failures. SDK errors and
blocked attempts also appear here; no separate manual-send panel is used. The
history retains the latest 100 attempts, with up to 200 log entries per attempt,
and resets when the app process restarts.
