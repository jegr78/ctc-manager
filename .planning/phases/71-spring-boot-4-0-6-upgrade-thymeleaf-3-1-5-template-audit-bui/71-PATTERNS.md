# Phase 71: Spring Boot 4.0.6 Upgrade + Thymeleaf 3.1.5 Template Audit + Build Guard — Pattern Map

**Mapped:** 2026-05-11
**Active branch:** `gsd/v1.10-platform-and-backup` (read-only analysis)
**Files analyzed:** 33 (1 pom + 17 broken templates + 2 layouts + 9 controllers/methods + 3 sitegen beans + 1 new IT + 1 new fixture)
**Analogs found:** 33 / 33 (100 % — every change has a close analog in-tree)

> **Audit result (corrects `<decisions>` D-02/D-03):** The full grep
> `th:(replace|insert|include)=".*\(.*\$\{.*\}.*\)"` over `src/main/resources/templates/`
> returned **17 lines across 16 files** (13 admin + 3 site + 1 fragment-call inside `site/matchday.html`),
> NOT 6+3=9 as referenced in CONTEXT.md. The plan must cover every line. Concrete list in
> § File Classification below.

---

## File Classification

### Admin templates with `${...}` in fragment-parameter position (13)

| Target file | Role | Data flow | Closest analog | Match |
|---|---|---|---|---|
| `src/main/resources/templates/admin/match-scoring-form.html` | template | server-render | `admin/match-scoring-list.html` (uses no fragment-param expr) | exact |
| `src/main/resources/templates/admin/race-scoring-form.html` | template | server-render | sibling of `match-scoring-form.html` | exact |
| `src/main/resources/templates/admin/season-phase-form.html` | template | server-render | sibling of `match-scoring-form.html` | exact |
| `src/main/resources/templates/admin/season-phase-group-form.html` | template | server-render | sibling of `season-phase-form.html` | exact |
| `src/main/resources/templates/admin/race-detail.html` | template | server-render | `admin/race-form.html` (no fragment-param expr) | exact |
| `src/main/resources/templates/admin/season-detail.html` | template | server-render | `admin/seasons.html` (literal title) | exact |
| `src/main/resources/templates/admin/driver-detail.html` | template | server-render | `admin/drivers.html` (literal title) | exact |
| `src/main/resources/templates/admin/team-detail.html` | template | server-render | `admin/teams.html` (literal title) | exact |
| `src/main/resources/templates/admin/driver-merge.html` | template | server-render | sibling of `driver-detail.html` | exact |
| `src/main/resources/templates/admin/matchday-detail.html` | template | server-render | `admin/matchdays.html` (literal title) | exact |

### Site templates with `${...}` in fragment-parameter position (3)

| Target file | Role | Data flow | Closest analog | Match |
|---|---|---|---|---|
| `src/main/resources/templates/site/driver-profile.html` | template | server-render (sitegen) | `site/team-profile.html` (peer offender; both fixed in this phase) | exact |
| `src/main/resources/templates/site/team-profile.html` | template | server-render (sitegen) | `site/driver-profile.html` (peer offender) | exact |
| `src/main/resources/templates/site/matchday.html` | template | server-render (sitegen) | `site/standings.html` (literal-string title — clean pattern) | exact |
| `src/main/resources/templates/site/matchday.html` (line 10, fragment-call to `matchCardBody(${race})`) | template fragment-call | server-render | `site/fragments/match-card.html` itself | partial (see § Translation Notes — this one is a *fragment ARG*, not a layout title; needs different fix) |

### Site templates ALREADY clean (literal-string layout titles — DO NOT TOUCH)

These already use `'Driver Ranking ' + ${season.displayLabel}` etc. **String-concatenation in fragment params IS restricted under Thymeleaf 3.1.5 — they are also broken offenders.** D-04 in CONTEXT.md is wrong: string-concatenation in fragment-call args is restricted, not only ternaries/Elvis/method calls. Pattern-grep audit confirms they were caught by the regex:

- `site/driver-ranking.html` (line 3) — `layout('Driver Ranking ' + ${season.displayLabel}, ...)`
- `site/standings.html` (line 3) — `layout('Standings ' + ${season.displayLabel}, ...)`
- `site/playoff-bracket.html` (line 3) — `layout('Playoffs ' + ${season.displayLabel}, ...)`
- `site/matchdays.html` (line 3) — `layout('Matchdays — ' + ${season.displayLabel}, ...)`

**Planner must decide:** treat these 4 also as in-scope (4 more sitegen beans get `context.setVariable("pageTitle", …)`) OR justify their exclusion. CONTEXT.md D-03 listed only `driver-profile/team-profile/matchday` — the broader regex catches 7 site templates total. Recommend: **fix all 7 site templates** for forward-compat per D-01 ("preemptively, not reactively").

### Admin templates ALREADY broken (string-concat — same regex match)

These are part of the audit's 13 admin offenders (CONTEXT.md only enumerated 6):

- `admin/driver-detail.html` (line 3) — `layout('Driver: ' + ${driver.psnId}, ...)`
- `admin/team-detail.html` (line 3) — `layout('Team: ' + ${team.shortName}, ...)`
- `admin/driver-merge.html` (line 3) — `layout('Merge Driver: ' + ${source.psnId}, ...)`
- `admin/matchday-detail.html` (line 3) — `layout('Matchday: ' + ${matchday.label}, ...)`

### Layouts (2 — Elvis fallback insertion only)

| Target file | Role | Data flow | Closest analog | Match |
|---|---|---|---|---|
| `src/main/resources/templates/admin/layout.html` (line 8 `<title>` tag) | layout template | server-render | itself — single-line edit | exact (self) |
| `src/main/resources/templates/site/layout.html` (line 8 `<title>` tag) | layout template | server-render | `admin/layout.html` peer layout | exact |

### Controllers (9 GET handlers in 7 controllers — add `model.addAttribute("pageTitle", …)`)

| Target controller / method | Role | Data flow | Closest analog | Match |
|---|---|---|---|---|
| `MatchScoringController.create(Model)` (line 31-35) | controller | request-response | sibling `edit(@PathVariable, Model)` line 37-48 | exact |
| `MatchScoringController.edit(UUID, Model)` (line 37-48) | controller | request-response | self pattern | exact |
| `RaceScoringController.create(Model)` (line 31-35) | controller | request-response | sibling `edit` line 37-48 | exact |
| `RaceScoringController.edit(UUID, Model)` (line 37-48) | controller | request-response | self pattern | exact |
| `SeasonPhaseController.create(UUID, PhaseType, Model)` (line 144-164) | controller | request-response | sibling `edit` line 167-192 | exact |
| `SeasonPhaseController.edit(UUID, UUID, Model)` (line 167-192) | controller | request-response | self pattern | exact |
| `SeasonPhaseController.detail(UUID, UUID, Model)` (line 58-99) — `season-detail.html` | controller | request-response | sibling `groupDetail` line 102-141 (same template) | exact |
| `SeasonPhaseController.groupDetail(UUID, UUID, UUID, Model)` (line 102-141) | controller | request-response | self pattern | exact |
| `SeasonController.detail(UUID, Model)` (line 36-60) — fallback empty-state path | controller | request-response | `SeasonPhaseController.detail` peer | exact |
| `SeasonPhaseGroupController.create(UUID, UUID, Model)` (line 41-53) | controller | request-response | sibling `edit` line 55-79 | exact |
| `SeasonPhaseGroupController.edit(UUID, UUID, UUID, Model)` (line 55-79) | controller | request-response | self pattern | exact |
| `RaceController.detail(UUID, Model)` (line 45-68) | controller | request-response | `MatchdayController.detail` peer | exact |
| `DriverController.detail(UUID, Model)` (line 35-40) | controller | request-response | `TeamController.detail` peer | exact |
| `DriverController.mergeForm(UUID, Model)` (line 106-113) | controller | request-response | `DriverController.detail` sibling | exact |
| `DriverController.previewMerge(UUID, UUID, …)` (line 115-131) | controller | request-response | `DriverController.mergeForm` sibling | exact |
| `TeamController.detail(UUID, Model)` (line 32-40) | controller | request-response | `DriverController.detail` peer | exact |
| `MatchdayController.detail(UUID, Model)` (line 52-65) | controller | request-response | `RaceController.detail` peer | exact |

### Sitegen page-generator beans (3 — add `context.setVariable("pageTitle", …)`)

| Target bean / method | Role | Data flow | Closest analog | Match |
|---|---|---|---|---|
| `org.ctc.sitegen.DriverProfilePageGenerator` line 106-126 | sitegen-bean | batch render | self — existing `context.setVariable(...)` block | exact |
| `org.ctc.sitegen.TeamProfilePageGenerator` line 94-202 | sitegen-bean | batch render | `DriverProfilePageGenerator` peer | exact |
| `org.ctc.sitegen.MatchdaysPageGenerator.generateDetails(…)` line 240-266 | sitegen-bean | batch render | `DriverProfilePageGenerator` peer | exact |

**(If planner extends scope to the 4 currently-literal-string-concat site offenders:** also touch `StandingsPageGenerator`, `DriverRankingPageGenerator`, `MatchdaysPageGenerator.generateOverview()` (matchdays.html), and the playoff-bracket generator.)

### Build / Test (3 new files)

| Target file | Role | Data flow | Closest analog | Match |
|---|---|---|---|---|
| `pom.xml` (parent bump + `<dependencyManagement>` + `<plugin>exec-maven-plugin</plugin>` block) | configuration | build | self — existing `<build><plugins>` list 165-265 (jacoco-maven-plugin = closest exec-bound execution analog) | role-match (no exec-maven-plugin precedent in repo; jacoco execution wiring is the closest analog) |
| `src/test/java/org/ctc/admin/controller/integration/TemplateRenderingSmokeIT.java` (NEW) | test (IT) | request-response | `src/test/java/org/ctc/admin/controller/integration/AdminDropdownRenderingIT.java` | **exact — same parameterized-MockMvc + @SpringBootTest + @ActiveProfiles("dev") + @Transactional harness** |
| `src/test/resources/sql/smoke-it-fixture.sql` OR `SmokeITSeeder.java` (NEW) | test-fixture | data seed | `src/test/resources/sql/legacy-season-without-playoff.sql` | **exact — deterministic-UUID fixture pattern, used via `@Sql(scripts = …)` already proven in `LegacyMigratedSeasonE2ETest`** |

---

## Pattern Assignments

### 1. `pom.xml` — Parent bump + Thymeleaf pin + exec-maven-plugin grep gate

**Target path:** `pom.xml` (root)

**Role:** configuration (build-system)

**Analogs:**
- Existing `<parent>` block (line 5-10) — single-line version edit
- Existing `<build><plugins>` list (line 165-265) — `jacoco-maven-plugin` (line 212-264) is the closest analog for a plugin with `<execution>` bound to a specific phase

**Current state — `<parent>` block (line 5-10):**
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.5</version>
    <relativePath/> <!-- lookup parent from repository -->
</parent>
```

**Current state — no `<dependencyManagement>` block exists** (verified by `grep -n "dependencyManagement" pom.xml` → no result).

**Existing plugin-with-phase-execution analog (jacoco — line 234-263):**
```xml
<executions>
    <execution>
        <id>prepare-agent</id>
        <goals><goal>prepare-agent</goal></goals>
    </execution>
    <execution>
        <id>report</id>
        <phase>verify</phase>
        <goals><goal>report</goal></goals>
    </execution>
    <execution>
        <id>check</id>
        <phase>verify</phase>
        <goals><goal>check</goal></goals>
        <configuration>
            <rules>
                <rule>
                    <element>BUNDLE</element>
                    <limits>
                        <limit>
                            <counter>LINE</counter>
                            <value>COVEREDRATIO</value>
                            <minimum>0.82</minimum>
                        </limit>
                    </limits>
                </rule>
            </rules>
        </configuration>
    </execution>
</executions>
```

**Translation notes:**
- Bump `<version>4.0.5</version>` → `<version>4.0.6</version>` (1-line change).
- Add new `<dependencyManagement>` block *after* `</parent>` and *before* `<groupId>org.ctc</groupId>` (around line 11) — or alternatively after `</properties>` and before `<dependencies>`. Either is valid Maven; the `<parent>`-adjacent placement is the conventional location.
- Add new `<plugin>` for `org.codehaus.mojo:exec-maven-plugin` inside `<build><plugins>`, modeled after the jacoco `<execution>` wiring above — but bound to **`validate` phase** (not `verify`) per D-06. Use goal `exec:exec` with executable=`grep`, args=`-r -E -l 'th:(replace|insert|include)=".*\(.*\$\{.*\}.*\)"' src/main/resources/templates/`. **Critical:** `grep` returns exit 1 when no match (= pass for our use case) and exit 0 when match found (= fail). Invert via shell-wrapper (`bash -c 'if grep … ; then exit 1 ; else exit 0 ; fi'`) OR use exec-maven-plugin's `<successCodes>` to declare 1 = success.
- The plugin is **already transitively available** via Spring Boot parent's plugin-management section; no new `<dependency>` is needed (D-17 confirms).

---

### 2. Admin templates — Fragment-parameter expression fix (9 templates analyzed below; 4 string-concat offenders follow identical pattern)

#### 2a. `admin/match-scoring-form.html` (line 3)

**Current broken line:**
```html
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout(${matchScoringForm.id != null ? 'Edit Match-Scoring' : 'New Match-Scoring'}, ~{::section})}">
```

**Corrected line:**
```html
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout(${pageTitle}, ~{::section})}">
```

**Translation notes:** Drop the inline ternary; controller now supplies `pageTitle`. Inner `<h1 th:text="${matchScoringForm.id != null ? 'Edit Match-Scoring' : 'New Match-Scoring'}"></h1>` (line 7) MAY remain — it is inside template body, not a fragment-call argument, so Thymeleaf 3.1.5 restricted mode does NOT apply. Optional cleanup: replace with `th:text="${pageTitle}"` for DRY.

#### 2b. `admin/race-scoring-form.html` (line 3) — analogous

**Broken:**
```html
th:replace="~{admin/layout :: layout(${raceScoringForm.id != null ? 'Edit Race-Scoring' : 'New Race-Scoring'}, ~{::section})}"
```

**Fixed:** `layout(${pageTitle}, ~{::section})`. Translation notes identical to 2a.

#### 2c. `admin/season-phase-form.html` (line 3)

**Broken:**
```html
th:replace="~{admin/layout :: layout(${seasonPhaseForm.id != null ? 'Edit Phase' : 'New Phase'}, ~{::section})}"
```

**Fixed:** `layout(${pageTitle}, ~{::section})`. Controller pageTitle value: `(form.getId() != null ? "Edit Phase" : "New Phase") + " — " + season.getName()` (mirrors current `<h1>` text on line 7, which uses string-concat — that line stays untouched because it's NOT a fragment-call arg).

#### 2d. `admin/season-phase-group-form.html` (line 3)

**Broken:**
```html
th:replace="~{admin/layout :: layout(${form.id != null ? 'Edit Group' : 'New Group'}, ~{::section})}"
```

**Fixed:** `layout(${pageTitle}, ~{::section})`. Controller pageTitle: `form.getId() != null ? "Edit Group" : "New Group"`. Inner `<h1>` line 7 retains its rich string (which uses `phase.label` Elvis fallback) — that lives inside the body, not in a fragment-call arg.

#### 2e. `admin/race-detail.html` (line 3) — Elvis + string-concat offender

**Broken:**
```html
th:replace="~{admin/layout :: layout('Race: ' + ${race.homeTeam.shortName} + ' vs ' + ${race.awayTeam?.shortName ?: 'Bye'}, ~{::section})}"
```

**Fixed:** `layout(${pageTitle}, ~{::section})`. Controller pageTitle: `"Race: " + race.getHomeTeam().getShortName() + " vs " + (race.getAwayTeam() != null ? race.getAwayTeam().getShortName() : "Bye")`.

#### 2f. `admin/season-detail.html` (line 3) — String-concat + nested ternary offender

**Broken:**
```html
th:replace="~{admin/layout :: layout('Season: ' + ${season.name} + (phase != null ? ' — ' + ${effectivePhaseLabel} : ''), ~{::section})}"
```

**Fixed:** `layout(${pageTitle}, ~{::section})`. Controller pageTitle: `"Season: " + season.getName() + (phase != null ? " — " + effectivePhaseLabel : "")`. **Note:** `SeasonPhaseController.detail/groupDetail` BOTH render this template AND need to set pageTitle; `SeasonController.detail` (empty-state fallback path, line 42-57) also needs pageTitle = `"Season: " + season.getName()` (no phase context in that branch).

#### 2g. `admin/driver-detail.html` (line 3) — String-concat offender

**Broken:**
```html
th:replace="~{admin/layout :: layout('Driver: ' + ${driver.psnId}, ~{::section})}"
```

**Fixed:** `layout(${pageTitle}, ~{::section})`. Controller pageTitle (in `DriverController.detail`, line 35-40): `"Driver: " + driver.getPsnId()`.

#### 2h. `admin/team-detail.html` (line 3) — String-concat offender

**Broken:**
```html
th:replace="~{admin/layout :: layout('Team: ' + ${team.shortName}, ~{::section})}"
```

**Fixed:** `layout(${pageTitle}, ~{::section})`. Controller pageTitle (in `TeamController.detail`, line 32-40): `"Team: " + team.getShortName()`.

#### 2i. `admin/driver-merge.html` (line 3)

**Broken:**
```html
th:replace="~{admin/layout :: layout('Merge Driver: ' + ${source.psnId}, ~{::section})}"
```

**Fixed:** `layout(${pageTitle}, ~{::section})`. Controller pageTitle (set in BOTH `DriverController.mergeForm` line 106-113 AND `previewMerge` line 115-131): `"Merge Driver: " + source.getPsnId()`.

#### 2j. `admin/matchday-detail.html` (line 3)

**Broken:**
```html
th:replace="~{admin/layout :: layout('Matchday: ' + ${matchday.label}, ~{::section})}"
```

**Fixed:** `layout(${pageTitle}, ~{::section})`. Controller pageTitle (in `MatchdayController.detail` line 52-65): `"Matchday: " + matchday.getLabel()`.

---

### 3. Site templates — Fragment-parameter expression fix (3 explicitly in scope + 4 string-concat offenders surfaced by audit)

#### 3a. `site/driver-profile.html` (line 3) — plain `${var}` (allowed under 3.1.5 but fixed preemptively per D-04)

**Broken:** `th:replace="~{site/layout :: layout(${driver.psnId}, ~{::section})}"`
**Fixed:** `th:replace="~{site/layout :: layout(${pageTitle}, ~{::section})}"`

#### 3b. `site/team-profile.html` (line 3)

**Broken:** `th:replace="~{site/layout :: layout(${team.name}, ~{::section})}"`
**Fixed:** `th:replace="~{site/layout :: layout(${pageTitle}, ~{::section})}"`

#### 3c. `site/matchday.html` (line 3 — title) AND (line 10 — fragment ARG, different fix)

**Broken line 3 (title):** `th:replace="~{site/layout :: layout(${matchday.label}, ~{::section})}"`
**Fixed line 3:** `th:replace="~{site/layout :: layout(${pageTitle}, ~{::section})}"`

**Broken line 10 (fragment ARG, NOT a layout title):**
```html
<th:block th:insert="~{site/fragments/match-card :: matchCardBody(${race})}"></th:block>
```

**Translation note for line 10:** This is a different shape — the `${race}` is a fragment argument inside a `th:each="race : ${races}"` loop. The `pageTitle` pattern does NOT apply. Two fix options:
1. **Use an iteration-variable reference:** Thymeleaf 3.1.5 allows iteration vars as fragment args via parameter shorthand; verify with a small spike. If it works: `matchCardBody(race)` (drop `${...}` wrapper).
2. **Inline the fragment body** instead of using parameterized fragment call.

Plan should explicitly choose option 1 if Thymeleaf 3.1.5 accepts unwrapped iteration-var refs; option 2 otherwise. **A spike is recommended during planning — this is the ONLY non-trivial fragment-call in the codebase.**

#### 3d. `site/driver-ranking.html`, `site/standings.html`, `site/playoff-bracket.html`, `site/matchdays.html` — string-concat offenders (matched by D-05 broad regex)

All four use the pattern `layout('Some Label ' + ${season.displayLabel}, ~{::section})`. Same fix: rewrite to `layout(${pageTitle}, ~{::section})`. Each requires a corresponding sitegen page-generator bean to set `pageTitle`. **Planner decision required:** scope these in (consistent with D-01 "preemptive, max forward-compat") or scope out (CONTEXT.md D-03 only lists 3 site templates).

---

### 4. Layout templates — Elvis fallback at title-rendering point

#### 4a. `admin/layout.html` (line 8)

**Current:**
```html
<title th:text="'CTC Admin - ' + ${title}">CTC Admin</title>
```

**Translation note:** The `title` parameter is supplied via the fragment-call signature `layout(title, content)` (line 2). Once all callers pass `${pageTitle}`, the resolved value flows in via `title`. The Elvis fallback per D-13 is for handlers that forget to set `pageTitle` — when `pageTitle` is null, the fragment-call passes null as `title`, and `'CTC Admin - ' + null` renders as `"CTC Admin - null"`. Fix:

**Corrected:**
```html
<title th:text="'CTC Admin - ' + (${title} ?: 'Home')">CTC Admin</title>
```

OR, per D-13's explicit form, render `${pageTitle}` directly (since `title` parameter IS `${pageTitle}` from caller-side):
```html
<title th:text="${title} ?: 'CTC Admin'">CTC Admin</title>
```

**Critical:** Lines 47-71 also reference `${title}` in `th:classappend` (e.g., `${title.contains('Season') ? 'active' : ''}`). Adding null-safety to `${title}` is REQUIRED to prevent NPE on those lines if pageTitle is null. Either (a) pre-coerce in the layout (`th:with="title=${title ?: 'CTC Admin'}"` at the `<aside>` level) OR (b) rewrite each `th:classappend` to use `${title != null and title.contains(...) ? 'active' : ''}`. Recommend (a) for surgical 1-line change.

#### 4b. `site/layout.html` (line 8)

**Current:** `<title th:text="'CTC - ' + ${title}">Community Team Cup</title>`

**Corrected:** `<title th:text="'CTC - ' + (${title} ?: 'Home')">Community Team Cup</title>` OR `<title th:text="${title} ?: 'CTC'">Community Team Cup</title>` per D-13.

**Translation note:** Site layout does NOT use `${title}` outside line 8, so no `th:classappend` refactor is needed — single-line edit is sufficient.

---

### 5. Admin controllers — Add `model.addAttribute("pageTitle", …)`

#### 5a. `MatchScoringController.create(Model)` (line 31-35)

**Current state — full method:**
```java
@GetMapping("/new")
public String create(Model model) {
    model.addAttribute("matchScoringForm", new MatchScoringForm());
    return "admin/match-scoring-form";
}
```

**Add 1 line before `return`:**
```java
model.addAttribute("pageTitle", "New Match-Scoring");
```

#### 5b. `MatchScoringController.edit(UUID, Model)` (line 37-48)

**Current state — relevant tail:**
```java
@GetMapping("/{id}/edit")
public String edit(@PathVariable UUID id, Model model) {
    var scoring = matchScoringService.findById(id);
    var form = new MatchScoringForm();
    form.setId(scoring.getId());
    // ... field copies ...
    model.addAttribute("matchScoringForm", form);
    return "admin/match-scoring-form";
}
```

**Add 1 line before `return`:**
```java
model.addAttribute("pageTitle", "Edit Match-Scoring");
```

#### 5c. `RaceScoringController.create(Model)` (line 31-35) — analogous

**Add:** `model.addAttribute("pageTitle", "New Race-Scoring");`

#### 5d. `RaceScoringController.edit(UUID, Model)` (line 37-48) — analogous

**Add:** `model.addAttribute("pageTitle", "Edit Race-Scoring");`

#### 5e. `SeasonPhaseController.create(UUID, PhaseType, Model)` (line 144-164)

**Current tail (line 162-164):**
```java
addFormModelAttributes(model, season, form);
return "admin/season-phase-form";
```

**Recommended approach:** add `pageTitle` inside the existing `addFormModelAttributes(...)` helper (line 318-341), keyed off `form.getId()`:
```java
model.addAttribute("pageTitle",
        (form.getId() != null ? "Edit Phase" : "New Phase") + " — " + ((Season) season).getName());
```
This single edit covers BOTH `create` and `edit` (DRY — both already call the same helper).

#### 5f. `SeasonPhaseController.edit(UUID, UUID, Model)` (line 167-192)

Covered by the helper edit above (5e).

#### 5g. `SeasonPhaseController.detail(UUID, UUID, Model)` (line 58-99) — renders `admin/season-detail.html`

**Current tail (lines 95-99):**
```java
model.addAttribute("combinedView", isGroupsLayout);
model.addAttribute("showGroupColumn", isGroupsLayout);

return "admin/season-detail";
```

**Add before `return`:**
```java
model.addAttribute("pageTitle",
        "Season: " + season.getName() + " — " + effectiveLabel(phase));
```

#### 5h. `SeasonPhaseController.groupDetail(...)` (line 102-141) — same template

**Add before `return`:**
```java
model.addAttribute("pageTitle",
        "Season: " + season.getName() + " — " + effectiveLabel(phase));
```

#### 5i. `SeasonController.detail(UUID, Model)` (line 36-60) — empty-state fallback only

This method auto-redirects to the phase URL UNLESS no REGULAR phase exists (line 41 `regular.isEmpty()`). The fallback branch (lines 42-56) renders `admin/season-detail.html` with `phase=null`. **Add before the `return "admin/season-detail";` on line 56:**
```java
model.addAttribute("pageTitle", "Season: " + season.getName());
```

#### 5j. `SeasonPhaseGroupController.create(...)` (line 41-53) and `edit(...)` (line 55-79)

Both render `admin/season-phase-group-form.html`. The form save-error branch (line 95) too. **Add to `create` (before line 52 `return`):**
```java
model.addAttribute("pageTitle", "New Group");
```
**Add to `edit` (before line 78 `return`):**
```java
model.addAttribute("pageTitle", "Edit Group");
```
**Add to `save`'s error branch (before line 95 `return`):**
```java
model.addAttribute("pageTitle", form.getId() != null ? "Edit Group" : "New Group");
```

#### 5k. `RaceController.detail(UUID, Model)` (line 45-68) — renders `admin/race-detail.html`

**Current tail (line 66-68):**
```java
model.addAttribute("canCreateCalendarEvent", data.canCreateCalendarEvent());
return "admin/race-detail";
```

**Add before `return`:**
```java
var race = data.race();
model.addAttribute("pageTitle",
        "Race: " + race.getHomeTeam().getShortName() + " vs "
                + (race.getAwayTeam() != null ? race.getAwayTeam().getShortName() : "Bye"));
```
**Note:** `race` is already added as a model attribute on line 48 — reuse the local. Adjust to:
```java
model.addAttribute("pageTitle",
        "Race: " + data.race().getHomeTeam().getShortName() + " vs "
                + (data.race().getAwayTeam() != null ? data.race().getAwayTeam().getShortName() : "Bye"));
```

#### 5l. `DriverController.detail(UUID, Model)` (line 35-40)

**Add before `return`:**
```java
model.addAttribute("pageTitle", "Driver: " + driver.getPsnId());
```

#### 5m. `DriverController.mergeForm(...)` (line 106-113) and `previewMerge(...)` (line 115-131)

Both render `admin/driver-merge.html`. **Add to `mergeForm` (before line 112 `return`):**
```java
model.addAttribute("pageTitle", "Merge Driver: " + source.getPsnId());
```
**Add to `previewMerge` (before line 125 `return`):**
```java
model.addAttribute("pageTitle", "Merge Driver: " + source.getPsnId());
```

#### 5n. `TeamController.detail(UUID, Model)` (line 32-40)

**Add before `return`:**
```java
model.addAttribute("pageTitle", "Team: " + data.team().getShortName());
```

#### 5o. `MatchdayController.detail(UUID, Model)` (line 52-65)

**Add before `return`:**
```java
model.addAttribute("pageTitle", "Matchday: " + matchday.getLabel());
```

---

### 6. Sitegen page-generator beans — Add `context.setVariable("pageTitle", …)`

#### 6a. `org.ctc.sitegen.DriverProfilePageGenerator` — line 106-126

**Current state (the variable-population block):**
```java
var context = new Context(Locale.ENGLISH);
context.setVariable("season", season);
context.setVariable("driver", driver);
context.setVariable("team", team);
context.setVariable("results", results);
int total = results.stream().mapToInt(r -> r.getPointsTotal()).sum();
context.setVariable("totalRaces", results.size());
context.setVariable("totalPoints", total);
// ... more setVariable calls through line 126 ...
context.setVariable("breadcrumbCurrent", driver.getPsnId());
```

**Add (recommended location: near `breadcrumbCurrent` line 123 since both are display-strings):**
```java
context.setVariable("pageTitle", driver.getPsnId());
```

#### 6b. `org.ctc.sitegen.TeamProfilePageGenerator` — line 94-202

**Current state (start of variable-population block, line 94-97):**
```java
var context = new Context(Locale.ENGLISH);
context.setVariable("season", season);
context.setVariable("team", team);
context.setVariable("standing", teamStanding);
```

**Add immediately after `context.setVariable("breadcrumbCurrent", team.getShortName());` (line 202):**
```java
context.setVariable("pageTitle", team.getName());
```

#### 6c. `org.ctc.sitegen.MatchdaysPageGenerator.generateDetails(...)` — line 240-266

**Current state (line 247-259):**
```java
var context = new Context(Locale.ENGLISH);
context.setVariable("season", season);
context.setVariable("matchday", matchday);
var raceViews = raceRepository.findByMatchdayId(matchday.getId()).stream()
        .map(r -> toRaceView(r, season, "../driver/", allLineups)).toList();
context.setVariable("races", raceViews);

context.setVariable("currentPage", "matchdays");
context.setVariable("seasonSlug", siteSlugger.slugify(season.getDisplayLabel()));
context.setVariable("seasonName", season.getName());
context.setVariable("hasPlayoff", ctx.hasPlayoff());
context.setVariable("playoffSeasonSlug", ctx.playoffSeasonSlug());
context.setVariable("breadcrumbCurrent", matchday.getLabel());
```

**Add immediately after `breadcrumbCurrent` (line 259):**
```java
context.setVariable("pageTitle", matchday.getLabel());
```

**Translation notes for all 3 generators:** Choice of `pageTitle` value should match the existing first-class display variable for that page — `psnId` for driver, `team.getName()` for team, `matchday.getLabel()` for matchday. This mirrors the pre-fix behavior of the broken `${...}` in the template's fragment-call.

---

### 7. NEW: `TemplateRenderingSmokeIT`

**Target path:** `src/test/java/org/ctc/admin/controller/integration/TemplateRenderingSmokeIT.java`

**Role:** test (Failsafe IT)

**Closest analog:** `src/test/java/org/ctc/admin/controller/integration/AdminDropdownRenderingIT.java`

**Analog code excerpt (full harness, ~95 lines):**
```java
package org.ctc.admin.controller.integration;

import org.ctc.TestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AdminDropdownRenderingIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestHelper testHelper;
    @Autowired private SeasonPhaseRepository seasonPhaseRepository;

    private Season season;
    private SeasonPhase regularPhase;

    @BeforeEach
    void setUp() {
        season = testHelper.createSeason("T-DropdownSmoke-" + UUID.randomUUID().toString().substring(0, 6));
        regularPhase = seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR).orElseThrow();
    }

    static Stream<Arguments> dropdownFormUrls() {
        return Stream.of(
                arguments("/admin/seasons/new", "season-form (new)"),
                arguments("/admin/seasons/{sid}/edit", "season-form (edit)"),
                // ...
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("dropdownFormUrls")
    void givenAdminFormUrl_whenGet_thenAllValueBearingOptionsHaveNonEmptyText(
            String urlTemplate, String formName) throws Exception {
        String url = urlTemplate
                .replace("{sid}", season.getId().toString())
                .replace("{pid}", regularPhase.getId().toString());

        var html = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // ... assertion ...
    }
}
```

**Translation notes for `TemplateRenderingSmokeIT`:**
1. **Class-level annotations:** Reuse identical block — `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional`.
2. **Route discovery (D-08):** Replace the hardcoded `dropdownFormUrls()` `MethodSource` with a `@DynamicTest` factory OR a `MethodSource` that introspects `RequestMappingHandlerMapping`:
   ```java
   @Autowired private RequestMappingHandlerMapping handlerMapping;

   Stream<DynamicTest> allAdminGetRoutes() {
       return handlerMapping.getHandlerMethods().entrySet().stream()
               .filter(e -> e.getKey().getMethodsCondition().getMethods().contains(RequestMethod.GET))
               .flatMap(e -> e.getKey().getPatternValues().stream())
               .filter(p -> p.startsWith("/admin"))
               .map(p -> DynamicTest.dynamicTest("GET " + p,
                       () -> assertRouteRenders(p)));
   }
   ```
3. **Path variable resolution (D-10):** Maintain a `Map<String, String>` keyed by path-variable name (`seasonId`, `phaseId`, `groupId`, `raceId`, `matchdayId`, `id` (context-dependent), `driverId`, `teamId`, `matchScoringId`, `raceScoringId`, `targetId`) populated from `@Sql`-seeded fixture UUIDs. Substitute via regex replace on the pattern string.
4. **Assertions (D-11):**
   ```java
   var response = mockMvc.perform(get(resolvedUrl)).andReturn().getResponse();
   assertThat(response.getStatus())
       .as("GET %s must return 200", resolvedUrl)
       .isEqualTo(200);
   assertThat(response.getContentAsString())
       .as("GET %s response must not contain TemplateProcessingException", resolvedUrl)
       .doesNotContain("TemplateProcessingException");
   ```
5. **Seeding (D-09):** Replace `@BeforeEach testHelper.createSeason(...)` with `@Sql(scripts = "/sql/smoke-it-fixture.sql", executionPhase = BEFORE_TEST_METHOD)` on the class OR each test (since `@DynamicTest` factory makes test-method-level `@Sql` tricky, prefer **class-level `@Sql`** loaded once per class). Combine with `@Transactional` rollback.
6. **Naming (per CLAUDE.md Given-When-Then):** Display name for dynamic tests: `givenSmokeFixtureSeeded_whenGet{Route}_thenRendersWithoutTemplateProcessingException`. Factory method name: `givenSmokeFixtureSeeded_whenGetAllAdminRoutes_thenAllRenderWithoutTemplateProcessingException`.

---

### 8. NEW: SmokeIT seeder — `@Sql` script

**Target path:** `src/test/resources/sql/smoke-it-fixture.sql`

**Role:** test-fixture (data seed)

**Closest analog:** `src/test/resources/sql/legacy-season-without-playoff.sql` (91 lines, deterministic-UUID minimal fixture)

**Analog code excerpt (header + first 4 INSERTs, lines 1-31):**
```sql
-- QUAL-03 fixture: legacy migrated season WITHOUT playoff (post-V6 schema).
-- Deterministic UUIDs in the 0000-0061-0000-* range to avoid PK collisions with other fixtures
-- and DevDataSeeder data.

-- Reference data: scoring rows on the phase.
INSERT INTO race_scorings (id, name, race_points, fastest_lap_points, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000001', 'Phase61-Legacy-RaceScoring',
        '25,18,15,12,10,8,6,4,2,1', 0,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO match_scorings (id, name, points_win, points_draw, points_loss, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000002', 'Phase61-Legacy-MatchScoring', 3, 1, 0,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO seasons (id, name, season_year, season_number, active, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000010', 'Test-Legacy-Season-2098', 2098, 1, false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO season_phases (id, season_id, sort_index, phase_type, layout, format, legs,
                           race_scoring_id, match_scoring_id, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000011',
        '00000000-0000-0061-0000-000000000010',
        0, 'REGULAR', 'LEAGUE', 'LEAGUE', 2,
        '00000000-0000-0061-0000-000000000001',
        '00000000-0000-0061-0000-000000000002',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
```

**Loader usage (from `LegacyMigratedSeasonE2ETest.java` line 50-52):**
```java
@Test
@Sql(scripts = "/sql/legacy-season-without-playoff.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
void givenLegacyMigratedSeasonWithoutPlayoff_thenRegularTabOnly() {
    // ... reference SEASON_WITHOUT_PLAYOFF_ID = "00000000-0000-0061-0000-000000000010"
}
```

**Translation notes for `smoke-it-fixture.sql`:**
1. **UUID range:** Use a distinct deterministic range, e.g. `00000000-0000-0071-0000-*` (phase number embedded for traceability — established convention in the legacy fixtures).
2. **Required entities (per D-09):** 1 RaceScoring + 1 MatchScoring + 1 Season + 1 SeasonPhase (REGULAR) + 1 SeasonPhaseGroup + 1 Team + 1 SeasonTeam + 1 PhaseTeam + 1 Driver + 1 SeasonDriver + 1 Matchday + 1 Match + 1 Race + 1 RaceLineup + 1 RaceResult. Mirror the column-list from `legacy-season-without-playoff.sql` to ensure H2 + MariaDB schema compliance.
3. **Prefix discipline (per CLAUDE.md "Isolate Test Data Completely"):** Names prefixed with `T-SMOKE-` / `Test_Smoke_*` / `Test-Smoke-Season 2071`.
4. **No collision with `DevDataSeeder`:** The smoke fixture loads ONLY via `@Sql`, never via the seeder bean. Since `TemplateRenderingSmokeIT` runs with `@ActiveProfiles("dev")` AND `DevDataSeeder` runs at startup on `dev`, the fixture rows must NOT clash with seeder rows (different UUID range guarantees this). **Alternative:** introduce a separate profile `smoke-test` that excludes `DevDataSeeder` — adds complexity, defer to planner.
5. **Path-variable export contract:** Document the UUID constants at the top of the SQL file as a comment block, then mirror them in the test class as `private static final String SEASON_SMOKE_ID = "00000000-0000-0071-0000-000000000010";` etc., used to substitute `{seasonId}`, `{phaseId}`, etc. in route URLs (D-10).

---

## Shared Patterns

### Pattern A: Thin Controller adding a Model attribute (CLAUDE.md "Keep Controllers Thin")

**Source:** `org.ctc.admin.controller.MatchScoringController` (entire file, 78 lines)

**Apply to:** Every controller GET handler that renders one of the 13 admin offender templates.

**Excerpt — the canonical add-one-line shape:**
```java
@GetMapping("/{id}/edit")
public String edit(@PathVariable UUID id, Model model) {
    var scoring = matchScoringService.findById(id);
    var form = new MatchScoringForm();
    // ... DTO copy ...
    model.addAttribute("matchScoringForm", form);
    model.addAttribute("pageTitle", "Edit Match-Scoring");  // <-- the only new line
    return "admin/match-scoring-form";
}
```

### Pattern B: Sitegen Context.setVariable (Phase 62 D-20 decomposed page-generator beans)

**Source:** `org.ctc.sitegen.DriverProfilePageGenerator` line 106-126

**Apply to:** 3 site offender templates (`driver-profile`, `team-profile`, `matchday`) and, IF scope is extended per recommendation in § 3d, also `StandingsPageGenerator` / `DriverRankingPageGenerator` / `MatchdaysPageGenerator.generateOverview()` / playoff-bracket generator.

**Excerpt:**
```java
var context = new Context(Locale.ENGLISH);
context.setVariable("season", season);
context.setVariable("driver", driver);
// ... existing setVariable calls ...
context.setVariable("breadcrumbCurrent", driver.getPsnId());
context.setVariable("pageTitle", driver.getPsnId());  // <-- new line
```

### Pattern C: `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional` IT harness

**Source:** `AdminDropdownRenderingIT.java` (line 40-93)

**Apply to:** `TemplateRenderingSmokeIT`.

### Pattern D: Deterministic-UUID `@Sql` test fixture (CLAUDE.md "Isolate Test Data Completely")

**Source:** `src/test/resources/sql/legacy-season-without-playoff.sql` (91 lines)
**Loaded via:** `src/test/java/org/ctc/e2e/LegacyMigratedSeasonE2ETest.java` line 50-52

**Apply to:** `src/test/resources/sql/smoke-it-fixture.sql`.

### Pattern E: Maven build-phase plugin execution (jacoco analog)

**Source:** `pom.xml` line 234-263 (jacoco-maven-plugin `<execution>` block)

**Apply to:** `exec-maven-plugin` grep-gate execution bound to `validate` phase.

### Pattern F: GlobalModelAdvice — REJECTED for `pageTitle`

**Source:** `org.ctc.admin.controller.GlobalModelAdvice` (whole file, 18 lines)

**Why rejected (per D-12):** `pageTitle` is per-route, not per-application. Using `@ControllerAdvice @ModelAttribute("pageTitle") public String pageTitle() { return "..."; }` is impossible without per-route context. Direct `model.addAttribute(...)` inside each handler is the correct pattern. This is documented here only to forestall the planner from inventing it.

---

## No Analog Found

| Target | Reason |
|---|---|
| `RequestMappingHandlerMapping`-driven dynamic-test-factory wiring | NEW pattern — no existing IT in `src/test/java` uses `RequestMappingHandlerMapping` introspection. The closest is `AdminDropdownRenderingIT`'s hardcoded `MethodSource`. Planner must invent the introspection block fresh (template provided in § 7 step 2). |
| `exec-maven-plugin` plugin block | NEW plugin — no `org.codehaus.mojo:exec-maven-plugin` references in `pom.xml`. Use the `org.springframework.boot:spring-boot-maven-plugin` (line 167-178) and `org.jacoco:jacoco-maven-plugin` (line 212-264) for shape; the `<configuration>` and `<executions>` semantics need to come from the exec-maven-plugin docs. |
| Thymeleaf `<dependencyManagement>` pin | NEW — current `pom.xml` has no `<dependencyManagement>` block at all. Standard Maven shape; no codebase analog needed. |

---

## Metadata

**Analog search scope:**
- `src/main/resources/templates/admin/` (62 files) — grep for `th:(replace|insert|include)=` with `${...}` in args
- `src/main/resources/templates/site/` (~16 files) — same grep
- `src/main/java/org/ctc/admin/controller/` (all GET-handler controllers)
- `src/main/java/org/ctc/sitegen/` (5 page-generator beans + supporting service)
- `src/test/java/org/ctc/admin/controller/integration/` (MockMvc IT precedents)
- `src/test/resources/sql/` (@Sql fixture precedents)
- `pom.xml` (build-plugin precedents)

**Files scanned:** ~110 templates + 30+ controllers/services + 3 ITs + 2 SQL fixtures + 1 pom

**Pattern extraction date:** 2026-05-11

**Critical scope-correction flagged for planner:** The audit-by-grep returned **17 fragment-call-with-`${...}` lines** (13 admin templates + 4 site `string-concat` templates + 3 site plain-`${var}` templates + 1 in-body fragment-call inside `site/matchday.html`). CONTEXT.md `<decisions>` D-02 + D-03 enumerated only 6 + 3 = 9. **Planner must either (a) expand the plan to cover all 17 lines for forward-compat consistency (recommended per D-01) or (b) explicitly document and justify why 8 additional lines stay out of scope.** The PLAT-04 requirement ("alle Findings über die 3 bekannten hinaus") supports the broader interpretation.
