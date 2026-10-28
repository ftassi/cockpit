# Cockpit

Personal car launcher for a Google Pixel 7a, sideloaded via adb (no Play Store).
The phone sits portrait on a magnetic mount, audio goes to the car radio through a
USB-C splitter (charging + DAC/AUX). See `PLAN.md` for the current plan and step status.

## Stack

- Kotlin + Jetpack Compose + Material 3, single activity, single Gradle module `app`
- Media3 (ExoPlayer + MediaSessionService) for radio playback
- `kotlinx.serialization` for the JSON config
- Package `dev.ftassi.cockpit`, `minSdk 35` (needs `AutomaticZenRule` driving mode)
- No DI framework, no analytics, no ads; network only for radio streams
  (and radio-browser.info in phase 2)

## Commands

Every dependency install and repetitive operation goes through the `Makefile`.
Add a target rather than documenting a raw command sequence. Run `make help`
for the list.

## Conventions

- Prefer clarity over enterprise patterns: no interfaces or layers without a
  second caller.
- Model UI blocks as a sealed interface (`Block`) and dispatch with `when`.
- Config lives in `app/src/main/assets/config.json` for the MVP; the loader is the
  only place that knows where it comes from (phase 2 moves it to `filesDir`).
- All Spotify integration goes through `launch/Launchers.kt`, so the phase 2
  switch to the App Remote SDK touches one place.
- Car mode state that must survive process death (saved volume, active flag,
  last station) goes in SharedPreferences.
- Use current official APIs (Android Developers, Media3 docs); call out
  behaviour that depends on the Android version.
- Unit-test pure logic only (config parsing, reconnection backoff, active
  session selection); everything else is verified on the device per `PLAN.md`.

## Communication

The owner is fluent in Rust, new to Kotlin/Android: when introducing a Kotlin
or Android concept, map it to its Rust equivalent (sealed/when ↔ enum/match,
nullable ↔ Option, coroutines/Flow ↔ async/Stream, StateFlow ↔ watch channel).
