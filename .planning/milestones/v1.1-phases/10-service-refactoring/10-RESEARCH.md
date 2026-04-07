# Phase 10: Service Refactoring - Research

**Researched:** 2026-04-06
**Domain:** Spring service decomposition, interface extraction, Spring `Map<String, T>` autowiring
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01:** Introduce a `TemplateManageable` interface with `loadTemplate()`, `saveTemplate(String)`, `resetTemplate()`, `hasCustomTemplate()` methods. All graphic services that support template editing implement this interface, including `TeamCardService` (which does not extend `AbstractGraphicService`).

**D-02:** Use Spring's `Map<String, TemplateManageable>` autowiring to build the dispatch map. The map key is the bean name (e.g., `lineupGraphicService`), mapped to a URL-friendly template type string.

**D-03:** Replace all 20 individual save/reset endpoint methods (10 save + 10 reset) with two generic endpoints: `@PostMapping("/{templateType}/save")` and `@PostMapping("/{templateType}/reset")` using `@PathVariable` dispatch into the service map.

**D-04:** Tab parameter for redirects is derived from the `templateType` path variable — the existing tab names already match URL segments. The preview endpoint already uses this generic approach.

**D-05:** Extract `PlayoffBracketViewService` containing bracket view assembly (getBracketView, buildMatchupView, view record types) and `PlayoffSeedingService` containing seeding logic (seedTeam, autoSeedBracket, saveSeed, saveSeedNumbers, getSeedingData).

**D-06:** Shared helper `calculateTeamTotals` stays in the core `PlayoffService` and is injected into `PlayoffBracketViewService` — no duplication.

**D-07:** Remaining matchup lifecycle methods (create, advance winner, delete) stay in `PlayoffService`.

**D-08:** Extract `RaceFormDataService` for form assembly methods (getNewRaceFormData, getRaceFormData, getResultsFormData, toRaceData, populateDrivers, getUsedCarIds, getUsedTrackIds) — read-only, no side effects.

**D-09:** Extract `RaceCalendarService` for calendar methods (createOrUpdateCalendarEvent, resolveEventDuration) — isolates GoogleCalendarService dependency.

**D-10:** Core CRUD (saveRace, saveResults, deleteRace, quickScore, getRaceListData, getRaceDetailData) stays in `RaceService`. Calendar-checks in `getRaceDetailData` resolved by injecting `RaceCalendarService` into `RaceService`.

### Claude's Discretion
- Exact naming of the template type mapping (bean name substring vs. explicit map configuration)
- Internal method distribution when boundary cases arise during implementation
- Test organization for the new services

### Deferred Ideas (OUT OF SCOPE)
None — analysis stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ARCH-03 | TemplateEditorController uses generic Map dispatch instead of 30+ copy-paste blocks | D-01 through D-04; Spring Map autowiring pattern; interface extraction |
| ARCH-04 | PlayoffService bracket-view and seeding logic in separate focused services | D-05 through D-07; dependency analysis; calculateTeamTotals sharing strategy |
| ARCH-05 | RaceService form-data assembly and calendar-event logic in separate focused services | D-08 through D-10; GoogleCalendarService isolation; read-only annotation strategy |
</phase_requirements>

---

## Summary

Phase 10 is a pure refactoring phase — no new features, no schema changes. It decomposes three large classes into focused units using well-established Spring idioms. The locked decisions from the discussion phase provide a complete and internally consistent plan: the interface-plus-map approach for TemplateEditorController, two-service splits for both PlayoffService and RaceService.

The codebase already provides all building blocks. `AbstractMatchdayGraphicService` and `AbstractPlayoffRoundGraphicService` already implement the exact four methods that will form the `TemplateManageable` contract (`loadTemplate`, `saveTemplate`, `resetTemplate`, `hasCustomTemplate`). The preview endpoint in `TemplateEditorController` already proves the `@PathVariable` dispatch pattern works. Spring's `Map<String, T>` autowiring is standard and requires no extra configuration.

The main implementation risk is the template-type-to-bean-name mapping: the URL path segments (e.g., `team-cards`, `power-rankings`) do not directly match Spring bean names (e.g., `teamCardService`, `powerRankingsGraphicService`). This mapping must be explicit. The planner should treat this as a hardcoded `Map<String, String>` constant in `TemplateEditorController` or a `@Component`-registered map, not an auto-derived convention.

**Primary recommendation:** Implement the three refactoring streams as independent tasks in a single wave. Each stream touches a disjoint set of classes and tests — there are no cross-stream dependencies.

---

## Standard Stack

No new library dependencies are introduced. This phase uses only what is already present.

| Component | Version | Already in Project |
|-----------|---------|-------------------|
| Spring Framework | 6.x (via Spring Boot 4.x) | Yes |
| `Map<String, T>` bean autowiring | Spring core feature | Yes — used implicitly in preview |
| Java interface extraction | Language feature | Yes |
| `@Transactional(readOnly = true)` | Spring TX | Yes — used throughout domain services |
| Lombok `@RequiredArgsConstructor` / `@Slf4j` | Lombok | Yes |

**No `npm install` or new `pom.xml` dependencies needed.**

---

## Architecture Patterns

### Recommended Project Structure (new files only)

```
src/main/java/org/ctc/
├── admin/
│   └── service/
│       └── TemplateManageable.java        # New interface (ARCH-03)
└── domain/
    └── service/
        ├── PlayoffBracketViewService.java  # New (ARCH-04)
        ├── PlayoffSeedingService.java      # New (ARCH-04)
        ├── RaceFormDataService.java        # New (ARCH-05)
        └── RaceCalendarService.java        # New (ARCH-05)
```

### Pattern 1: TemplateManageable Interface (ARCH-03)

**What:** An interface extracted from the existing `loadTemplate` / `saveTemplate` / `resetTemplate` / `hasCustomTemplate` methods already present in `AbstractMatchdayGraphicService` and `AbstractPlayoffRoundGraphicService`.

**Contract (verified from source):**
```java
// Source: AbstractMatchdayGraphicService lines 161-188
public interface TemplateManageable {
    String loadTemplate() throws IOException;
    void saveTemplate(String content) throws IOException;
    void resetTemplate() throws IOException;
    boolean hasCustomTemplate();
}
```

All four abstract graphic service implementations already have these methods with matching signatures. `TeamCardService` has identical method bodies (verified at lines 161-187 of `AbstractMatchdayGraphicService` and `TeamCardService` lines 37-80+).

### Pattern 2: Spring Map<String, TemplateManageable> Autowiring (ARCH-03)

**What:** Spring auto-collects all `@Bean`/`@Service` instances implementing `TemplateManageable` into a `Map<String, TemplateManageable>` keyed by bean name. [VERIFIED: standard Spring feature]

```java
// In TemplateEditorController — replaces 11 individual @Autowired fields
@RequiredArgsConstructor
public class TemplateEditorController {
    // Spring injects: {"teamCardService" -> teamCardService, "lineupGraphicService" -> ..., etc.}
    private final Map<String, TemplateManageable> templateServices;
    private final TemplatePreviewService templatePreviewService;

    // Explicit mapping: URL path segment -> bean name
    private static final Map<String, String> TEMPLATE_TYPE_TO_BEAN = Map.of(
        "team-cards",         "teamCardService",
        "lineup",             "lineupGraphicService",
        "settings",           "settingsGraphicService",
        "race-results",       "resultsGraphicService",
        "match-results",      "matchResultsGraphicService",
        "matchday-overview",  "matchdayOverviewGraphicService",
        "matchday-schedule",  "matchdayScheduleGraphicService",
        "matchday-results",   "matchdayResultsGraphicService",
        "overlay",            "overlayGraphicService",
        "power-rankings",     "powerRankingsGraphicService"
    );
}
```

**Key insight:** Spring uses the method parameter name or field name as the map key when using `@RequiredArgsConstructor`. Because `Map<String, TemplateManageable>` is a generic type, Spring collects ALL beans implementing `TemplateManageable`, with bean names as keys. This is deterministic and does not require `@Qualifier`. [VERIFIED: Spring Framework docs, standard idiom]

### Pattern 3: Generic Save/Reset Endpoints (ARCH-03)

```java
@PostMapping("/{templateType}/save")
public String save(@PathVariable String templateType,
                   @RequestParam String template,
                   RedirectAttributes redirectAttributes) {
    String beanName = TEMPLATE_TYPE_TO_BEAN.get(templateType);
    if (beanName == null) {
        redirectAttributes.addFlashAttribute("errorMessage", "Unknown template type");
        return "redirect:/admin/tools/template-editors";
    }
    try {
        templateServices.get(beanName).saveTemplate(template);
        redirectAttributes.addFlashAttribute("successMessage", "Template saved");
    } catch (Exception e) {
        redirectAttributes.addFlashAttribute("errorMessage", "Save failed: " + e.getMessage());
    }
    return "redirect:/admin/tools/template-editors?tab=" + templateType;
}
```

The redirect URL uses `templateType` directly as the tab parameter — this already works because all existing redirect targets use the same URL segments as the path variables (verified from current controller, lines 143, 166, 193, etc.).

### Pattern 4: PlayoffService Split (ARCH-04)

**What:** Extract two new services from `PlayoffService` (621 lines). Core `PlayoffService` retains matchup lifecycle and acts as the coordinator.

**PlayoffBracketViewService** — takes `PlayoffService` as a constructor dependency for `calculateTeamTotals`:
```java
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayoffBracketViewService {
    private final PlayoffRepository playoffRepository;
    private final RaceRepository raceRepository;
    private final PlayoffSeedRepository playoffSeedRepository;
    private final ScoringService scoringService;   // needed for calculateTeamTotals logic

    public PlayoffService.PlayoffBracketView getBracketView(UUID playoffId) { ... }
    // private: buildMatchupView, calculateTeamTotals (duplicated or delegated)
}
```

**Design choice for `calculateTeamTotals`:** Decision D-06 says it stays in `PlayoffService` and gets injected into `PlayoffBracketViewService`. However, `calculateTeamTotals` is a private method in `PlayoffService`. The planner must choose one of:
- Make it `package-private` or extract it to `ScoringService` (preferred — it only uses `ScoringService.isDriverInTeam`)
- Duplicate the 8-line helper in both services (acceptable given its simplicity)

The method is 8 lines and only calls `scoringService.isDriverInTeam()`. Duplicating is safer than creating a circular dependency. [ASSUMED — final call is Claude's discretion per CONTEXT.md]

**PlayoffSeedingService** — fully self-contained, no circular dep risk:
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayoffSeedingService {
    private final PlayoffRepository playoffRepository;
    private final PlayoffMatchupRepository playoffMatchupRepository;
    private final PlayoffSeedRepository playoffSeedRepository;
    private final TeamRepository teamRepository;
    private final PlayoffBracketViewService bracketViewService;
    private final EntityManager entityManager;

    // seedTeam, autoSeedBracket, saveSeed, saveSeedNumbers, getSeedingData
}
```

**PlayoffController** will need to inject `PlayoffService`, `PlayoffBracketViewService`, and `PlayoffSeedingService`.

**Record migration:** `PlayoffService` inner records (`SeedEntry`, `PlayoffListData`, `SeedingData`, `MatchupDetailData`, `PlayoffBracketView`, `RoundView`, `MatchupView`, `LegView`) should move to their primary owner service to avoid cross-service record references. `PlayoffBracketView`, `RoundView`, `MatchupView`, `LegView` → `PlayoffBracketViewService`. `SeedEntry`, `SeedingData` → `PlayoffSeedingService`. `PlayoffListData`, `MatchupDetailData` → `PlayoffService`. [ASSUMED — planner should confirm record placement]

**Controller impact:** `PlayoffController` currently only injects `PlayoffService`. After the split it will inject all three. References like `data.bracketView()` (from `PlayoffListData`) still work because `PlayoffListData` stays in `PlayoffService`.

### Pattern 5: RaceService Split (ARCH-05)

**RaceFormDataService** — read-only, no `@Transactional` write operations:
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class RaceFormDataService {
    private final RaceRepository raceRepository;
    private final MatchdayRepository matchdayRepository;
    private final TeamRepository teamRepository;
    private final CarRepository carRepository;
    private final TrackRepository trackRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final RaceLineupRepository raceLineupRepository;

    // getNewRaceFormData, getRaceFormData, getResultsFormData,
    // toRaceData, populateDrivers, getUsedCarIds, getUsedTrackIds
}
```

**RaceCalendarService** — isolates the Google Calendar dependency:
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class RaceCalendarService {
    private final RaceRepository raceRepository;
    private final GoogleCalendarService googleCalendarService;

    // createOrUpdateCalendarEvent, resolveEventDuration
}
```

**RaceService after split** — injects `RaceCalendarService` for `getRaceDetailData` calendar-check fields:
```java
public class RaceService {
    // Removed: GoogleCalendarService (replaced by RaceCalendarService injection)
    // Added: RaceCalendarService raceCalendarService
    // Removed: TeamCardService (already accessed indirectly via raceDetailData)
    // Kept: TeamCardService (used in getRaceDetailData for card existence checks)
}
```

**RaceController** currently only injects `RaceService`. After split, `RaceController` also injects `RaceFormDataService` and `RaceCalendarService` for the calendar-event endpoint. Alternatively, `RaceService` can proxy these calls — but that defeats the purpose of extraction. The cleaner approach: `RaceController` injects all three services directly. [ASSUMED — planner confirms]

**Record migration:** `RaceService` inner records (`RaceData`, `RaceResultData`, `RaceListData`, `RaceDetailData`, `ResultsFormData`, `RaceFormData`, `SaveResult`) stay in `RaceService` since they are used across the split services and `RaceController` references them by `RaceService.RaceFormData` etc. Moving them would require updating all callers. [VERIFIED by inspecting record usage in RaceController]

### Anti-Patterns to Avoid

- **Circular dependency:** `PlayoffBracketViewService` must NOT inject `PlayoffService` (which injects `PlayoffBracketViewService`). Use `ScoringService` directly for `calculateTeamTotals`.
- **God record class:** Do not create a shared `PlayoffDtos` class. Keep records as inner types of their primary service.
- **Controller business logic creep:** When controllers gain new service injections, they must remain thin — no logic, just delegation.
- **Breaking existing URLs:** No endpoint path changes are allowed (CLAUDE.md constraint). The new generic endpoints `/{templateType}/save` and `/{templateType}/reset` replace old specific paths but keep the same URL structure.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Dispatching by string key to service | Custom factory/registry class | Spring `Map<String, TemplateManageable>` autowiring |
| URL-to-service resolution | Switch/if-else chains | `TEMPLATE_TYPE_TO_BEAN` constant map + map lookup |
| Service-level read-only transactions | Manual session management | `@Transactional(readOnly = true)` on extracted service methods |

---

## Common Pitfalls

### Pitfall 1: URL Path vs Bean Name Mismatch in TEMPLATE_TYPE_TO_BEAN
**What goes wrong:** `templateType` path variable is `"power-rankings"` but bean name is `"powerRankingsGraphicService"` — direct conversion fails.
**Why it happens:** Spring bean names follow camelCase Java conventions; URL paths use kebab-case.
**How to avoid:** Use an explicit `TEMPLATE_TYPE_TO_BEAN` constant map (shown above) — never attempt auto-conversion.
**Warning signs:** `NullPointerException` on `templateServices.get(beanName)`.

### Pitfall 2: Circular Dependency with calculateTeamTotals
**What goes wrong:** `PlayoffBracketViewService` injects `PlayoffService` to call `calculateTeamTotals`, but `PlayoffService` also injects `PlayoffBracketViewService` for `getBracketView`. Spring throws `BeanCurrentlyInCreationException`.
**Why it happens:** D-06 says the helper "stays in PlayoffService and is injected" — but this creates a circular dep if the direction is PlayoffBracketViewService → PlayoffService.
**How to avoid:** Either (a) duplicate the 8-line helper in `PlayoffBracketViewService` using the already-available `ScoringService.isDriverInTeam`, or (b) extract the helper to a static utility method. Do NOT inject PlayoffService into PlayoffBracketViewService.
**Warning signs:** Spring context fails to start with circular dependency error.

### Pitfall 3: Test URL Mismatch After Controller Refactoring
**What goes wrong:** Existing `TemplateEditorControllerTest` tests specific endpoint paths like `/admin/tools/template-editors/team-cards/reset`. After refactoring to `/{templateType}/reset`, these paths still work (they resolve to the generic handler) — but any test asserting the redirect URL `?tab=team-cards` must still pass.
**Why it happens:** The redirect now uses the `templateType` path variable directly. If the path variable is `team-cards`, the redirect is `?tab=team-cards` — which matches existing test expectations.
**How to avoid:** Run the full test suite (`./mvnw verify`) after each stream is complete.
**Warning signs:** `TemplateEditorControllerTest` failures on redirect assertions.

### Pitfall 4: Missing @Transactional on Extracted RaceFormDataService Methods
**What goes wrong:** OSIV is enabled, so lazy loading works in templates. But `RaceFormDataService` methods access lazy-loaded associations. Without a transaction, these may fail in edge cases (especially in tests where OSIV may not be active).
**Why it happens:** Methods moved from `RaceService` had implicit transaction support from the service-level `@Transactional`. Extracted service has no class-level annotation.
**How to avoid:** Add `@Transactional(readOnly = true)` to methods in `RaceFormDataService` that load entities and traverse associations.

### Pitfall 5: Record Types Stranded in Wrong Service
**What goes wrong:** `PlayoffController` references `PlayoffService.SeedingData` but `SeedingData` was moved to `PlayoffSeedingService`. Compilation fails.
**Why it happens:** Inner record types are tied to their enclosing class by `OuterClass.RecordName` syntax.
**How to avoid:** Decide record ownership before writing code. Map to planner as explicit task: "Record types stay in PlayoffService unless their primary method is fully extracted."

### Pitfall 6: RaceController Requires New Injections
**What goes wrong:** After splitting RaceService, the controller's `raceService.createOrUpdateCalendarEvent(raceId)` call no longer exists on `RaceService` — compilation error.
**Why it happens:** The calendar endpoint in `RaceController` delegates to `RaceService`, which will no longer hold calendar logic.
**How to avoid:** Update `RaceController` to inject `RaceCalendarService` directly and call it for calendar endpoints.

---

## Code Examples

### TemplateManageable Interface (to create)
```java
// Package: org.ctc.admin.service
// Source: derived from AbstractMatchdayGraphicService lines 161-188 [VERIFIED]
public interface TemplateManageable {
    String loadTemplate() throws IOException;
    void saveTemplate(String content) throws IOException;
    void resetTemplate() throws IOException;
    boolean hasCustomTemplate();
}
```

### Existing Index Method — How to Refactor (ARCH-03)
```java
// Current: 10 try-catch blocks (lines 43-132 of TemplateEditorController)
// After: single loop over templateServices
@GetMapping
public String index(@RequestParam(defaultValue = "team-cards") String tab, Model model) {
    for (var entry : TEMPLATE_TYPE_TO_BEAN.entrySet()) {
        String typeKey = entry.getKey();
        String beanName = entry.getValue();
        TemplateManageable svc = templateServices.get(beanName);
        try {
            // Model attribute names derived from typeKey (e.g., "team-cards" -> "teamCardsTemplate")
            // OR keep existing attribute names via a second mapping constant
            String attrBase = beanNameToAttrPrefix(typeKey); // e.g., "team-cards" -> "teamCard"
            model.addAttribute(attrBase + "Template", svc.loadTemplate());
            model.addAttribute(attrBase + "IsCustom", svc.hasCustomTemplate());
        } catch (Exception e) {
            // set empty + first errorMessage wins
        }
    }
    model.addAttribute("activeTab", tab);
    return "admin/template-editors";
}
```

**Note:** The Thymeleaf template `admin/template-editors.html` references model attributes by name (e.g., `teamCardTemplate`, `lineupIsCustom`). The index method must produce the same attribute names as before, or the template must be updated in sync. The planner must either keep the same attribute naming via a three-way mapping (typeKey → attrPrefix), or do a loop-less refactor only for save/reset endpoints. [ASSUMED — safest approach is to keep the index method loop-free or add the attr prefix mapping]

### Spring Map Autowiring (verified Spring idiom)
```java
// Spring collects all beans implementing TemplateManageable into this map
// Keys = Spring bean names (e.g., "teamCardService", "lineupGraphicService")
// This field MUST be named "templateServices" (or any name) — Spring resolves by type + generic
@RequiredArgsConstructor
public class TemplateEditorController {
    private final Map<String, TemplateManageable> templateServices;
    // ^ Spring injects: Map keyed by bean name, value is the TemplateManageable bean
}
```
[VERIFIED: standard Spring Framework behavior for `Map<String, T>` injection]

---

## Runtime State Inventory

Step 2.5: SKIPPED — this is not a rename/refactor/migration phase. No stored data or runtime state is affected. All changes are code-only refactoring with identical observable behavior.

---

## Environment Availability

Step 2.6: SKIPPED — no new external dependencies. The phase uses only existing Java/Spring/Lombok toolchain and existing project test infrastructure.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito 5.x + MockMvc |
| Config file | `pom.xml` (Surefire + Failsafe) |
| Quick run command | `./mvnw test` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ARCH-03 | Generic save endpoint dispatches to correct service via templateType | Integration (MockMvc) | `./mvnw test -pl . -Dtest=TemplateEditorControllerTest` | Yes (update existing) |
| ARCH-03 | Invalid templateType returns redirect with errorMessage | Integration (MockMvc) | same | Yes (add test) |
| ARCH-03 | GET index still loads all 10 template model attributes | Integration (MockMvc) | same | Yes (existing covers partially) |
| ARCH-04 | PlayoffBracketViewService.getBracketView returns correct view | Unit (Mockito) | `./mvnw test -Dtest=PlayoffBracketViewServiceTest` | No — Wave 0 |
| ARCH-04 | PlayoffSeedingService.autoSeedBracket correctly places teams | Integration | `./mvnw test -Dtest=PlayoffServiceTest` | Partial (existing PlayoffServiceTest covers behavior) |
| ARCH-04 | PlayoffController still works end-to-end after split | Integration (MockMvc) | `./mvnw test -Dtest=PlayoffControllerTest` | Yes (update injections) |
| ARCH-05 | RaceFormDataService.getNewRaceFormData returns populated form | Unit (Mockito) | `./mvnw test -Dtest=RaceFormDataServiceTest` | No — Wave 0 |
| ARCH-05 | RaceCalendarService.createOrUpdateCalendarEvent delegates to GoogleCalendarService | Unit (Mockito) | `./mvnw test -Dtest=RaceCalendarServiceTest` | No — Wave 0 |
| ARCH-05 | RaceController calendar endpoint still works after split | Integration (MockMvc) | `./mvnw test -Dtest=RaceControllerTest` | Yes (update if needed) |

### Sampling Rate
- **Per task commit:** `./mvnw test`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** `./mvnw verify -Pe2e` (full suite including Playwright E2E)

### Wave 0 Gaps
- [ ] `src/test/java/org/ctc/domain/service/PlayoffBracketViewServiceTest.java` — covers ARCH-04 bracket view assembly
- [ ] `src/test/java/org/ctc/domain/service/PlayoffSeedingServiceTest.java` — covers ARCH-04 seeding logic
- [ ] `src/test/java/org/ctc/domain/service/RaceFormDataServiceTest.java` — covers ARCH-05 form data assembly
- [ ] `src/test/java/org/ctc/domain/service/RaceCalendarServiceTest.java` — covers ARCH-05 calendar delegation

---

## Security Domain

`security_enforcement` key not present in `.planning/config.json` — treated as enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No — admin-only app, no auth changes in this phase | — |
| V3 Session Management | No | — |
| V4 Access Control | No — no new endpoints exposed publicly | — |
| V5 Input Validation | Yes — `templateType` PathVariable from user input | Allowlist via `TEMPLATE_TYPE_TO_BEAN` lookup; null check before dispatch |
| V6 Cryptography | No | — |

### Known Threat Patterns for Path Variable Dispatch

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Arbitrary templateType injection | Tampering | `TEMPLATE_TYPE_TO_BEAN.get()` returns null for unknown types; explicit null check before dispatch; redirect with errorMessage |
| Template content injection (existing) | Already mitigated | Existing `TemplatePreviewService.TemplateSecurityException` mechanism unchanged |

**The allowlist approach (TEMPLATE_TYPE_TO_BEAN map) is the correct mitigation.** Any `templateType` not in the map results in a safe redirect with an error message, not a service call.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `calculateTeamTotals` should be duplicated in `PlayoffBracketViewService` (using `ScoringService` directly) rather than injecting `PlayoffService` | Architecture Patterns, Pattern 4 | Circular dependency at Spring startup if PlayoffService↔PlayoffBracketViewService injection attempted instead |
| A2 | The `index` GET method in `TemplateEditorController` should use the same model attribute names as before (requiring a typeKey→attrPrefix mapping or partial loop approach) | Code Examples | Thymeleaf template rendering failures if model attribute names change |
| A3 | Inner record types in `PlayoffService` that are also used by new services should remain in `PlayoffService` to avoid cascading reference changes | Architecture Patterns, Pattern 4 | Compilation errors across controllers and tests if records move without full search-and-replace |
| A4 | `RaceController` will inject `RaceCalendarService` directly for the calendar event endpoint | Architecture Patterns, Pattern 5 | Compilation error if not addressed; RaceService no longer holds calendar logic |

---

## Open Questions

1. **Index method model attribute naming strategy**
   - What we know: Template `admin/template-editors.html` uses attribute names like `teamCardTemplate`, `lineupIsCustom`, `powerRankingsTemplate`, etc.
   - What's unclear: Whether the planner should (a) keep a third mapping constant `templateType → modelAttrPrefix`, or (b) leave the index method loop-free (10 try-catch blocks unchanged) and only refactor save/reset endpoints.
   - Recommendation: Option (b) is lower risk — only save/reset endpoints are the problem (20 methods). The GET index has no duplication issue (it already has distinct model attribute names per service). Confirm with planner.

2. **`PlayoffListData.bracketView()` field after split**
   - What we know: `PlayoffListData` record in `PlayoffService` holds a `PlayoffBracketView` (to be moved to `PlayoffBracketViewService`). The record type would need to reference the other service's inner type.
   - What's unclear: Whether `PlayoffBracketView` and related view records move to `PlayoffBracketViewService` or stay in `PlayoffService` as shared types.
   - Recommendation: Move `PlayoffBracketView`, `RoundView`, `MatchupView`, `LegView` to `PlayoffBracketViewService`. Update `PlayoffListData` to use `PlayoffBracketViewService.PlayoffBracketView`. Update `PlayoffController` and `PlayoffServiceTest` references.

---

## Sources

### Primary (HIGH confidence)
- `TemplateEditorController.java` (lines 1-380) — current 20 save/reset methods verified by reading source
- `AbstractMatchdayGraphicService.java` (lines 161-188) — loadTemplate/saveTemplate/resetTemplate/hasCustomTemplate contract verified
- `AbstractPlayoffRoundGraphicService.java` (lines 26-60+) — same interface pattern confirmed
- `PlayoffService.java` (lines 1-621) — all three responsibility clusters identified by reading source
- `RaceService.java` (lines 1-525) — calendar and form-data clusters identified by reading source
- `RaceController.java` (lines 1-60+) — only injects `RaceService`; will need new injections post-split
- `PlayoffController.java` (lines 1-80+) — only injects `PlayoffService`; will need new injections post-split
- `CONVENTIONS.md` — service/naming conventions verified
- `TESTING.md` — test framework, JaCoCo exclusions, test patterns verified
- `.planning/config.json` — `nyquist_validation: true` confirmed

### Secondary (MEDIUM confidence)
- Spring Framework docs (training knowledge): `Map<String, T>` autowiring collects all beans by type, keyed by bean name — standard Spring 5+ behavior

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new libraries; all patterns from existing code
- Architecture: HIGH — all decisions locked in CONTEXT.md; source code verified
- Pitfalls: HIGH — derived from direct source inspection, not assumptions
- Test gaps: HIGH — test file existence verified via Glob tool

**Research date:** 2026-04-06
**Valid until:** 2026-05-06 (stable codebase, no external dependencies)
