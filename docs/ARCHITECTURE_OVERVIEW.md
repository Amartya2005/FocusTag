# Architecture Overview

FocusTag is organized as an Android application backed by Supabase services.

The app separates UI, data models, repositories, services, and workers. Focus-related state and enforcement logic are represented in dedicated domain/data components, while Supabase migrations provide the versioned database contract.

This structure keeps device behavior, application state, and backend policy changes independently reviewable.
