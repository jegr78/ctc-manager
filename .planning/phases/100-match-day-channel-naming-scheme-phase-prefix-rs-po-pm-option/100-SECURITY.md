---
phase: 100
slug: match-day-channel-naming-scheme-phase-prefix-rs-po-pm-option
status: verified
threats_open: 0
asvs_level: 1
created: 2026-05-26
audited: 2026-05-26
audit_method: retroactive-STRIDE
---

# Phase 100 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.
> Retroactive-STRIDE audit: phase plans did not contain `<threat_model>` blocks at
> plan time (Phase 100 was scoped as a small naming-scheme refactor inside an
> already-secured `DiscordChannelService`); register reconstructed from
> implementation files post-execution.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Operator UI → CTC-Manager | Authenticated operator clicks `Create Discord Channel` on `/admin/matches/{id}/edit`. CSRF + HTTP-Basic on prod/docker; CSRF only on dev/local. | `Match`, `Team`, `SeasonPhaseGroup` (DB-sourced; no operator-supplied content in this request) |
| CTC-Manager → Discord API | Spring `RestClient` outbound POST `/guilds/{id}/channels` with channel-name JSON payload. Allowlisted via `DiscordHostValidator`. Bot-token bearer auth. | Channel name (composed from `Matchday.sortIndex`, `PhaseType`, `SeasonPhaseGroup.name`, `Team.shortName`) |
| Discord API → Operator's Discord client | Discord persists channel and renders the name verbatim to all members of the guild. | Channel name + permission overwrites |

---

## Threat Register (retroactive STRIDE)

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-100-01 | Tampering | `DiscordChannelService.channelName(Match)` — JSON body shipped to Discord | mitigate | Channel name is composed in Java and serialized to JSON by Jackson via Spring `RestClient`. Jackson auto-escapes `"`, `\`, control chars. No raw-string concatenation into the HTTP body. Operator-controlled `Team.shortName` and `SeasonPhaseGroup.name` cannot break JSON envelope (covered by framework). | closed |
| T-100-02 | Tampering | Discord channel name regex compliance | mitigate | Discord enforces `^[a-z0-9_-]{1,100}$` on channel names. The `groupSlug` chain in `DiscordChannelService:163-170` reduces user input to `[a-z0-9-]`; team shortNames are appended verbatim then lowercased per D-11. If a future operator manually edits a `Team.shortName` to contain spaces/Unicode/illegal chars, Discord's API returns `400 Bad Request` → propagated as `DiscordApiException` → operator-friendly flash via existing typed-catch in `MatchController` (Phase 91 UX-01 pattern preserved). | closed |
| T-100-03 | Tampering | 100-char cap | mitigate | `DiscordChannelService.channelName(Match):148-151` throws `BusinessRuleException` when composed name exceeds 100 chars. Verified live in `100-UAT.md` Test 5 (85-char group name → `…(103)` flash, no channel created). Operator sees produced name + length and shortens the group name in `/admin/seasons/{id}/phases/{p}/groups/{g}/edit`. No silent-truncate, no integer-overflow path. | closed |
| T-100-04 | Information Disclosure | Channel name leaks group + team membership to Discord guild | accept | The Discord channel name is the deliberate visibility surface for the league's match — that is its function. By design (D-01) the name encodes matchday, phase, optional group, and both team short-names. The operator chooses which Discord guild and which member roles see the channel; the channel-name itself contains no secret data (no tokens, no IDs from outside the public league context). Group-membership is operator-public information by construction (race-league teams need to know who they race against). | closed (accepted) |
| T-100-05 | Information Disclosure | Operator-facing `BusinessRuleException` flash echoes the full produced name | accept | The 100-char overflow flash text contains the full produced channel name + its length. This is operator-facing (admin UI requires HTTP-Basic + CSRF on prod/docker) — operator already has full read access to all team shortNames and group names via `/admin/teams` and `/admin/seasons/.../groups/`. No additional secret is leaked by the echo. | closed (accepted) |
| T-100-06 | Denial of Service | `groupSlug` regex chain | mitigate | Four `replaceAll` operations on `SeasonPhaseGroup.name`. Per IN-06 fix (commit `d06fb07c`), patterns are pre-compiled as `private static final Pattern` constants — no per-call regex compilation. All patterns are linear (`\\p{M}`, `[^a-z0-9]`, `-{2,}`, `^-|-$`) — no nested quantifiers, no catastrophic-backtracking risk. Group name length is bounded by the DB column (implicit varchar) and operator-controlled. The 100-char cap on the composed name is the ultimate ceiling. | closed |
| T-100-07 | Denial of Service | Discord API rate-limit on channel creation | mitigate | Channel creation is gated by the existing `DiscordRateLimitInterceptor` (per-bucket token-bucket + max-5-parallel; Phase 93 INFRA-01). Phase 100 changes only the channel-name string composition, not the rate-limit logic. No regression possible. | closed |
| T-100-08 | Elevation of Privilege | Channel created with wrong permission-overwrites | mitigate | `DiscordChannelService.createMatchChannel(Match):51-105` constructs 4 permission-overwrites (everyone-DENY, home-team-ALLOW, away-team-ALLOW, bot-user-ALLOW) and calls `assertPermissionAudit` to verify Discord echoed them back correctly. Audit-failure triggers cleanup-DELETE (Phase 94 CHAN-02). Phase 100 does NOT touch the permission-overwrite path. Live-verified in `100-UAT.md` Tests 2-4 — all 3 new channels show the expected 4-overwrite shape in Discord. | closed |
| T-100-09 | Tampering | Outbound-name regression (production emits malformed channel name despite WireMock IT staying green) | mitigate | `DiscordChannelServiceWireMockIT:149` adds `matchingJsonPath("$.name", equalTo("md1-rs-homeh-vs-awayh"))` outbound-name pinning assertion (added in commit `c9400e00`). Closes the "WireMock is not Real-API Coverage" gap from CLAUDE.md — a future production-side regex/payload regression that emits the wrong outbound name fails this assertion immediately. | closed |

**Threat count:** 9 threats (7 mitigate / 2 accept) — all closed.

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-100-01 | T-100-04 | Channel-name visibility is the deliberate function of the feature. League operator controls guild membership, role visibility, and team-shortName publishing. No secret data crosses this boundary. | Jens Gross (operator) | 2026-05-26 |
| R-100-02 | T-100-05 | Admin-UI flash content is bounded by `BusinessRuleException` message format. Audience is the authenticated operator who already has full read access to all team/group data. Echoing the produced name aids diagnosis (D-10 rationale). | Jens Gross (operator) | 2026-05-26 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-05-26 | 9 | 9 | 0 | orchestrator (inline retroactive-STRIDE; --interactive mode) |

---

## Cross-Reference: Static-Analysis Gates

Phase 100 passes all blocking SAST gates at phase-end:

- **SpotBugs + find-sec-bugs:** `BugInstance` count = 0 (verify-bound `<goal>check</goal>` gate; `./mvnw clean verify -Pe2e` 2026-05-26 BUILD SUCCESS)
- **CodeQL `security-extended`:** No new HIGH/CRITICAL findings introduced by Phase 100 commits (workflow `.github/workflows/codeql.yml` gate-step)
- **JaCoCo:** 88.98 % line coverage on `DiscordChannelService` (≥ project gate 82 %, exceeds v1.11 baseline 88.88 %)

No CodeQL FP-suppressions added in this phase. `docs/security/sast-acceptance.md` unchanged.

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log (R-100-01, R-100-02)
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter
- [x] Cross-reference to SAST gates current (SpotBugs 0, CodeQL HIGH/CRITICAL 0)

**Approval:** verified 2026-05-26
