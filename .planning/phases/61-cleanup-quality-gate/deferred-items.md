# Phase 61 Deferred Items

Items discovered during plan 61-02 execution that are out of scope and tracked here for follow-up.

## Pre-existing Unused Fields

- `PlayoffService.playoffSeedRepository` (src/main/java/org/ctc/domain/service/PlayoffService.java:36) — declared but never referenced. Predates plan 61-02. Not removed because removal is unrelated to MIGR-06 cleanup; can be cleaned in a separate refactor commit if desired.
