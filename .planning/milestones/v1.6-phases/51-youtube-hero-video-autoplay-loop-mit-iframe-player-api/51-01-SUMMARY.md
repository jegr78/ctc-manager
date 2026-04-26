---
phase: 51-youtube-hero-video
plan: 01
subsystem: site-generator
tags: [youtube, iframe-api, autoplay, loop, css, template]
dependency_graph:
  requires: []
  provides: [YT-01, YT-02, YT-03]
  affects: [src/main/resources/templates/site/index.html, src/main/resources/static/site/css/style.css, src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java]
tech_stack:
  added: []
  patterns: [YouTube iFrame Player API, Thymeleaf th:inline=javascript, CSS overlay z-index interception]
key_files:
  created: []
  modified:
    - src/main/resources/templates/site/index.html
    - src/main/resources/static/site/css/style.css
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
decisions:
  - "Used Thymeleaf th:inline=javascript with /*[[${videoId}]]*/ for safe server-side injection into inline script block"
  - "Dual-layer looping: playerVars loop+playlist as primary, onStateChange ENDED->seekTo(0)+playVideo() as safety net"
  - "CSS overlay (z-index:2) over iframe intercepts all mouse events, preventing YouTube controls from being visible/clickable"
metrics:
  duration: ~15 minutes
  completed: "2026-04-17"
  tasks_completed: 2
  files_modified: 3
---

# Phase 51 Plan 01: YouTube iFrame Player API Autoplay + Loop Summary

**One-liner:** Replaced static YouTube iframe embed with iFrame Player API for guaranteed muted autoplay and seamless looping, secured by CSS overlay for control interception.

## What Was Built

The simple `<iframe th:src="...">` embed was replaced with the YouTube iFrame Player API. The static HTML now contains a `<div id="yt-hero-player">` target element and an IIFE script block that:

1. Dynamically injects the `youtube.com/iframe_api` script into `<head>`
2. On `onYouTubeIframeAPIReady`, creates a `YT.Player` with `autoplay:1, mute:1, controls:0, loop:1, playlist:videoId`
3. On `onReady`, calls `event.target.playVideo()` as a fallback trigger
4. On `onStateChange` with `YT.PlayerState.ENDED`, calls `seekTo(0)` + `playVideo()` for seamless looping

A `.hero-video-overlay` div (`position:absolute, z-index:2`) was added both in the template and CSS to sit on top of the dynamically-created iframe, intercepting all user mouse events so YouTube controls are never accessible.

## Commits

| Hash | Message |
|------|---------|
| 4c6819f | feat(51-01): replace iframe with YouTube iFrame Player API + CSS overlay |
| e95302a | test(51-01): update LAND-01 assertion for iFrame Player API markup |

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Replace iframe with YouTube iFrame Player API + CSS overlay | 4c6819f | index.html, style.css |
| 2 | Update LAND-01 test assertion for iFrame Player API markup | e95302a | SiteGeneratorServiceTest.java |

## Decisions Made

1. **Thymeleaf JS inlining:** Used `th:inline="javascript"` with `/*[[${videoId}]]*/` syntax — the safe Thymeleaf inline expression auto-escapes the videoId value server-side before it appears in the rendered HTML, addressing T-51-01.

2. **Dual-layer loop strategy:** `playerVars loop:1 + playlist:videoId` is the native YouTube loop mechanism (first defense). `onStateChange ENDED -> seekTo(0) + playVideo()` is the JavaScript safety net for the case where the native loop has a gap or doesn't fire correctly — exactly the documented reliability issue with the old embed approach.

3. **CSS overlay pattern:** `.hero-video-overlay` with `z-index:2` and `cursor:default` sits above the iframe to intercept pointer events, hiding controls (T-51-04). The iframe renders underneath with `z-index` unset (auto/0).

4. **LAND-01 test update:** The old test asserted `iframe[src*='youtube.com/embed/']` — inapplicable since no iframe exists in static HTML (API creates it dynamically via JS). Updated to assert `#yt-hero-player` div, `.hero-video-overlay` div, and `onYouTubeIframeAPIReady` in script blocks.

## Test Results

- Full test suite: **1009 tests, 0 failures, 0 errors** (BUILD SUCCESS)
- JaCoCo coverage: **All checks passed** (82%+ minimum maintained)
- `SiteGeneratorServiceTest`: 70 tests, 0 failures

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — videoId is wired from `YouTubeScraperService` via `SiteGeneratorService.generateIndex()`.

## Threat Flags

No new threat surface beyond what was documented in the plan's threat model.

## Self-Check: PASSED

- [x] src/main/resources/templates/site/index.html — contains `onYouTubeIframeAPIReady`, `yt-hero-player`, `hero-video-overlay`
- [x] src/main/resources/static/site/css/style.css — contains `.hero-video-overlay` with `z-index: 2`
- [x] src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java — contains `givenActiveSeason_whenGenerate_thenIndexHasYouTubePlayerApi`
- [x] Commit 4c6819f exists
- [x] Commit e95302a exists
- [x] 1009 tests pass, BUILD SUCCESS
