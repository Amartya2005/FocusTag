# FocusTag

**Classroom IoT product (pilot):** a Kotlin/Jetpack Compose Android app that turns a physical or simulated NFC tap into a verified, tamper-resistant Focus Session — automatically blocking distracting apps until the tag is tapped again. Built for classroom / educational focus use cases with teacher monitoring support.

> **This repository (`Amartya2005/FocusTag`) is the pilot development repo.**  
> Upstream / original source of truth: [0xyusufz/FocusTag](https://github.com/0xyusufz/FocusTag).

## Stack

- Kotlin, Jetpack Compose, Material 3
- NFC + AccessibilityService enforcement
- Supabase (Auth + Postgres) with WorkManager sync
- Gradle (Kotlin DSL)

## Layout

- `app/` — Android application (domain engines, UI, repositories)
- `supabase/migrations/` — Phase 9 schema + RLS
- `gradle/` — wrapper and version catalog

## Note

Seeded from the local clone of `0xyusufz/FocusTag`. Some binary assets (launcher webps, `gradle-wrapper.jar`, review zips) may need restoring separately for a full local `./gradlew` build.
