---
phase: 48-landing-page-redesign
reviewed: 2026-04-17T16:07:05Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
  - src/main/java/org/ctc/sitegen/SiteProperties.java
  - src/main/java/org/ctc/sitegen/YouTubeScraperService.java
  - src/main/resources/application-dev.yml
  - src/main/resources/application.yml
  - src/main/resources/static/site/css/style.css
  - src/main/resources/templates/site/index.html
  - src/main/resources/templates/site/layout.html
  - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
  - src/test/java/org/ctc/sitegen/YouTubeScraperServiceTest.java
  - src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java
findings:
  critical: 1
  warning: 3
  info: 2
  total: 6
status: issues_found
---

# Phase 48: Code Review Report

**Reviewed:** 2026-04-17T16:07:05Z
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

Reviewed 11 files spanning the landing page redesign (Phase 48) and E2E site validation (Phase 49). The codebase is well-structured with clean separation between the site generator service, YouTube scraper, configuration properties, and templates. Test coverage is thorough with 55+ integration tests in `SiteGeneratorServiceTest` and 8 E2E validation tests in `SiteGeneratorE2ETest`.

Key concerns:
1. **Critical:** The `YouTubeScraperService.scrapeVideoId()` method only catches `IOException` but `Jsoup.connect(null)` throws `IllegalArgumentException` — an unchecked exception can crash site generation.
2. **Warning:** Missing input validation on the `videoId` before embedding in iframe `th:src` — Thymeleaf escapes attribute values, but the fallback config value bypasses the regex-validated scrape path entirely.
3. **Warning:** Duplicate CSS `padding` property in `.hero` rule (dead declaration).
4. **Warning:** E2E test mutates all existing seasons in `@BeforeAll` without rollback, creating test-ordering fragility.

## Critical Issues

### CR-01: YouTubeScraperService catches only IOException — unchecked exceptions propagate

**File:** `src/main/java/org/ctc/sitegen/YouTubeScraperService.java:26-39`
**Issue:** The `scrapeVideoId()` method catches only `IOException`. If `channelUrl` is null or malformed, `Jsoup.connect()` throws `IllegalArgumentException` (unchecked), which propagates up through `generateIndex()` and causes the entire site generation to fail with an unhandled exception. While the `SiteProperties` default makes null unlikely in production, this is a robustness defect — any misconfiguration would crash the full generation pipeline rather than gracefully falling back.
**Fix:**
```java
public String scrapeVideoId(String channelUrl, String fallbackVideoId) {
    if (channelUrl == null || channelUrl.isBlank()) {
        log.warn("YouTube channel URL is null or blank, using fallback videoId");
        return fallbackVideoId;
    }
    try {
        String html = fetchChannelHtml(channelUrl);
        var matcher = VIDEO_ID_PATTERN.matcher(html);
        if (matcher.find()) {
            String videoId = matcher.group(1);
            log.info("Scraped YouTube videoId: {} from {}", videoId, channelUrl);
            return videoId;
        }
        log.warn("No videoId found on YouTube channel page: {}", channelUrl);
    } catch (Exception e) {
        log.warn("Failed to scrape YouTube channel page {}: {}", channelUrl, e.getMessage());
    }
    return fallbackVideoId;
}
```

## Warnings

### WR-01: Fallback videoId from config bypasses regex validation

**File:** `src/main/java/org/ctc/sitegen/YouTubeScraperService.java:39`
**Issue:** The scraped video ID path is validated by `VIDEO_ID_PATTERN` (only `[a-zA-Z0-9_-]`), but the `fallbackVideoId` from `SiteProperties.youtubeVideoId` is returned without any validation. This value is then embedded in an iframe `th:src` attribute. While Thymeleaf's `th:src` performs URL attribute escaping (preventing direct XSS), a malformed fallback could produce a broken or unexpected URL. The iframe in `index.html` (line 13) constructs the URL as `'https://www.youtube.com/embed/' + ${videoId}` — an adversarial config value could redirect the embed to an unintended page.
**Fix:** Validate the fallback value against the same pattern before use:
```java
private boolean isValidVideoId(String videoId) {
    return videoId != null && videoId.matches("[a-zA-Z0-9_-]{11,}");
}

public String scrapeVideoId(String channelUrl, String fallbackVideoId) {
    // ... scraping logic ...
    return isValidVideoId(fallbackVideoId) ? fallbackVideoId : "";
}
```

### WR-02: Duplicate padding property in .hero CSS rule

**File:** `src/main/resources/static/site/css/style.css:139-142`
**Issue:** The `.hero` rule declares `padding` twice — `padding: 40px 0;` on line 139 is immediately overridden by `padding: 48px 32px;` on line 142. The first declaration is dead code that suggests an incomplete refactoring. The second value wins per CSS cascade rules, so there is no visual bug, but it creates confusion about the intended padding.
**Fix:** Remove line 139:
```css
.hero {
    text-align: center;
    background: linear-gradient(180deg, var(--bg-card) 0%, var(--bg) 100%);
    margin: -32px -32px 32px;
    padding: 48px 32px;
}
```

### WR-03: E2E test mutates all existing seasons without rollback

**File:** `src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java:82-88`
**Issue:** The `@BeforeAll` setup renames ALL existing seasons by prepending "Test_" to their names and deactivates them. Since the test uses `@TestInstance(PER_CLASS)` without `@Transactional`, these mutations persist in the H2 database for the entire test context lifetime. If `SiteGeneratorServiceTest` shares the same Spring context (both use `@SpringBootTest @ActiveProfiles("dev")`), the renamed seasons from the E2E setup leak into other test classes. This violates the project's test isolation principle from CLAUDE.md ("E2E test data must use separate entities with a test prefix...never use real teams, drivers, or seasons for automated tests"). The approach of renaming existing data rather than simply creating isolated test data is fragile.
**Fix:** Instead of mutating existing seasons, the E2E test setup should create its own data with unique test prefixes (as it does for teams and drivers), and rely on the "Test" name filter in `SiteGeneratorService.generate()` to exclude non-test data. Or use `@DirtiesContext` to ensure the context is reset after this class.

## Info

### IN-01: Iframe missing title attribute (accessibility)

**File:** `src/main/resources/templates/site/index.html:13-15`
**Issue:** The YouTube embed iframe lacks a `title` attribute. WCAG 2.1 guideline 4.1.2 requires that iframes have an accessible name. The rest of the layout demonstrates attention to accessibility (skip-link at line 12 of layout.html, `aria-label` on nav toggle, `aria-hidden` on decorative separators), so this is likely an oversight.
**Fix:**
```html
<iframe th:src="'https://www.youtube.com/embed/' + ${videoId}"
        title="Community Team Cup YouTube Video"
        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
        allowfullscreen></iframe>
```

### IN-02: YouTubeScraperService test does not cover null channelUrl

**File:** `src/test/java/org/ctc/sitegen/YouTubeScraperServiceTest.java`
**Issue:** The test class covers the happy path (valid page), no-match fallback, IOException fallback, and multiple-ID scenarios — but does not test what happens when `channelUrl` is null or blank. This gap corresponds directly to CR-01 above.
**Fix:** Add a test case:
```java
@Test
void givenNullChannelUrl_whenScrapeVideoId_thenReturnsFallback() {
    // when
    var result = service.scrapeVideoId(null, "fallback-id");

    // then
    assertThat(result).isEqualTo("fallback-id");
}
```

---

_Reviewed: 2026-04-17T16:07:05Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
