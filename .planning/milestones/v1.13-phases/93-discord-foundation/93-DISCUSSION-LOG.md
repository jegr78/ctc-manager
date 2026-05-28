# Phase 93: Discord Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-21
**Phase:** 93-discord-foundation
**Areas discussed:** Live-Discord UAT vs WireMock-only, DiscordGlobalConfig Single-Row-Enforcement, EmojiCache + RateLimitBucket Cache-Impl, Threat-Model Artifact-Shape

---

## Live-Discord UAT vs WireMock-only

| Option | Description | Selected |
|--------|-------------|----------|
| WireMock-IT + deferred UAT-03 (Recommended) | Phase 93 closes on WireMock-IT-coverage allein. Operator-UAT gegen Live-Discord (Test Connection + Test Announcement-Webhook) wird als UAT-03 in STATE.md `Pending UATs` deferred und vor Phase 94 (CHAN-02 channel-create real testen) gegen Test-Server ausgeführt. Analog UAT-02-Pattern. Schnell, aber Bot-Auth-Fehler treffen erst in Phase 94. Mitigation: 94-01 (Team-Role-Mapping) ist DB-only und unblockiert, real-Discord erst CHAN-02. | ✓ |
| Live-UAT als Phase-93-Close-Gate | Phase 93 wird erst geschlossen, wenn Operator gegen Live-Test-Server: (a) Test Connection grün, (b) Test Announcement-Webhook ländet, (c) Refresh-Emoji-Cache zeigt Einträge. Erkennt Bot-Setup-Probleme früh (Token, OAuth-Scopes, Guild-ID), kostet 30-60 min Operator-Zeit, verzögert Phase 94 bis UAT durch. Sicherheitsmarge. | |
| Beide: Live-UAT als 93-Close-Soft-Gate + UAT-03 post-deploy für prod-creds | Phase 93 schließt mit Live-UAT gegen Test-Server (Bot-Token funktioniert grundsätzlich), ABER eine zweite UAT-03 mit prod-Token + prod-Guild bleibt post-deploy deferred (operator action). Doppelt redundant, max-Sicherheit, höchster Operator-Aufwand. | |

**User's choice:** WireMock-IT + deferred UAT-03 (Recommended)
**Notes:** UAT-03 wird neuer STATE.md `Pending UATs` Eintrag analog UAT-02 / QUAL-02 / UX-01. 94-01 Team-Role-Mapping ist DB-only und unblockiert; real-Discord-Interaktion erst CHAN-02 — Live-UAT-Kosten genau einmal am richtigen Punkt fällig, nicht zweimal. Phase 94 success criteria referenziert UAT-03 als hard-precondition für CHAN-02-Start.

---

## DiscordGlobalConfig Single-Row-Enforcement

| Option | Description | Selected |
|--------|-------------|----------|
| Service-side singleton mit `findFirstByOrderByIdAsc()` + Insert-Guard (Recommended) | `DiscordGlobalConfigService.getOrThrow()` ruft `repo.findFirstByOrderByIdAsc().orElseThrow()`. `save()` Methode ruft `findFirstByOrderByIdAsc()`, hängt id falls vorhanden, sonst Insert. Page-init: wenn null → leere Form mit `id=null`; nach Save id=1 (immer). Default-Row insertion via Flyway V8 als Bootstrap (NULL-Werte für alle config-fields; UI zeigt 'not configured'). Keine UI-Bedingungen für Multi-Row-Risiko. Standard Spring-Data-Pattern. | ✓ |
| DB-CHECK-Constraint `id = 1` + Flyway-Seed-Row | V8 ergänzt `CONSTRAINT chk_singleton CHECK (id = 1)` + initialen Seed-Row mit `id=1` und Empty-Strings. Service liest immer `findById(1L)`. Strikt DB-enforced, aber: H2 vs MariaDB CHECK-Verhalten unterschiedlich (H2 2.x enforced, MariaDB 10.7+ enforced; ältere Versionen ignorieren CHECK). Risiko Drift. Erhöhter Migration-Test-Aufwand. | |
| Application-Property + DB hybrid | Guild-ID, Webhook-URLs in `application-*.yml` (env-var overlay), nur volatile state (`last_emoji_refresh_at`, `bot_application_id_cache`) in DB. Sehr Spring-native, kein V8 mit user-form-fields. ABER: widerspricht INFRA-03 Acceptance Criterion ('admin page provides operator surface for guild-ID input ... announcement-webhook-URL input ...'). REJECTED: macht Phase 93 zu klein, Operator müsste yml editieren. | |

**User's choice:** Service-side singleton mit `findFirstByOrderByIdAsc()` + Insert-Guard (Recommended)
**Notes:** V8 enthält INSERT eines empty seed-rows (`guild_id=''`, `vs_emoji_name='CTC'`, andere Felder ''). Service `getOrInitialize()` returnt deterministisch eine Row — kein Optional-Handling in callern. UI rendert empty fields mit "not configured" Badges bis Operator speichert. Cross-engine compatibility erhalten (`CURRENT_TIMESTAMP` + empty-string DEFAULT funktionieren auf H2 + MariaDB). Phase 72 D-09 LONGTEXT precedent zeigt cross-engine CHECK-Drift-Risiko.

---

## EmojiCache + RateLimitBucket Cache-Impl

| Option | Description | Selected |
|--------|-------------|----------|
| Hand-rolled `ConcurrentHashMap<String, CachedEntry>` mit `Instant.now()` TTL-Check (Recommended) | Strikt zero-new-deps. Pattern: `record CachedEntry<T>(T value, Instant expiresAt)`. Lookup: `map.get(key).filter(e -> e.expiresAt().isAfter(now()))`. Refresh: `map.put(key, new CachedEntry(...))`. Rate-Limit-Bucket analog: `ConcurrentHashMap<String, BucketState>` mit remaining/resetAt. ~80-100 LOC pro Cache. Volle Test-Kontrolle via Clock-Injection. Phase 86 Pattern (no-frills custom util). | ✓ |
| Spring `@Cacheable` + `ConcurrentMapCacheManager` (built-in) | Spring-Boot-Auto-Config ConcurrentMapCache, Annotation `@Cacheable(value="discord-emojis")` + manual `@CacheEvict` für Refresh-Button. ABER: Spring's built-in ConcurrentMapCache hat KEIN TTL out-of-the-box (Caffeine bietet das). Für TTL müsste man schedule-evict via Spring-Scheduler. Für Rate-Limit-Bucket nicht passend (per-request state, kein method-level cache). Mismatch zum Use-Case. | |
| Guava `CacheBuilder` (schon transitiv da via 33.4.8-jre) | Guava ist bereits explizite Dependency (`<guava.version>` 33.4.8-jre, override Phase 68). `CacheBuilder.newBuilder().expireAfterWrite(60, MINUTES).build()`. Battle-tested TTL-Semantik. Rate-Limit-Bucket via `Cache<String, BucketState>` mit `expireAfterAccess` per Bucket-`resetAt`. Kein neuer prod-dep (Guava ist drin), aber: macht Guava-Cache prod-relevant statt nur Build-Hilfe. Verifizieren ob `com.google.common.cache` im fat-jar landet. | |

**User's choice:** Hand-rolled `ConcurrentHashMap<String, CachedEntry>` mit `Instant.now()` TTL-Check (Recommended)
**Notes:** Beide Caches teilen sich das `record CachedEntry<T>(T value, Instant expiresAt)` aus `org.ctc.discord.util.CachedEntry`. `Clock` per Spring `@Bean Clock systemClock()` für Test-Replay via `Clock.fixed(...)`. Phase 86 D-11 no-frills-util-Präferenz greift; Guava-Cache als prod-relevant ist semantischer Dep-Contract-Wechsel (Renovate-scope), nicht-trivial; defer auf v1.14 wenn ein 3. Cache aufkommt.

---

## Threat-Model Artifact-Shape

| Option | Description | Selected |
|--------|-------------|----------|
| Eigenständiges `93-THREAT-MODEL.md` Artefakt (Recommended) | Ein Markdown-File `93-THREAT-MODEL.md` im Phase-Dir mit Tabelle T-93-01..04 (Threat / Likelihood / Impact / Mitigation / Verification). Zitiert von 93-02-PLAN.md + 93-02-SUMMARY.md. Später wieder-referenzierbar von Phase 94 (T-93-03 channel-permission audit landet erst in CHAN-02) und docs/operations/discord-integration.md (Phase 98 DOCS-02 Troubleshooting). Analog zu `docs/security/sast-acceptance.md`. Single source-of-truth, von Phase 94+ Plans referenzierbar. | ✓ |
| Inline-Block in 93-02-PLAN.md | Threat-Tabelle als Sektion `## Threat Model` im PLAN, T-93-01..04 als Mitigation-Tasks. Verifikation pro Task. Gehört zu Plan-Lifecycle (PLAN → EXECUTE → SUMMARY). Kein eigenständiges Artefakt. Kürzer, aber: Phase 94 müsste auf Plan-Datei statt eigenständiges Threat-Doc verweisen, und Plan-Datei wird typischerweise nicht mehr geändert nach Execution (Phase 94 erweitert dann eigenes Threat-Doc). | |
| Aufgeteilt: Threats in 93-02-PLAN.md, Mitigation-Surfaces inline in jeder relevanten Datei | Keine separate Threat-Markdown. Threat-IDs T-93-01..04 leben nur in 93-02-PLAN.md, jede Mitigation als Source-Marker-Kommentar an der Stelle, wo sie greift (analog CodeQL FP-Suppression). Maximale Code-Anbindung, aber Threat-IDs sind verteilt, kein zentraler Audit-Punkt. Phase 98 Troubleshooting müsste alle Source-Marker grep'en. | |

**User's choice:** Eigenständiges `93-THREAT-MODEL.md` Artefakt (Recommended)
**Notes:** Artifact analog `docs/security/sast-acceptance.md` Update-on-Triage Discipline. Spans Phase 93 (T-93-01..04 + 6 mitigation surfaces a-f) und vorwärts-referenziert von Phase 94 CONTEXT (T-93-03 channel-permission audit landet erst dort) sowie Phase 98 DOCS-02 (Troubleshooting-Sektion in operator runbook). Setzt v1.13 Konvention: Phase 94 may add `94-THREAT-MODEL.md` für CHAN-02 permission-overwrite-risks falls T-93-03 phase-local elaboration verlangt.

---

## Claude's Discretion

- **Package layout for `DiscordConfigController`** — `org.ctc.admin.controller.discord` vs `org.ctc.discord.web`. Planner picks per Phase-92 convention; both valid per CLAUDE.md § Naming Patterns.
- **Exact CSS variant für "not configured" badges** — likely `.error-badge` + neutral variant or `.badge-warning`; planner verifies against `admin.css` palette.
- **Navigation entry placement** — under existing "Integrations" group if exists, sonst sibling zu Google-Sheets-Import. Planner decides based on `admin/layout.html` actual nav structure.
- **Exact `application.yml` `logging.pattern` regex** — must mask webhook-URL pattern AND nicht regress existing log readability. Planner verifies via `DiscordLogMaskingTest`.
- **`DiscordTimestamps` as static-util vs Spring `@Component`** — `Clock`-injection via `DiscordEmojiCache` constructor; `DiscordTimestamps` static (params) or singleton-bean (injected). Planner picks per test-shape ergonomics.
- **Test-buttons on config page sync POST + page-reload vs AJAX + inline-result** — planner picks based on Phase-30 CSRF existing precedent (likely sync POST + flash + page-reload per CLAUDE.md § Flash Attributes).
- **Exact T-93-01..04 cell prose in `93-THREAT-MODEL.md`** — D-04 + D-10 lock structure; planner mirrors `docs/security/sast-acceptance.md` style.

## Deferred Ideas

- **Guava `CacheBuilder` promotion zu first-class prod-dep** — defer auf v1.14 backlog wenn 3. Cache emergiert in Phase 94+.
- **Spring `@Cacheable` + `@Scheduled`-evict adoption** — selbe Defer-Logik.
- **CodeQL FP-Suppression für positive-whitelist SSRF** — Plan 93-02 owns falls CodeQL `DiscordRestClient` / `DiscordWebhookClient` host-validation flagged; 3-layer Phase 85 D-19 invariant.
- **`docs/operations/discord-integration.md`** — Phase 98 DOCS-02 scope; Phase 93 plans nur forward-reference.
- **Multi-Guild support / DISC-FUTURE-04** — `findFirstByOrderByIdAsc` singleton pattern (D-02) makes future multi-guild EASIER (V13 add `guild_id_pk` UNIQUE).
- **Always-online deployment / DISC-FUTURE-01** — Phase 93 strukturell outbound-only.
- **Per-user timezone override** — REQUIREMENTS.md Out-of-Scope; `app.timezone` ist server-global.
- **Caffeine adoption** — only if hand-rolled cache surfaces concurrency / TTL bugs in Phase 94-97 use.
