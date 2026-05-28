# Phase 100: Match Day Channel Naming Scheme — Context

**Gathered:** 2026-05-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Extend the Discord match-channel naming scheme established in Phase 94
(`md{N}-{home}-vs-{away}`) by two orthogonal tokens:

1. **Phase-type prefix** (`rs` / `po` / `pm`) derived from
   `match.getMatchday().getPhase().getPhaseType()`.
2. **Optional group prefix** derived from
   `match.getMatchday().getGroup()` (a nullable `SeasonPhaseGroup`).

The resulting format is `md{N}-{phase}-[{group}-]{home}-vs-{away}`.

**In scope:**
- Refactor `DiscordChannelService.channelName(Match)`
  (`src/main/java/org/ctc/discord/service/DiscordChannelService.java:120-127`)
  to produce the new format.
- Update all existing IT/WireMock fixtures that hard-code the old format
  (`md1-h-vs-a`, `md1-home-vs-away`, `md1-hc-vs-ac`) — 8 test files identified
  in `<code_context>` below.
- Defensive validation: fail-fast `BusinessRuleException` if the produced
  channel name exceeds Discord's 100-char cap (no silent truncate).

**Out of scope (explicit non-goals):**
- Migration of existing match-channels created under the old scheme — operator
  decision: leave-as-is (D-04). No PATCH-rename action, no diagnostic list, no
  lazy auto-rename. Only newly-created channels follow the new scheme.
- Changes to archive-category naming (Phase 94 CHAN-03 scope, untouched).
- Changes to webhook naming (`"CTC Manager"` per `DiscordChannelService:42`).
- Changes to thread / forum-channel / announcement-channel naming (Phase 96 / 97
  scope).
- Operator-configurable phase-abbreviations (rejected — overkill for 3 static
  enum values; see D-02 rationale).
- A `SeasonPhaseGroup.slug` field — rejected (rejected option in Gray-Area 3;
  full-slugify derivation is sufficient).
- Milestone-scope question (whether Phase 100 ships inside v1.13 vs opens
  v1.14) — that's a roadmap / orchestrator concern, not a CONTEXT decision.

</domain>

<decisions>
## Implementation Decisions

### Token Order & Format

- **D-01:** Channel-name format is `md{N}-{phase}-[{group}-]{home}-vs-{away}`,
  all lowercase, dash-separated. `mdN` is the leading token so Discord's
  alphabetical channel sort groups by matchday number first; phase-type is the
  second token, group (when present) is the third token.

  Concrete examples (using existing TestDataService team shortNames):
  ```
  md3-rs-alf-vs-bra          # Regular Season, MD3, no group
  md3-rs-group-a-alf-vs-bra  # Regular Season, MD3, "Group A"
  md3-rs-group-b-alf-vs-bra  # Regular Season, MD3, "Group B"
  md1-po-alf-vs-bra          # Playoff, MD1, no group (playoffs are typically league)
  md1-pm-alf-vs-bra          # Placement, MD1, no group
  ```

- **D-02:** When `matchday.getGroup() == null` (LEAGUE-layout phase, no group
  membership), the group token is **omitted entirely** — no placeholder, no
  empty dash sequence. The format collapses to `md{N}-{phase}-{home}-vs-{away}`.

- **D-03:** Final transformation is `.toLowerCase(Locale.ROOT)` on the fully
  composed string (preserving the existing Phase-94 invariant — see
  `DiscordChannelService.channelName(Match):126`).

### Phase-Type Abbreviation Mapping

- **D-04:** Hardcoded switch in `DiscordChannelService` (or a private static
  helper in the same class). Source: `match.getMatchday().getPhase().getPhaseType()`.
  Java 21+ switch-expression:
  ```java
  private static String phaseAbbrev(PhaseType type) {
      return switch (type) {
          case REGULAR   -> "rs";
          case PLAYOFF   -> "po";
          case PLACEMENT -> "pm";
      };
  }
  ```
  Rationale: 3 static enum values; configurability via `DiscordGlobalConfig`
  was rejected as YAGNI. Exhaustiveness is compiler-enforced — adding a new
  `PhaseType` value in a future phase will trigger a compile-fail on this
  switch, which is desired (forces the operator to pick an abbreviation
  explicitly).

- **D-05:** No fallback / `default` branch — exhaustive sealed switch only.
  Per CLAUDE.md "Doing tasks": no error handling for scenarios that can't
  happen.

### Group-Slug Derivation

- **D-06:** Full slugified `SeasonPhaseGroup.name` (NOT shortened, NOT
  last-token-only, NOT an explicit slug field). Rule:
  1. ASCII-decompose via `java.text.Normalizer.normalize(name, Form.NFD)`
     followed by stripping combining marks (`replaceAll("\\p{M}", "")`).
     Captures umlauts: `Über` → `Uber`, `Schöne` → `Schone`.
  2. Lowercase.
  3. Replace every non-`[a-z0-9]` character with `-`.
  4. Collapse consecutive `-` to a single `-`.
  5. Trim leading / trailing `-`.

  Examples:
  ```
  "Group A"       → "group-a"
  "Group B"       → "group-b"
  "Bronze"        → "bronze"
  "Pro Division"  → "pro-division"
  "Über-Liga"     → "uber-liga"
  "Group A!!!"    → "group-a"
  ```

- **D-07:** Edge case — empty slug after derivation (e.g., name consists only
  of non-alphanumeric chars after `@NotBlank` somehow). Defensive guard:
  if slug is empty after derivation, treat as `group == null` (omit token
  entirely). Do NOT throw — `@NotBlank` already enforces non-empty input at
  the form layer; this is defense-in-depth, not user-facing.

### Migration of Existing Channels

- **D-08:** **Leave-as-is.** Existing match-channels created under the old
  Phase-94 scheme (`md1-{home}-vs-{away}`) retain their current names. No
  PATCH-rename action, no diagnostic UI list, no lazy auto-rename trigger.
  Operator deletes obsolete test-channels manually in Discord if desired.

- **D-09:** Phase 100 deliberately introduces a "two schemes coexist" state
  for already-existing rows in `matches` where `discord_channel_id IS NOT NULL`.
  This is acceptable because (a) the column stores only the Discord channel ID,
  not the name — Discord is the source of truth for the live channel name;
  (b) the operator's v1.13 channels are predominantly UAT artifacts, not
  production league channels; (c) all post-Phase-100 channel creations
  produce the new scheme uniformly.

### Discord 100-Char Cap (Defensive Validation)

- **D-10:** Discord enforces a 100-character maximum on channel names. With
  the new scheme, worst-case length is approximately:
  `md99` (4) + `-` + `rs` (2) + `-` + `pro-division` (12) + `-` + 4 char-
  shortName + `-vs-` + 4-char-shortName = ~33 chars typical, but
  group-slug + shortName combinations could spike for unusually long inputs.

  Add a defensive check at the end of `channelName(Match)`: if the produced
  string `> 100` chars, throw `BusinessRuleException` with a clear message
  (`"Discord channel name exceeds 100 characters: <produced-name> (<len>)"`),
  NOT silently truncate. Operator surfaces this via existing typed-catch in
  `DiscordChannelService.createMatchChannel` → flash message → operator
  shortens the group name in `/admin/seasons/{id}/phases/{phaseId}` form.

- **D-11:** No regex pre-validation of `shortName` chars during channel-name
  composition — `Team.shortName` is already constrained by Team-Form
  validation (Phase 1-era pattern). The slugify rule in D-06 only applies to
  `SeasonPhaseGroup.name`; `shortName` is appended verbatim (then lowercased
  by D-03). If a `shortName` somehow contains Discord-illegal chars
  (e.g., spaces, slashes), Discord's own API will reject the create-channel
  call — that's an existing Phase-94 invariant, NOT a Phase-100 regression.

### Code Placement

- **D-12:** Keep the naming logic inside `DiscordChannelService` (private
  static methods `channelName(Match)` + `phaseAbbrev(PhaseType)` +
  `groupSlug(SeasonPhaseGroup)`). Do NOT extract to a separate
  `MatchChannelNamer` class — YAGNI; only one caller exists
  (`createMatchChannel`). If a second caller emerges in v1.14 (e.g.,
  rename-button), extract then.

- **D-13:** Unit-test the naming logic indirectly via existing
  `DiscordChannelServiceWireMockIT` + `DiscordChannelServicePermissionAuditFailIT`
  + `DiscordChannelServiceCleanupFailIT` by updating their fixtures to feed
  matches with phase-type + group via TestDataService helpers, and asserting
  the WireMock `urlPathMatching` expectations on the new channel names.
  Optionally, add a small parameterized `DiscordChannelServiceNamingTest`
  (pure unit, no Spring context) for the 6 example cases in D-01 + D-06 if
  the WireMock-level coverage feels indirect — planner's call.

### Test-Fixture Impact

- **D-14:** All 8 existing test files that hard-code old names must be
  refreshed (see `<code_context>` for the full list). The refresh is
  mechanical: each `md1-h-vs-a` / `md1-home-vs-away` / `md1-hc-vs-ac` fixture
  becomes either `md1-rs-h-vs-a` (no group) or `md1-rs-group-a-h-vs-a`
  (with group), depending on what the test sets up. The TestDataService
  team fixtures (e.g., `T-ALF` / `T-BRA`) are stable — only the channel-name
  literal strings in WireMock stubs change.

### Claude's Discretion

- Exact wording of the `BusinessRuleException` message in D-10 is planner's
  call — must include the produced name (for operator triage) and the actual
  length.
- Whether D-13's optional `DiscordChannelServiceNamingTest` is added is
  planner's call; the WireMock-level coverage may be sufficient.
- Method extraction in D-12 (keeping inside `DiscordChannelService` vs a
  package-private helper class in `org.ctc.discord.service`) is fine either
  way as long as the public API surface of `DiscordChannelService` is
  unchanged.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase-94 baseline (the scheme being extended)
- `.planning/phases/94-team-roles-match-channel-lifecycle/94-CONTEXT.md` —
  CHAN-02 acceptance: "channel name follows `md{N}-{teamA.shortName}-vs-{teamB.shortName}`
  (lowercase, dash-separated, Discord-enforced)". Phase 100 supersedes this
  with the new format from D-01.
- `.planning/milestones/v1.13-ROADMAP.md` § Phase 94 SC line referencing the
  same naming invariant — update is out-of-scope for Phase 100 (historical
  milestone artifact; v1.13 closes via `/gsd-complete-milestone v1.13`).

### Existing implementation (the file to modify)
- `src/main/java/org/ctc/discord/service/DiscordChannelService.java` —
  `channelName(Match)` at line 120-127 is the sole call site producing the
  current name. `createMatchChannel` at line 52 is the public entry point.

### Domain model (sources of the new tokens)
- `src/main/java/org/ctc/domain/model/PhaseType.java` — enum with `REGULAR`,
  `PLAYOFF`, `PLACEMENT` (no other values exist as of 2026-05-26).
- `src/main/java/org/ctc/domain/model/SeasonPhase.java` — `phaseType` field
  is `@Column(nullable = false)`, always present on every `Matchday.phase`.
- `src/main/java/org/ctc/domain/model/SeasonPhaseGroup.java` — `name` field
  is `@NotBlank @Column(nullable = false)`. Source of the group slug.
- `src/main/java/org/ctc/domain/model/Matchday.java` — `phase` (NOT NULL),
  `group` (nullable, only set when `PhaseLayout.GROUPS`). Convenience getter
  `getSeason()` derives via `phase.getSeason()`.
- `src/main/java/org/ctc/domain/model/Match.java` — `matchday` is NOT NULL;
  reach phase + group via `match.getMatchday().getPhase()` + `getGroup()`.

### Test fixtures that hard-code old names (must be updated)
- `src/test/java/org/ctc/discord/DiscordRestClientIT.java:206` — `md1-h-vs-a`
- `src/test/java/org/ctc/discord/service/DiscordChannelServicePermissionAuditFailIT.java`
  lines 111, 128, 154, 182 — `md1-h-vs-a`
- `src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java`
  lines 91, 113 — `md1-h-vs-a`
- `src/test/java/org/ctc/discord/service/DiscordChannelServiceCleanupFailIT.java`
  lines 117, 125 — `md1-hc-vs-ac`
- `src/test/java/org/ctc/discord/service/DiscordChannelServiceWireMockIT.java`
  lines 123, 130, 182, 189, 219 — `md1-home-vs-away`
- `src/test/java/org/ctc/discord/service/DiscordAutoPostListenerIT.java`
  lines 200, 222 — `createMatchChannel(match)` calls; literals likely
  referenced in test stubs (re-grep during planning to confirm).

### Project-level invariants (must read before planning)
- `CLAUDE.md` — "Build & Test Discipline" (`./mvnw clean verify -Pe2e` gate,
  no flaky dismissal), "No Comment Pollution" (no Phase/Plan refs in source),
  "Subagent Rules" (inline sequential, no worktrees, no parallel waves),
  "Architectural Principles" → "Grep All Usages Before Refactor" (planner
  MUST grep ALL test files for the 3 old literals before deciding the test
  fixture scope), "WireMock is not Real-API Coverage" (WireMock stubs use
  `urlPathMatching` + `withQueryParam` — channel-name changes affect the
  body, not the URL, so existing route-stubs stay green; only the response
  JSON bodies need refresh).
- `.planning/STATE.md` — current milestone state (`v1.13`, Phase 99 context
  gathered). Phase 100 may extend v1.13 OR open v1.14 — orchestrator
  decides at plan time, not here.

### Discord platform constraints
- Discord developer docs (no local copy): channel names must match
  `^[a-z0-9_-]{1,100}$`. The 100-char cap drives D-10; the `[a-z0-9_-]`
  pattern drives the slugify rule in D-06 (no apostrophes, no spaces, no
  unicode — diacritics stripped via NFD-decompose).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`DiscordChannelService.channelName(Match)` private static** — the single
  place to change. Existing method is 7 lines. New method will be ~15-20
  lines including the slugify helper or ~25 if the helper is extracted.

- **TestDataService phase + group fixtures** — `s1` (Season 1) already has
  `s1Regular` (PhaseLayout.GROUPS) with `s1GroupA` + `s1GroupB`
  (`src/main/java/org/ctc/admin/TestDataService.java:188-196`). E2E /
  Playwright tests covering the new naming scheme can build matches against
  these fixtures without new seed-data.

- **`PhaseLayout` enum** — confirms `LEAGUE` (no groups) vs `GROUPS`
  semantics. The `LEAGUE` case is what makes the group token "optional"
  in D-01 (it's actually a function of `PhaseLayout`, but driven directly
  by the nullable `Matchday.group` reference, which is the more precise
  source).

### Established Patterns

- **`@Transactional` on createMatchChannel** — the new `channelName(Match)`
  is a pure function (no I/O, no Discord calls), so adding it doesn't change
  the transaction boundary. Phase 94 invariant preserved.

- **Defensive validation throws `BusinessRuleException`** — pattern already
  used in `DiscordChannelService.assertPreconditions` (line 107-118). D-10's
  100-char-cap check follows the same pattern: throw early, let the controller-
  level typed-catch surface a flash message.

- **WireMock IT stub bodies, not URLs** — Discord create-channel POSTs use
  `urlPathEqualTo("/guilds/{id}/channels")`. Channel-name changes only the
  request body (`.withRequestBody(matchingJsonPath(...))` for asserting
  outbound) and the response body. The matching/stub URLs do NOT change —
  this scopes the test-fixture refresh to JSON literals only.

- **Inline sequential execution on milestone branch** — Phase 100 follows
  the v1.13 inline-sequential discipline (CLAUDE.md "Subagent Rules") even
  if it later turns out to belong to v1.14. The orchestrator decides
  branching at plan time; the discussion captures decisions only.

### Integration Points

- **`createMatchChannel` callers** — currently a single button-handler on
  `/admin/matches/{id}/edit` (`MatchController`) + the auto-post listener
  `DiscordAutoPostListenerIT` covers. Neither caller needs changes; the
  naming change is purely internal to `DiscordChannelService`.

- **Future v1.14 rename-button (NOT this phase)** — if a v1.14 plan adds an
  admin button to re-name pre-Phase-100 channels, it would call Discord
  `PATCH /channels/{id}` with the new `channelName(match)` output. Phase 100
  deliberately does NOT build this — see D-08.

</code_context>

<specifics>
## Specific Ideas

- **Concrete-examples block in 100-PLAN.md** — the planner should embed the
  six examples from D-01 (with and without group, for all 3 phase types)
  as the acceptance-test cases. Each row maps directly to one
  `@ParameterizedTest` `@CsvSource` entry if D-13's optional pure-unit test
  is added.

- **Existing fixtures keep their spirit, just refresh names** — the
  `md1-home-vs-away` style literal in `DiscordChannelServiceWireMockIT`
  becomes `md1-rs-home-vs-away` (no group, since the test's match is
  league-layout). The test's WireMock route stays the same; only the JSON
  payload changes. The mechanical diff is small per file but spans 8 files.

- **Slugify edge cases (already covered by D-06)** — multiple consecutive
  separators collapse to one (`"Group  A"` → `group-a`); leading/trailing
  punctuation trimmed (`" Group A!"` → `group-a`); ASCII-decompose handles
  umlauts (`"Über-Liga"` → `uber-liga`). Empty-after-slugify is the
  defensive guard in D-07.

</specifics>

<deferred>
## Deferred Ideas

### v1.14 (or later) backlog
- **Operator-configurable phase-abbreviations** — if a future league wants
  different abbreviations (e.g., `reg` / `pl` / `pla`), introduce 3 fields on
  `DiscordGlobalConfig` + Flyway migration + form-validation. Rejected for
  Phase 100 as YAGNI.

- **`SeasonPhaseGroup.slug` field** — explicit slug per group (instead of
  derived from `name`). Use case: operator wants `"Group A"` to display
  long-form in UI but appear as short `g-a` in Discord. Not needed today;
  derivation rule from D-06 covers all current group names.

- **Bulk-rename action for old-scheme channels** — admin button on
  `/admin/discord-config` that iterates `matches` with `discord_channel_id IS NOT NULL`
  and PATCHes each channel-name to the new scheme. Skipped by D-08 (leave-as-is);
  if operator decides differently post-merge, this is the v1.14 entry point.

- **Diagnostic list of "channels with old naming scheme"** — read-only
  `/admin/discord-config` panel showing which matches' Discord channel names
  don't match the current `channelName(match)` output. Skipped by D-08; same
  v1.14 entry point as above if surfaced later.

### Out-of-scope for Phase 100 (covered by other phases / not project scope)
- **Phase 100 milestone-scope decision** (v1.13 extension vs v1.14 opener) —
  belongs to roadmap / orchestrator. CONTEXT does not lock this; the planner
  decides at `/gsd-plan-phase 100` time based on milestone-state cues
  (PR #130 merge status, `v1.13-MILESTONE-AUDIT.md` close status).

- **Archive-category-naming refactor** — Phase 94 CHAN-03 owns
  `Match Days Archive {year}` / `Match Days Archive {year} ({num})`. Out of
  scope.

- **Forum-thread / announcement-channel naming** — Phase 96 / 97 / 98 scope;
  unaffected by this phase's per-match channel naming.

- **Discord channel-renaming on team-shortName change** — if `Team.shortName`
  is edited after a match-channel is created, the channel keeps its current
  name. Not a Phase-100 regression; was already the case in Phase 94.

</deferred>

---

*Phase: 100-match-day-channel-naming-scheme-phase-prefix-rs-po-pm-option*
*Context gathered: 2026-05-26*
