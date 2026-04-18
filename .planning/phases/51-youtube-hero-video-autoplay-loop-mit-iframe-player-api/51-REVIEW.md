---
phase: 51-youtube-hero-video-autoplay-loop-mit-iframe-player-api
reviewed: 2026-04-17T00:00:00Z
depth: standard
files_reviewed: 3
files_reviewed_list:
  - src/main/resources/templates/site/index.html
  - src/main/resources/static/site/css/style.css
  - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
findings:
  critical: 0
  warning: 3
  info: 3
  total: 6
status: issues_found
---

# Phase 51: Code Review Report

**Reviewed:** 2026-04-17
**Depth:** standard
**Files Reviewed:** 3
**Status:** issues_found

## Summary

Phase 51 introduces YouTube IFrame Player API integration as the hero video on the landing page. The implementation uses the recommended API approach (no `<iframe>` in markup, dynamic injection via `onYouTubeIframeAPIReady`) with mute+autoplay+loop and a click-blocker overlay. The CSS `landing-hero` container already existed from Phase 48 and correctly applies the 16:9 aspect-ratio wrapper.

Three warnings were found: a potential XSS vector if `videoId` is ever populated from user-controlled input, a missing `aria-hidden` on the decorative overlay div, and a logic gap in the `onStateChange` loop fallback that can silently stall. Three info-level items cover minor maintainability concerns in the test file.

No critical security vulnerabilities exist in the current production data path — `videoId` is scraped server-side and injected via Thymeleaf inline JavaScript, which is safe as long as the scraped value matches the expected 11-character YouTube ID format.

---

## Warnings

### WR-01: `videoId` Thymeleaf inline-JS injection lacks server-side sanitisation

**File:** `src/main/resources/templates/site/index.html:18`

**Issue:** The variable `videoId` is emitted into a JavaScript string literal via `/*[[${videoId}]]*/`. Thymeleaf's inline JavaScript mode HTML-escapes the value when placed inside double-quoted JS strings, but that escaping is designed for HTML context, not JS string context. If `videoId` ever contains a single-quote or backslash (e.g. due to a malformed YouTube page response being passed through), it can break out of the surrounding JS string. The current scraper validates nothing about the extracted value beyond the regex `[a-zA-Z0-9_-]{11,}`, which does not constrain the length upper bound — a very long match is theoretically possible.

**Fix:** Add a server-side guard in `YouTubeScraperService` or in `generateIndex` before setting the context variable:

```java
// In SiteGeneratorService.generateIndex(), after scraping:
if (videoId != null && !videoId.matches("[a-zA-Z0-9_\\-]{1,20}")) {
    log.warn("Scraped videoId '{}' failed safety check, using fallback", videoId);
    videoId = siteProperties.getYoutubeVideoId();
}
ctx.setVariable("videoId", videoId);
```

---

### WR-02: `onStateChange` loop fallback is redundant and has a hidden gap

**File:** `src/main/resources/templates/site/index.html:43-46`

**Issue:** The `loop: 1` + `playlist: videoId` `playerVars` combination is the canonical YouTube loop mechanism and handles looping natively. The `onStateChange` handler adds a manual seekTo(0)+playVideo() fallback for `ENDED` — but YouTube's `ENDED` state is never reached when `loop: 1` is active for single-video playlists on modern browsers. On mobile Safari, the `ENDED` event can still fire before the native loop restarts, causing a brief black frame. More importantly, the fallback calls `event.target.seekTo(0)` without first checking that the player is still in a valid state, which can throw a JS exception if the player has been garbage-collected (e.g. during aggressive mobile browser memory management), crashing the entire script block.

**Fix:** Guard the state-change handler with a ready-flag and a try-catch:

```javascript
var playerReady = false;
events: {
    onReady: function(event) {
        playerReady = true;
        event.target.playVideo();
    },
    onStateChange: function(event) {
        if (!playerReady) return;
        try {
            if (event.data === YT.PlayerState.ENDED) {
                event.target.seekTo(0);
                event.target.playVideo();
            }
        } catch (e) {
            // player may be unavailable during page unload
        }
    }
}
```

---

### WR-03: Click-blocker overlay is not hidden from assistive technology

**File:** `src/main/resources/templates/site/index.html:14`

**Issue:** `<div class="landing-hero-overlay"></div>` is a purely decorative element that blocks pointer events on the video. Without `aria-hidden="true"`, screen readers may announce this empty div as an interactive region (some AT software announces elements with `z-index` or `cursor: default` as focusable). The CLAUDE.md conventions enforce accessibility best practices from previous UX phases (UX-01, UX-07).

**Fix:**

```html
<div class="landing-hero-overlay" aria-hidden="true"></div>
```

---

## Info

### IN-01: `givenActiveSeason_whenGenerate_thenIndexHasYouTubePlayerApi` does not assert `loop` and `mute` playerVars

**File:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java:1243-1257`

**Issue:** The test verifies the presence of `#yt-hero-player`, `.landing-hero-overlay`, and `onYouTubeIframeAPIReady` but does not assert that `mute: 1` and `loop: 1` are present in the rendered script. These are the two `playerVars` values most likely to be accidentally removed during future refactoring (autoplay without mute causes browser autoplay blocking; missing `loop` defeats the purpose of the feature). A brittle test that checks rendered HTML string content would at least catch a regression.

**Fix:** Add assertions for the key playerVars in the script body:

```java
var hasLoopVar = scripts.stream()
        .anyMatch(s -> s.data().contains("loop") && s.data().contains("mute"));
assertTrue(hasLoopVar, "Index player script must declare loop and mute playerVars");
```

---

### IN-02: `SiteProperties` YouTube config fields are not verified by any test

**File:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java:82-95`

**Issue:** The test mocks `YouTubeScraperService` to return a fixed `videoId` (`dQw4w9WgXcQ`) but never verifies that the `SiteProperties.youtubeChannelUrl` and `youtubeVideoId` fields are correctly wired into the scraper call (i.e. `verify(youTubeScraperService).scrapeVideoId(anyString(), anyString())`). If a future refactor changes which properties are passed to `scrapeVideoId`, the mock will still return the fixed ID and the test will pass silently despite the regression.

**Fix:** Add a BDDMockito `then(youTubeScraperService).should().scrapeVideoId(...)` verification in `givenActiveSeason_whenGenerate_thenIndexHasYouTubePlayerApi`, or add a dedicated test:

```java
@Test
void givenActiveSeason_whenGenerate_thenYouTubeScraperIsInvoked() {
    siteGeneratorService.generate();
    then(youTubeScraperService).should().scrapeVideoId(anyString(), anyString());
}
```

---

### IN-03: `.landing-hero` padding-bottom approach is not mobile-safe for full-bleed hero

**File:** `src/main/resources/static/site/css/style.css:676-684`

**Issue:** The `.landing-hero` container uses the classic `padding-bottom: 56.25%; height: 0` technique to maintain a 16:9 aspect ratio. Modern CSS `aspect-ratio: 16 / 9` is widely supported (all evergreen browsers, Safari 15+) and is cleaner. The current approach also applies `margin: 24px 0` which means the video is not full-width inside `.main` — on mobile (`padding: 16px`) there will be 16px gutters on each side of the hero, which may look awkward given the full-bleed intent. This is a style concern, not a correctness bug.

**Fix (optional modernisation):**

```css
.landing-hero {
    position: relative;
    aspect-ratio: 16 / 9;
    overflow: hidden;
    margin: 24px 0;
    border-radius: 8px;
}
```

Remove the now-redundant `height: 0` and `max-width: 100%` — `aspect-ratio` handles both. The absolute-positioned iframe child still works correctly with this approach.

---

_Reviewed: 2026-04-17_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
