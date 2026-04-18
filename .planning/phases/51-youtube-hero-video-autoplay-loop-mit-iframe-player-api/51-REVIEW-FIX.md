---
phase: 51-youtube-hero-video-autoplay-loop-mit-iframe-player-api
fixed_at: 2026-04-17T20:01:22Z
review_path: .planning/phases/51-youtube-hero-video-autoplay-loop-mit-iframe-player-api/51-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 51: Code Review Fix Report

**Fixed at:** 2026-04-17T20:01:22Z
**Source review:** .planning/phases/51-youtube-hero-video-autoplay-loop-mit-iframe-player-api/51-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3
- Fixed: 3
- Skipped: 0

## Fixed Issues

### WR-01: videoId Thymeleaf inline-JS injection lacks server-side sanitisation

**Files modified:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`
**Commit:** 4fbefee
**Applied fix:** Added a regex safety check after scraping the videoId in `generateIndex()`. If the scraped value does not match `[a-zA-Z0-9_-]{1,20}`, it logs a warning and falls back to the configured `siteProperties.getYoutubeVideoId()`. This prevents any malformed scrape result from being injected into the Thymeleaf inline JavaScript context.

### WR-02: onStateChange loop fallback needs ready-flag guard and try-catch

**Files modified:** `src/main/resources/templates/site/index.html`
**Commit:** bba7959
**Applied fix:** Added a `playerReady` flag variable (set to `true` in `onReady` callback) and an early-return guard in `onStateChange` that skips processing if the player is not yet ready. Wrapped the `seekTo(0)` + `playVideo()` fallback in a try-catch to prevent JS exceptions from crashing the script block when the player is garbage-collected on mobile browsers.

### WR-03: Click-blocker overlay missing aria-hidden="true"

**Files modified:** `src/main/resources/templates/site/index.html`
**Commit:** b91e483
**Applied fix:** Added `aria-hidden="true"` attribute to the decorative `.landing-hero-overlay` div so screen readers do not announce the empty overlay element.

---

_Fixed: 2026-04-17T20:01:22Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
