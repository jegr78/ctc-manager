# Team Cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable admins to define team colors, upload logos, manage season-specific team data (rating, color/logo overrides), and generate team card PNGs via Playwright.

**Architecture:** New `SeasonTeam` entity replaces the `season_teams` join table and stores season-specific data (rating, color/logo overrides). Team entity gets color fields and file-upload for logos. A `TeamCardService` renders a Thymeleaf HTML template and captures it as PNG via Playwright headless browser. A new admin tool page provides card generation, preview gallery, and download.

**Tech Stack:** Java 25, Spring Boot 4.x, JPA/Hibernate, Thymeleaf, Playwright (Java), CSS3 gradients, FileStorageService

**Spec:** `docs/superpowers/specs/2026-03-30-team-cards-design.md`

---

## File Structure

### New Files
| File | Responsibility |
|------|---------------|
| `src/main/java/org/ctc/domain/model/SeasonTeam.java` | JPA entity: season-team link with rating, color/logo overrides |
| `src/main/java/org/ctc/domain/repository/SeasonTeamRepository.java` | Spring Data repository for SeasonTeam |
| `src/main/java/org/ctc/admin/service/TeamCardService.java` | Card generation: Thymeleaf render + Playwright screenshot |
| `src/main/java/org/ctc/admin/controller/TeamCardController.java` | Admin tool page controller: generate, download, gallery |
| `src/main/resources/templates/admin/team-cards.html` | Tool page: season selector, card gallery, generate/download buttons |
| `src/main/resources/templates/admin/team-card-render.html` | Standalone 1080x1920 card template for Playwright capture |
| `src/main/resources/static/admin/js/color-sync.js` | JS: sync color picker ↔ hex input |
| `src/test/java/org/ctc/domain/model/SeasonTeamTest.java` | Unit tests for effective color/logo fallback |
| `src/test/java/org/ctc/admin/service/TeamCardServiceTest.java` | Unit tests for card service |
| `src/test/java/org/ctc/admin/controller/TeamCardControllerTest.java` | Integration tests for controller |

### Modified Files
| File | Change |
|------|--------|
| `V1__initial_schema.sql` | Add color columns to `teams`, rewrite `season_teams` as entity table |
| `Team.java` | Add `primaryColor`, `secondaryColor`, `accentColor` fields |
| `Season.java` | Replace `@ManyToMany teams` with `@OneToMany seasonTeams` + convenience `getTeams()` |
| `SeasonController.java` | Adapt add-team/remove-team to create/delete SeasonTeam records |
| `TeamController.java` | Add logo upload endpoint, update save() for color fields |
| `StandingsService.java` | Use `season.getTeams()` (convenience method — minimal change) |
| `TestDataService.java` | Create SeasonTeam records instead of ManyToMany, add colors to teams |
| `team-form.html` | Add color picker section + logo file upload |
| `season-form.html` | Adapt team list to use seasonTeams |
| `season-detail.html` | Adapt team display to use seasonTeams |
| `layout.html` | Add "Team Cards" sidebar link under Tools |
| `pom.xml` | Add Playwright compile-scope dependency |
| `Dockerfile` | Install Chromium for Playwright |
| Various services/controllers | Adapt `season.getTeams()` calls (SwissPairingService, PlayoffService, CsvImportService, etc.) |

---

## Task 1: Schema & SeasonTeam Entity

**Files:**
- Modify: `src/main/resources/db/migration/V1__initial_schema.sql:36-43,62-68`
- Create: `src/main/java/org/ctc/domain/model/SeasonTeam.java`
- Create: `src/main/java/org/ctc/domain/repository/SeasonTeamRepository.java`
- Test: `src/test/java/org/ctc/domain/model/SeasonTeamTest.java`

- [ ] **Step 1: Write SeasonTeam unit tests**

```java
package org.ctc.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonTeamTest {

    @Test
    void getEffectivePrimaryColor_returnsOverrideWhenSet() {
        var team = new Team("Test Team", "TST");
        team.setPrimaryColor("#ff0000");

        var seasonTeam = new SeasonTeam();
        seasonTeam.setTeam(team);
        seasonTeam.setPrimaryColor("#00ff00");

        assertThat(seasonTeam.getEffectivePrimaryColor()).isEqualTo("#00ff00");
    }

    @Test
    void getEffectivePrimaryColor_fallsBackToTeamColor() {
        var team = new Team("Test Team", "TST");
        team.setPrimaryColor("#ff0000");

        var seasonTeam = new SeasonTeam();
        seasonTeam.setTeam(team);

        assertThat(seasonTeam.getEffectivePrimaryColor()).isEqualTo("#ff0000");
    }

    @Test
    void getEffectiveSecondaryColor_returnsOverrideWhenSet() {
        var team = new Team("Test Team", "TST");
        team.setSecondaryColor("#aaaaaa");

        var seasonTeam = new SeasonTeam();
        seasonTeam.setTeam(team);
        seasonTeam.setSecondaryColor("#bbbbbb");

        assertThat(seasonTeam.getEffectiveSecondaryColor()).isEqualTo("#bbbbbb");
    }

    @Test
    void getEffectiveSecondaryColor_fallsBackToTeamColor() {
        var team = new Team("Test Team", "TST");
        team.setSecondaryColor("#aaaaaa");

        var seasonTeam = new SeasonTeam();
        seasonTeam.setTeam(team);

        assertThat(seasonTeam.getEffectiveSecondaryColor()).isEqualTo("#aaaaaa");
    }

    @Test
    void getEffectiveAccentColor_returnsOverrideWhenSet() {
        var team = new Team("Test Team", "TST");
        team.setAccentColor("#111111");

        var seasonTeam = new SeasonTeam();
        seasonTeam.setTeam(team);
        seasonTeam.setAccentColor("#222222");

        assertThat(seasonTeam.getEffectiveAccentColor()).isEqualTo("#222222");
    }

    @Test
    void getEffectiveAccentColor_fallsBackToTeamColor() {
        var team = new Team("Test Team", "TST");
        team.setAccentColor("#111111");

        var seasonTeam = new SeasonTeam();
        seasonTeam.setTeam(team);

        assertThat(seasonTeam.getEffectiveAccentColor()).isEqualTo("#111111");
    }

    @Test
    void getEffectiveLogoUrl_returnsOverrideWhenSet() {
        var team = new Team("Test Team", "TST");
        team.setLogoUrl("/uploads/teams/abc/logo.png");

        var seasonTeam = new SeasonTeam();
        seasonTeam.setTeam(team);
        seasonTeam.setLogoUrl("/uploads/teams/abc/special.png");

        assertThat(seasonTeam.getEffectiveLogoUrl()).isEqualTo("/uploads/teams/abc/special.png");
    }

    @Test
    void getEffectiveLogoUrl_fallsBackToTeamLogoUrl() {
        var team = new Team("Test Team", "TST");
        team.setLogoUrl("/uploads/teams/abc/logo.png");

        var seasonTeam = new SeasonTeam();
        seasonTeam.setTeam(team);

        assertThat(seasonTeam.getEffectiveLogoUrl()).isEqualTo("/uploads/teams/abc/logo.png");
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure**

Run: `./mvnw test -pl . -Dtest=SeasonTeamTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: Compilation error — `SeasonTeam` class does not exist yet.

- [ ] **Step 3: Add color fields to Team entity**

In `Team.java`, add after `logoUrl` field (line 31):

```java
private String primaryColor;
private String secondaryColor;
private String accentColor;
```

- [ ] **Step 4: Update V1 schema — add team colors**

In `V1__initial_schema.sql`, replace the `teams` table definition (lines 36-43) with:

```sql
CREATE TABLE teams (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    short_name VARCHAR(50) NOT NULL,
    logo_url VARCHAR(500),
    primary_color VARCHAR(7),
    secondary_color VARCHAR(7),
    accent_color VARCHAR(7),
    parent_team_id UUID,
    CONSTRAINT fk_team_parent FOREIGN KEY (parent_team_id) REFERENCES teams(id)
);
```

- [ ] **Step 5: Update V1 schema — rewrite season_teams as entity table**

In `V1__initial_schema.sql`, replace the `season_teams` table definition (lines 62-68) with:

```sql
CREATE TABLE season_teams (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL,
    team_id UUID NOT NULL,
    rating INTEGER,
    primary_color VARCHAR(7),
    secondary_color VARCHAR(7),
    accent_color VARCHAR(7),
    logo_url VARCHAR(500),
    CONSTRAINT fk_st_season FOREIGN KEY (season_id) REFERENCES seasons(id),
    CONSTRAINT fk_st_team FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT uk_season_team UNIQUE (season_id, team_id)
);
```

- [ ] **Step 6: Create SeasonTeam entity**

```java
package org.ctc.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "season_teams")
@Getter @Setter @NoArgsConstructor
public class SeasonTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    private Integer rating;

    private String primaryColor;
    private String secondaryColor;
    private String accentColor;
    private String logoUrl;

    public SeasonTeam(Season season, Team team) {
        this.season = season;
        this.team = team;
    }

    public String getEffectivePrimaryColor() {
        return primaryColor != null ? primaryColor : team.getPrimaryColor();
    }

    public String getEffectiveSecondaryColor() {
        return secondaryColor != null ? secondaryColor : team.getSecondaryColor();
    }

    public String getEffectiveAccentColor() {
        return accentColor != null ? accentColor : team.getAccentColor();
    }

    public String getEffectiveLogoUrl() {
        return logoUrl != null ? logoUrl : team.getLogoUrl();
    }
}
```

- [ ] **Step 7: Create SeasonTeamRepository**

```java
package org.ctc.domain.repository;

import org.ctc.domain.model.SeasonTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonTeamRepository extends JpaRepository<SeasonTeam, UUID> {
    List<SeasonTeam> findBySeasonId(UUID seasonId);
    Optional<SeasonTeam> findBySeasonIdAndTeamId(UUID seasonId, UUID teamId);
    void deleteBySeasonIdAndTeamId(UUID seasonId, UUID teamId);
}
```

- [ ] **Step 8: Run tests — expect pass**

Run: `./mvnw test -pl . -Dtest=SeasonTeamTest`
Expected: All 8 tests PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/org/ctc/domain/model/SeasonTeam.java \
       src/main/java/org/ctc/domain/repository/SeasonTeamRepository.java \
       src/main/java/org/ctc/domain/model/Team.java \
       src/main/resources/db/migration/V1__initial_schema.sql \
       src/test/java/org/ctc/domain/model/SeasonTeamTest.java
git commit -m "$(cat <<'EOF'
SeasonTeam Entity und Team-Farbfelder einfuehren

Neues SeasonTeam-Entity ersetzt die ManyToMany-Join-Tabelle season_teams.
Speichert saisonspezifisches Rating sowie optionale Farb-/Logo-Overrides.
Team-Entity um primaryColor, secondaryColor, accentColor erweitert.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Season Entity — ManyToMany zu OneToMany Migration

**Files:**
- Modify: `src/main/java/org/ctc/domain/model/Season.java:59-64`

- [ ] **Step 1: Replace ManyToMany with OneToMany in Season.java**

Replace the `@ManyToMany teams` block (lines 59-64) with:

```java
@OneToMany(mappedBy = "season", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("team.shortName ASC")
private List<SeasonTeam> seasonTeams = new ArrayList<>();
```

Add a convenience method that returns the Team list (minimizes changes in consuming code):

```java
public List<Team> getTeams() {
    return seasonTeams.stream()
            .map(SeasonTeam::getTeam)
            .toList();
}
```

Add import for `SeasonTeam` if not already present.

- [ ] **Step 2: Update ToString exclude list**

In the `@ToString` annotation, replace `"teams"` with `"seasonTeams"` in the exclude list.

- [ ] **Step 3: Run full test suite to identify breakages**

Run: `./mvnw test`
Expected: Some tests fail due to `season.getTeams().add(...)` calls which no longer work on the unmodifiable list returned by the convenience method. Note which tests fail.

- [ ] **Step 4: Add helper method for adding teams**

Add to `Season.java`:

```java
public SeasonTeam addTeam(Team team) {
    var seasonTeam = new SeasonTeam(this, team);
    seasonTeams.add(seasonTeam);
    return seasonTeam;
}

public void removeTeam(Team team) {
    seasonTeams.removeIf(st -> st.getTeam().getId().equals(team.getId()));
}

public boolean containsTeam(Team team) {
    return seasonTeams.stream().anyMatch(st -> st.getTeam().getId().equals(team.getId()));
}

public Optional<SeasonTeam> findSeasonTeam(Team team) {
    return seasonTeams.stream()
            .filter(st -> st.getTeam().getId().equals(team.getId()))
            .findFirst();
}
```

Add import for `Optional`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/ctc/domain/model/Season.java
git commit -m "$(cat <<'EOF'
Season: ManyToMany Teams durch OneToMany SeasonTeams ersetzen

Convenience-Methoden getTeams(), addTeam(), removeTeam(), containsTeam()
minimieren den Aenderungsaufwand in konsumierendem Code.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Adapt All season.getTeams() Consumers

**Files:**
- Modify: `src/main/java/org/ctc/admin/controller/SeasonController.java`
- Modify: `src/main/java/org/ctc/admin/TestDataService.java`
- Modify: `src/main/java/org/ctc/domain/service/StandingsService.java`
- Modify: `src/main/java/org/ctc/domain/service/PlayoffService.java`
- Modify: `src/main/java/org/ctc/domain/service/SwissPairingService.java`
- Modify: `src/main/java/org/ctc/dataimport/CsvImportService.java`
- Modify: `src/main/java/org/ctc/admin/controller/MatchController.java`
- Modify: `src/main/java/org/ctc/admin/controller/RaceLineupController.java`
- Modify: `src/main/resources/templates/admin/season-form.html`
- Modify: `src/main/resources/templates/admin/season-detail.html`
- Modify: Test files that use `season.getTeams().add(...)`

- [ ] **Step 1: Adapt SeasonController — addTeam/removeTeam**

Replace `SeasonController.addTeam()` method (lines 102-119) with:

```java
@PostMapping("/{id}/add-team")
public String addTeam(@PathVariable UUID id, @RequestParam UUID teamId,
                      RedirectAttributes redirectAttributes) {
    var season = seasonRepository.findById(id).orElseThrow();
    var team = teamRepository.findById(teamId).orElseThrow();
    if (!season.containsTeam(team)) {
        if (team.isSubTeam() && !season.containsTeam(team.getParentTeam())) {
            season.addTeam(team.getParentTeam());
            log.info("Auto-added parent team {} to season {}", team.getParentTeam().getShortName(), season.getName());
        }
        season.addTeam(team);
        seasonRepository.save(season);
        log.info("Added team {} to season {}", team.getShortName(), season.getName());
    }
    redirectAttributes.addFlashAttribute("successMessage", "Team added: " + team.getShortName());
    return "redirect:/admin/seasons/" + id + "/edit";
}
```

Replace `SeasonController.removeTeam()` method (lines 121-156) with:

```java
@PostMapping("/{id}/remove-team")
public String removeTeam(@PathVariable UUID id, @RequestParam UUID teamId,
                         RedirectAttributes redirectAttributes) {
    var season = seasonRepository.findById(id).orElseThrow();
    var team = teamRepository.findById(teamId).orElseThrow();

    if (!team.isSubTeam()) {
        boolean hasSubs = season.getTeams().stream()
                .anyMatch(t -> t.isSubTeam() && t.getParentOrSelf().getId().equals(team.getId()));
        if (hasSubs) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Cannot remove parent team " + team.getShortName() + " — remove its sub-teams first");
            return "redirect:/admin/seasons/" + id + "/edit";
        }
    }

    season.removeTeam(team);

    if (team.isSubTeam()) {
        var parent = team.getParentTeam();
        boolean hasOtherSubs = season.getTeams().stream()
                .anyMatch(t -> t.isSubTeam() && t.getParentOrSelf().getId().equals(parent.getId()));
        if (!hasOtherSubs) {
            season.removeTeam(parent);
            log.info("Auto-removed parent team {} from season {} (no sub-teams left)",
                    parent.getShortName(), season.getName());
        }
    }

    seasonRepository.save(season);
    log.info("Removed team {} from season {}", team.getShortName(), season.getName());
    redirectAttributes.addFlashAttribute("successMessage", "Team removed");
    return "redirect:/admin/seasons/" + id + "/edit";
}
```

- [ ] **Step 2: Adapt TestDataService — use season.addTeam()**

Replace all `season.getTeams().addAll(...)` calls with individual `season.addTeam(team)` calls. For example, replace lines 110-112:

```java
// Old: season.getTeams().addAll(parentTeams);
// New:
for (var team : parentTeams) {
    season.addTeam(team);
}
```

Apply the same pattern to all `getTeams().addAll(...)` calls in `seedSeasons()` (lines 117-118, 128-129, 140-158) and test data methods (lines 400, 435).

- [ ] **Step 3: Adapt templates — season-form.html and season-detail.html**

In `season-form.html`: Replace `${season.teams}` with `${season.teams}` — the convenience method `getTeams()` returns a `List<Team>`, so templates should work without changes. Verify by checking that `season.teams.size()`, `season.teams.isEmpty()`, and `th:each="team : ${season.teams}"` all still work.

In `season-detail.html`: Same — verify that `${season.teams}` references work via the convenience getter.

**Note:** Thymeleaf accesses `season.teams` via `getTeams()`, so the convenience method handles the mapping transparently. The `season.teams.contains(t)` check in `season-form.html` (line 130) needs to be verified: since `getTeams()` returns a new list each call, `contains()` works via Team's `equals()`. If Team doesn't override `equals()`, compare by ID. Adjust the template to use:

```html
th:if="${!season.teams.contains(t)}"
```

If this doesn't work due to missing `equals()` on Team, switch to:

```html
th:unless="${season.teams.![id].contains(t.id)}"
```

- [ ] **Step 4: Adapt remaining services**

`StandingsService.java` line 32: `season.getTeams()` already works via convenience method — no change needed.

`PlayoffService.java`, `SwissPairingService.java`, `CsvImportService.java`, `MatchController.java`, `RaceLineupController.java`: All use `season.getTeams()` as a read-only list — the convenience method handles this. No changes needed.

- [ ] **Step 5: Adapt test files**

In test files that do `season.getTeams().add(team)`, replace with `season.addTeam(team)`:

- `SwissPairingServiceTest.java` line 169
- `StandingsControllerTest.java` lines 52-53
- `SiteGeneratorServiceTest.java` lines 96-97

- [ ] **Step 6: Run full test suite**

Run: `./mvnw verify`
Expected: All tests PASS. If any fail, fix the remaining `getTeams().add()` or `getTeams().addAll()` usages.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
Alle season.getTeams()-Aufrufe auf SeasonTeam migrieren

SeasonController nutzt season.addTeam()/removeTeam().
TestDataService erstellt SeasonTeam-Records.
Convenience-Methode getTeams() macht Aenderungen in Services und
Templates transparent.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Team Colors & Logo Upload in Admin UI

**Files:**
- Modify: `src/main/java/org/ctc/admin/controller/TeamController.java`
- Modify: `src/main/resources/templates/admin/team-form.html`
- Create: `src/main/resources/static/admin/js/color-sync.js`

- [ ] **Step 1: Add logo upload endpoint to TeamController**

Add `FileStorageService` dependency and upload endpoint to `TeamController.java`:

```java
private final FileStorageService fileStorageService;
```

Add to the constructor injection (already handled by `@RequiredArgsConstructor`).

Add endpoint:

```java
@PostMapping("/{id}/logo")
public String uploadLogo(@PathVariable UUID id, @RequestParam MultipartFile logo,
                         RedirectAttributes redirectAttributes) {
    try {
        var team = teamRepository.findById(id).orElseThrow();
        if (team.getLogoUrl() != null) {
            fileStorageService.delete(team.getLogoUrl());
        }
        String url = fileStorageService.storeImage("teams", id, logo);
        team.setLogoUrl(url);
        teamRepository.save(team);
        redirectAttributes.addFlashAttribute("successMessage", "Logo updated");
    } catch (Exception e) {
        redirectAttributes.addFlashAttribute("errorMessage", "Logo upload failed: " + e.getMessage());
    }
    return "redirect:/admin/teams/" + id + "/edit";
}
```

Add import for `MultipartFile` and `FileStorageService`.

- [ ] **Step 2: Update TeamController.save() for color fields**

In the `save()` method, add color field updates in the existing-team branch (after line 175):

```java
existing.setPrimaryColor(team.getPrimaryColor());
existing.setSecondaryColor(team.getSecondaryColor());
existing.setAccentColor(team.getAccentColor());
```

- [ ] **Step 3: Create color-sync.js**

```javascript
document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.color-pair').forEach(function (pair) {
        var picker = pair.querySelector('input[type="color"]');
        var text = pair.querySelector('input[type="text"]');
        if (!picker || !text) return;

        picker.addEventListener('input', function () {
            text.value = picker.value;
        });
        text.addEventListener('input', function () {
            if (/^#[0-9a-fA-F]{6}$/.test(text.value)) {
                picker.value = text.value;
            }
        });
    });
});
```

- [ ] **Step 4: Update team-form.html — add color section and logo upload**

Replace the logoUrl text input (line 21-24) and add the color + logo sections. The full form section becomes:

After the shortName field and before the parentTeam display, add:

```html
<div class="card" style="margin-top:16px;" th:if="${team.id != null || team.parentTeam == null}">
    <h2>Brand Colors</h2>
    <div class="form-row">
        <div class="form-group">
            <label>Primary Color</label>
            <div class="color-pair" style="display:flex;gap:8px;align-items:center;">
                <input type="color" th:value="${team.primaryColor ?: '#333333'}"
                       style="width:48px;height:36px;padding:2px;border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--bg-input);cursor:pointer;">
                <input type="text" th:field="*{primaryColor}" placeholder="#333333"
                       pattern="#[0-9a-fA-F]{6}" maxlength="7"
                       style="width:100px;">
            </div>
        </div>
        <div class="form-group">
            <label>Secondary Color</label>
            <div class="color-pair" style="display:flex;gap:8px;align-items:center;">
                <input type="color" th:value="${team.secondaryColor ?: '#555555'}"
                       style="width:48px;height:36px;padding:2px;border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--bg-input);cursor:pointer;">
                <input type="text" th:field="*{secondaryColor}" placeholder="#555555"
                       pattern="#[0-9a-fA-F]{6}" maxlength="7"
                       style="width:100px;">
            </div>
        </div>
        <div class="form-group">
            <label>Accent Color</label>
            <div class="color-pair" style="display:flex;gap:8px;align-items:center;">
                <input type="color" th:value="${team.accentColor ?: '#4fc3f7'}"
                       style="width:48px;height:36px;padding:2px;border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--bg-input);cursor:pointer;">
                <input type="text" th:field="*{accentColor}" placeholder="#4fc3f7"
                       pattern="#[0-9a-fA-F]{6}" maxlength="7"
                       style="width:100px;">
            </div>
        </div>
    </div>
</div>
```

Remove the old `logoUrl` text input and add a logo upload section (only when editing):

```html
<div th:if="${team.id != null}" class="card" style="margin-top:16px;">
    <h2>Logo</h2>
    <div th:if="${team.logoUrl != null}" style="margin-bottom:12px;">
        <img th:src="${team.logoUrl}" alt="" style="max-width:150px;border-radius:6px;background:#222;padding:8px;">
    </div>
    <div th:if="${team.logoUrl == null}" class="empty-state" style="padding:16px;"><p>No logo yet.</p></div>
    <form th:action="@{/admin/teams/{id}/logo(id=${team.id})}" method="post" enctype="multipart/form-data" style="margin-top:8px;">
        <div style="display:flex;gap:8px;align-items:flex-end;">
            <div class="form-group" style="margin-bottom:0;">
                <input type="file" name="logo" accept="image/png,image/jpeg,image/webp,image/gif" required
                       style="padding:6px;border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--bg-input);color:var(--white);font-size:13px;">
                <small class="text-dim" style="display:block;margin-top:2px;">PNG, JPG, WebP, GIF. Max 10 MB.</small>
            </div>
            <button type="submit" class="btn btn-primary btn-sm">Upload</button>
        </div>
    </form>
</div>
```

Add `color-sync.js` script to the page (before closing `</section>`):

```html
<script th:src="@{/admin/js/color-sync.js}"></script>
```

- [ ] **Step 5: Add color-sync.js to layout.html**

Add after the `searchable-dropdown.js` script line in `layout.html`:

```html
<script th:src="@{/admin/js/color-sync.js}"></script>
```

**Alternative:** Only load it on team-form.html (already done in step 4). Since it's small and idempotent, loading it globally is fine too. Choose one approach.

- [ ] **Step 6: Run tests**

Run: `./mvnw verify`
Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/ctc/admin/controller/TeamController.java \
       src/main/resources/templates/admin/team-form.html \
       src/main/resources/static/admin/js/color-sync.js
git commit -m "$(cat <<'EOF'
Team-Formular: Farbfelder und Logo-Upload ergaenzen

Color Picker + Hex-Eingabe fuer Primary/Secondary/Accent Color.
Logo-Upload per FileStorageService (gleicher Pattern wie Cars/Tracks).
color-sync.js synchronisiert Picker und Textfeld.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: TestDataService — Farben und SeasonTeam-Daten

**Files:**
- Modify: `src/main/java/org/ctc/admin/TestDataService.java`

- [ ] **Step 1: Add team colors to seed data**

In `seedTeams()`, set colors on each team. Use the actual CTC team colors where known. Example:

```java
private List<Team> seedTeams() {
    var teams = List.of(
            team("Project One Racing", "P1R", "#e53935", "#555555", "#e53935"),
            team("Community League Racing", "CLR", "#2196f3", "#444444", "#2196f3"),
            team("Tidgney Community Racing", "TCR", "#fdd835", "#333333", "#fdd835"),
            team("Amigos Racing Team", "ART", "#4caf50", "#333333", "#4caf50"),
            team("Apex Hunter Racing", "AHR", "#ff9800", "#333333", "#ff9800"),
            team("Medway Racing League", "MRL", "#9c27b0", "#333333", "#9c27b0"),
            team("Gen-X Racing", "GXR", "#00bcd4", "#333333", "#00bcd4"),
            team("Dream Team Racing", "DTR", "#e53935", "#555555", "#e53935"),
            team("VEZ Racing Team", "VEZ", "#ff5722", "#333333", "#ff5722"),
            team("The Neutrals Racing", "TNR", "#e53935", "#555555", "#00bcd4")
    );
    return teamRepository.saveAll(teams);
}

private Team team(String name, String shortName, String primary, String secondary, String accent) {
    var t = new Team(name, shortName);
    t.setPrimaryColor(primary);
    t.setSecondaryColor(secondary);
    t.setAccentColor(accent);
    return t;
}
```

- [ ] **Step 2: Add ratings to active season teams**

After creating Season 4 teams via `season.addTeam()`, set ratings on selected SeasonTeams:

```java
// Set ratings for active season
s4.findSeasonTeam(findSub.apply("TNR A")).ifPresent(st -> st.setRating(93));
s4.findSeasonTeam(findParent.apply("P1R")).ifPresent(st -> st.setRating(93));
s4.findSeasonTeam(findSub.apply("CLR 1")).ifPresent(st -> st.setRating(92));
s4.findSeasonTeam(findParent.apply("TCR")).ifPresent(st -> st.setRating(86));
s4.findSeasonTeam(findParent.apply("DTR")).ifPresent(st -> st.setRating(85));
s4.findSeasonTeam(findSub.apply("CLR 2")).ifPresent(st -> st.setRating(87));
s4.findSeasonTeam(findSub.apply("TNR B")).ifPresent(st -> st.setRating(85));
```

- [ ] **Step 3: Run app to verify seed data**

Run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
Verify: Check `/admin/teams` — teams should display. Check that editing a team shows color fields. Check `/admin/seasons/{active-season-id}` — teams should still appear.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/ctc/admin/TestDataService.java
git commit -m "$(cat <<'EOF'
TestDataService: Teamfarben und SeasonTeam-Ratings hinzufuegen

Jedes Team bekommt Primary/Secondary/Accent Colors.
Aktive Saison bekommt Beispiel-Ratings pro SeasonTeam.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Playwright Runtime-Dependency

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add Playwright as compile-scope dependency**

The existing Playwright dependency has `<scope>test</scope>`. Add a second dependency without scope restriction for runtime use, or change the scope. Recommended approach — add a separate compile-scope dependency block:

```xml
<!-- Playwright for Team Card generation (runtime) -->
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>${playwright.version}</version>
</dependency>
```

Remove the existing test-scoped Playwright dependency to avoid conflicts (or change its scope to `compile`). The simplest approach: just remove `<scope>test</scope>` from the existing dependency.

- [ ] **Step 2: Verify build**

Run: `./mvnw compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "$(cat <<'EOF'
Playwright auf compile-Scope aendern fuer Team Card Generierung

Playwright wird zur Laufzeit benoetigt um HTML-Templates als PNG
zu screenshotten.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: TeamCardService — Card Generation

**Files:**
- Create: `src/main/java/org/ctc/admin/service/TeamCardService.java`
- Create: `src/main/resources/templates/admin/team-card-render.html`
- Test: `src/test/java/org/ctc/admin/service/TeamCardServiceTest.java`

- [ ] **Step 1: Write TeamCardService tests**

```java
package org.ctc.admin.service;

import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;
import org.ctc.domain.service.FileStorageService;
import org.ctc.domain.service.StandingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TeamCardServiceTest {

    @Test
    void getCardPath_returnsExpectedPath() {
        var season = new Season("Season 4 - 2026");
        season.setId(java.util.UUID.randomUUID());
        var team = new Team("Test Team", "TST");
        team.setPrimaryColor("#ff0000");
        team.setSecondaryColor("#555555");
        team.setAccentColor("#ff0000");

        var seasonTeam = new SeasonTeam(season, team);

        var service = new TeamCardService(null, null, null, "uploads");
        String path = service.getCardPath(seasonTeam);

        assertThat(path).isEqualTo("/uploads/team-cards/" + season.getId() + "/TST.png");
    }

    @Test
    void getCardFilename_usesTeamShortName() {
        var team = new Team("Community League Racing 1", "CLR 1");
        team.setPrimaryColor("#2196f3");
        team.setSecondaryColor("#444444");
        team.setAccentColor("#2196f3");
        var season = new Season("Season 4");
        season.setId(java.util.UUID.randomUUID());
        var seasonTeam = new SeasonTeam(season, team);

        var service = new TeamCardService(null, null, null, "uploads");
        String path = service.getCardPath(seasonTeam);

        assertThat(path).contains("CLR_1.png");
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure**

Run: `./mvnw test -Dtest=TeamCardServiceTest`
Expected: Compilation error — `TeamCardService` does not exist.

- [ ] **Step 3: Create team-card-render.html template**

This is the standalone HTML page that Playwright will screenshot. It must be exactly 1080x1920:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <style>
        @font-face {
            font-family: 'Conthrax';
            src: url('data:font/woff2;base64,FONT_BASE64_HERE') format('woff2');
            font-weight: 600;
        }

        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            width: 1080px;
            height: 1920px;
            overflow: hidden;
            font-family: 'Conthrax', -apple-system, sans-serif;
            color: white;
            position: relative;
        }

        .background {
            position: absolute;
            inset: 0;
            background: linear-gradient(180deg,
                var(--secondary-color) 0%,
                #111111 100%);
        }

        .glow-overlay {
            position: absolute;
            inset: 0;
            background: linear-gradient(180deg,
                var(--primary-color-alpha) 0%,
                transparent 40%);
        }

        .content {
            position: relative;
            z-index: 1;
            display: flex;
            flex-direction: column;
            align-items: center;
            height: 100%;
            padding: 120px 60px 160px;
        }

        .rating-circle {
            width: 260px;
            height: 260px;
            border-radius: 50%;
            border: 8px solid var(--accent-color);
            background: rgba(50, 50, 50, 0.8);
            display: flex;
            align-items: center;
            justify-content: center;
            margin-bottom: 80px;
        }

        .rating-number {
            font-size: 120px;
            font-weight: 600;
            letter-spacing: -4px;
        }

        .logo-container {
            width: 320px;
            height: 320px;
            display: flex;
            align-items: center;
            justify-content: center;
            margin-bottom: 60px;
        }

        .logo-container img {
            max-width: 100%;
            max-height: 100%;
            object-fit: contain;
        }

        .team-name {
            font-size: 52px;
            font-weight: 600;
            text-align: center;
            margin-bottom: 8px;
            line-height: 1.1;
        }

        .sub-team-label {
            font-size: 40px;
            color: var(--accent-color);
            margin-bottom: 80px;
        }

        .stats {
            margin-top: auto;
            text-align: center;
        }

        .stat-label {
            font-size: 32px;
            color: var(--accent-color);
            letter-spacing: 6px;
            font-weight: 600;
        }

        .stat-value {
            font-size: 72px;
            font-weight: 600;
            margin-bottom: 40px;
        }

        .record-value {
            font-size: 48px;
            font-weight: 600;
            letter-spacing: 4px;
        }
    </style>
</head>
<body th:style="'--primary-color:' + ${primaryColor} + ';--secondary-color:' + ${secondaryColor} + ';--accent-color:' + ${accentColor} + ';--primary-color-alpha:' + ${primaryColor} + '40;'">
    <div class="background"></div>
    <div class="glow-overlay"></div>
    <div class="content">
        <div class="rating-circle">
            <span class="rating-number" th:text="${rating != null ? rating : '—'}"></span>
        </div>
        <div class="logo-container">
            <img th:if="${logoBase64 != null}" th:src="${logoBase64}" alt="">
            <span th:if="${logoBase64 == null}" style="font-size:48px;opacity:0.3;">No Logo</span>
        </div>
        <div class="team-name" th:text="${teamName}"></div>
        <div class="sub-team-label" th:if="${subTeamLabel != null}" th:text="${subTeamLabel}"></div>
        <div class="stats">
            <div class="stat-label">POINTS</div>
            <div class="stat-value" th:text="${points}"></div>
            <div class="stat-label">RECORD</div>
            <div class="record-value" th:text="${record}"></div>
        </div>
    </div>
</body>
</html>
```

**Note:** The `@font-face` base64 will be filled by the service reading the Conthrax font file and encoding it. Alternatively, use `file://` reference to the font. This detail will be refined during implementation.

- [ ] **Step 4: Create TeamCardService**

```java
package org.ctc.admin.service;

import com.microsoft.playwright.*;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Season;
import org.ctc.domain.service.FileStorageService;
import org.ctc.domain.service.StandingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Service
public class TeamCardService {

    private final TemplateEngine templateEngine;
    private final StandingsService standingsService;
    private final FileStorageService fileStorageService;
    private final Path uploadDir;

    public TeamCardService(TemplateEngine templateEngine,
                           StandingsService standingsService,
                           FileStorageService fileStorageService,
                           @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.templateEngine = templateEngine;
        this.standingsService = standingsService;
        this.fileStorageService = fileStorageService;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public String generateCard(SeasonTeam seasonTeam) throws IOException {
        var team = seasonTeam.getTeam();
        var season = seasonTeam.getSeason();

        // Get standings data
        var standings = standingsService.calculateStandings(season.getId());
        var teamStanding = standings.stream()
                .filter(s -> s.getTeam().getId().equals(team.getId()))
                .findFirst()
                .orElse(null);

        int points = teamStanding != null ? teamStanding.getPoints() : 0;
        String record = teamStanding != null ? teamStanding.getMatchRecord() : "0 - 0 - 0";

        // Encode logo as base64
        String logoBase64 = encodeLogoBase64(seasonTeam.getEffectiveLogoUrl());

        // Determine sub-team label
        String subTeamLabel = null;
        if (team.isSubTeam()) {
            String parentShort = team.getParentTeam().getShortName();
            String teamShort = team.getShortName();
            subTeamLabel = teamShort.replace(parentShort, "").trim();
            if (subTeamLabel.isEmpty()) subTeamLabel = null;
        }

        // Render template
        var ctx = new Context();
        ctx.setVariable("teamName", team.isSubTeam() ? team.getParentTeam().getName() : team.getName());
        ctx.setVariable("subTeamLabel", subTeamLabel);
        ctx.setVariable("rating", seasonTeam.getRating());
        ctx.setVariable("points", points);
        ctx.setVariable("record", record);
        ctx.setVariable("primaryColor", seasonTeam.getEffectivePrimaryColor());
        ctx.setVariable("secondaryColor", seasonTeam.getEffectiveSecondaryColor());
        ctx.setVariable("accentColor", seasonTeam.getEffectiveAccentColor());
        ctx.setVariable("logoBase64", logoBase64);

        String html = templateEngine.process("admin/team-card-render", ctx);

        // Write temp HTML
        Path tempFile = Files.createTempFile("team-card-", ".html");
        Files.writeString(tempFile, html);

        // Screenshot with Playwright
        String outputPath = getCardStoragePath(seasonTeam);
        Path outputFile = uploadDir.resolve(outputPath);
        Files.createDirectories(outputFile.getParent());

        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage(new Browser.NewPageOptions()
                    .setViewportSize(1080, 1920));
            page.navigate("file://" + tempFile.toAbsolutePath());
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(outputFile)
                    .setFullPage(false));
            browser.close();
        } finally {
            Files.deleteIfExists(tempFile);
        }

        log.info("Generated team card: {}", outputFile);
        return getCardPath(seasonTeam);
    }

    public List<String> generateAllCards(Season season) throws IOException {
        var paths = new ArrayList<String>();
        for (var seasonTeam : season.getSeasonTeams()) {
            if (seasonTeam.getTeam().isSubTeam() || !seasonTeam.getTeam().hasSubTeams()) {
                paths.add(generateCard(seasonTeam));
            }
        }
        return paths;
    }

    public String getCardPath(SeasonTeam seasonTeam) {
        return "/uploads/team-cards/" + seasonTeam.getSeason().getId() + "/"
                + sanitizeFilename(seasonTeam.getTeam().getShortName()) + ".png";
    }

    public boolean cardExists(SeasonTeam seasonTeam) {
        return Files.exists(uploadDir.resolve(getCardStoragePath(seasonTeam)));
    }

    private String getCardStoragePath(SeasonTeam seasonTeam) {
        return "team-cards/" + seasonTeam.getSeason().getId() + "/"
                + sanitizeFilename(seasonTeam.getTeam().getShortName()) + ".png";
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String encodeLogoBase64(String logoUrl) {
        if (logoUrl == null) return null;
        try {
            Path logoFile = uploadDir.resolve(logoUrl.substring("/uploads/".length()));
            if (Files.exists(logoFile)) {
                byte[] bytes = Files.readAllBytes(logoFile);
                String mimeType = Files.probeContentType(logoFile);
                if (mimeType == null) mimeType = "image/png";
                return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
            }
        } catch (IOException e) {
            log.warn("Failed to encode logo: {}", logoUrl, e);
        }
        return null;
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./mvnw test -Dtest=TeamCardServiceTest`
Expected: Tests PASS (getCardPath tests only — no Playwright needed for these).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/ctc/admin/service/TeamCardService.java \
       src/main/resources/templates/admin/team-card-render.html \
       src/test/java/org/ctc/admin/service/TeamCardServiceTest.java
git commit -m "$(cat <<'EOF'
TeamCardService: Card-Generierung via Thymeleaf + Playwright

Rendert team-card-render.html Template mit Teamfarben, Logo (base64),
Rating und Standings-Daten. Playwright macht 1080x1920 Screenshot.
Gradient-Fade Design mit Conthrax-Font.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: TeamCardController & Tool Page

**Files:**
- Create: `src/main/java/org/ctc/admin/controller/TeamCardController.java`
- Create: `src/main/resources/templates/admin/team-cards.html`
- Modify: `src/main/resources/templates/admin/layout.html`

- [ ] **Step 1: Create TeamCardController**

```java
package org.ctc.admin.controller;

import org.ctc.admin.service.TeamCardService;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Controller
@RequestMapping("/admin/tools/team-cards")
@RequiredArgsConstructor
public class TeamCardController {

    private final SeasonRepository seasonRepository;
    private final SeasonTeamRepository seasonTeamRepository;
    private final TeamCardService teamCardService;

    @GetMapping
    public String index(@RequestParam(required = false) UUID seasonId, Model model) {
        var seasons = seasonRepository.findAll();
        var activeSeason = seasonId != null
                ? seasonRepository.findById(seasonId).orElse(null)
                : seasons.stream().filter(s -> s.isActive()).findFirst().orElse(null);

        if (activeSeason != null) {
            var seasonTeams = seasonTeamRepository.findBySeasonId(activeSeason.getId());
            var cardStates = seasonTeams.stream().map(st -> new CardState(
                    st,
                    teamCardService.cardExists(st),
                    teamCardService.getCardPath(st)
            )).toList();
            model.addAttribute("season", activeSeason);
            model.addAttribute("cardStates", cardStates);
        }

        model.addAttribute("seasons", seasons);
        model.addAttribute("selectedSeasonId", activeSeason != null ? activeSeason.getId() : null);
        return "admin/team-cards";
    }

    @PostMapping("/generate/{seasonTeamId}")
    public String generate(@PathVariable UUID seasonTeamId, RedirectAttributes redirectAttributes) {
        var seasonTeam = seasonTeamRepository.findById(seasonTeamId).orElseThrow();
        try {
            teamCardService.generateCard(seasonTeam);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Card generated: " + seasonTeam.getTeam().getShortName());
        } catch (Exception e) {
            log.error("Card generation failed for {}", seasonTeam.getTeam().getShortName(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Generation failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/team-cards?seasonId=" + seasonTeam.getSeason().getId();
    }

    @PostMapping("/generate-all")
    public String generateAll(@RequestParam UUID seasonId, RedirectAttributes redirectAttributes) {
        var season = seasonRepository.findById(seasonId).orElseThrow();
        try {
            var paths = teamCardService.generateAllCards(season);
            redirectAttributes.addFlashAttribute("successMessage",
                    paths.size() + " cards generated");
        } catch (Exception e) {
            log.error("Batch card generation failed for season {}", season.getName(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Generation failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/team-cards?seasonId=" + seasonId;
    }

    @GetMapping("/download/{seasonTeamId}")
    public ResponseEntity<Resource> download(@PathVariable UUID seasonTeamId) {
        var seasonTeam = seasonTeamRepository.findById(seasonTeamId).orElseThrow();
        String cardPath = teamCardService.getCardPath(seasonTeam);
        Path file = Paths.get("uploads").toAbsolutePath().normalize()
                .resolve(cardPath.substring("/uploads/".length()));

        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + seasonTeam.getTeam().getShortName() + "-card.png\"")
                .body(new FileSystemResource(file));
    }

    @GetMapping("/download-all")
    public ResponseEntity<byte[]> downloadAll(@RequestParam UUID seasonId) throws IOException {
        var seasonTeams = seasonTeamRepository.findBySeasonId(seasonId);
        var baos = new ByteArrayOutputStream();

        try (var zip = new ZipOutputStream(baos)) {
            for (var st : seasonTeams) {
                String cardPath = teamCardService.getCardPath(st);
                Path file = Paths.get("uploads").toAbsolutePath().normalize()
                        .resolve(cardPath.substring("/uploads/".length()));
                if (Files.exists(file)) {
                    zip.putNextEntry(new ZipEntry(st.getTeam().getShortName() + "-card.png"));
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
        }

        var season = seasonRepository.findById(seasonId).orElseThrow();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"team-cards-" + season.getName().replaceAll("[^a-zA-Z0-9-]", "_") + ".zip\"")
                .body(baos.toByteArray());
    }

    public record CardState(SeasonTeam seasonTeam, boolean exists, String cardPath) {}
}
```

- [ ] **Step 2: Create team-cards.html template**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Team Cards', ~{::section})}">
<body>
<section>
    <h1>Team Cards</h1>
    <div class="card">
        <div style="display:flex;gap:12px;align-items:center;flex-wrap:wrap;">
            <form th:action="@{/admin/tools/team-cards}" method="get" style="display:flex;gap:8px;align-items:center;">
                <label for="seasonId" style="white-space:nowrap;">Season:</label>
                <select name="seasonId" id="seasonId" onchange="this.form.submit()"
                        style="padding:8px 12px;border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--bg-input);color:var(--white);font-size:14px;">
                    <option value="" th:if="${seasons.isEmpty()}">No seasons</option>
                    <option th:each="s : ${seasons}" th:value="${s.id}" th:text="${s.name}"
                            th:selected="${s.id == selectedSeasonId}"></option>
                </select>
            </form>
            <div th:if="${season != null}" style="display:flex;gap:8px;margin-left:auto;">
                <form th:action="@{/admin/tools/team-cards/generate-all}" method="post">
                    <input type="hidden" name="seasonId" th:value="${season.id}">
                    <button type="submit" class="btn btn-primary">Generate All</button>
                </form>
                <a th:href="@{/admin/tools/team-cards/download-all(seasonId=${season.id})}"
                   class="btn btn-secondary">Download ZIP</a>
            </div>
        </div>
    </div>

    <div th:if="${season != null && cardStates != null}" style="margin-top:16px;">
        <div style="display:grid;grid-template-columns:repeat(auto-fill, minmax(220px, 1fr));gap:16px;">
            <div th:each="cs : ${cardStates}" class="card" style="padding:12px;text-align:center;">
                <div th:if="${cs.exists()}" style="margin-bottom:8px;">
                    <img th:src="${cs.cardPath()}" alt="" style="width:100%;border-radius:6px;">
                </div>
                <div th:unless="${cs.exists()}" style="aspect-ratio:9/16;background:var(--bg-input);border-radius:6px;display:flex;align-items:center;justify-content:center;margin-bottom:8px;">
                    <span class="text-dim">Not generated</span>
                </div>
                <strong th:text="${cs.seasonTeam().team.shortName}"></strong>
                <span th:if="${cs.seasonTeam().rating != null}" class="text-dim"
                      th:text="' — Rating: ' + ${cs.seasonTeam().rating}"></span>
                <div style="margin-top:8px;display:flex;gap:4px;justify-content:center;">
                    <form th:action="@{/admin/tools/team-cards/generate/{id}(id=${cs.seasonTeam().id})}" method="post">
                        <button type="submit" class="btn btn-primary btn-sm">Generate</button>
                    </form>
                    <a th:if="${cs.exists()}"
                       th:href="@{/admin/tools/team-cards/download/{id}(id=${cs.seasonTeam().id})}"
                       class="btn btn-secondary btn-sm">Download</a>
                </div>
            </div>
        </div>
    </div>

    <div th:if="${season == null}" class="card" style="margin-top:16px;">
        <div class="empty-state" style="padding:24px;"><p>Select a season to manage team cards.</p></div>
    </div>
</section>
</body>
</html>
```

- [ ] **Step 3: Add sidebar link in layout.html**

In `layout.html`, add after the "Generate Site" link (line 49):

```html
<a th:href="@{/admin/tools/team-cards}" th:classappend="${title.contains('Team Cards') ? 'active' : ''}">Team Cards</a>
```

- [ ] **Step 4: Run tests**

Run: `./mvnw verify`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/ctc/admin/controller/TeamCardController.java \
       src/main/resources/templates/admin/team-cards.html \
       src/main/resources/templates/admin/layout.html
git commit -m "$(cat <<'EOF'
Team Cards Tool-Seite mit Galerie, Generierung und Download

Neue Seite unter /admin/tools/team-cards mit Saison-Auswahl,
Card-Galerie (Thumbnails), Einzel-/Batch-Generierung und
ZIP-Download. Sidebar-Link unter Tools ergaenzt.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: SeasonTeam Rating Verwaltung

**Files:**
- Modify: `src/main/resources/templates/admin/season-detail.html`
- Modify: `src/main/java/org/ctc/admin/controller/SeasonController.java`

- [ ] **Step 1: Add rating column to season-detail team table**

In `season-detail.html`, update the Teams section to show ratings and an inline edit form. Replace the simple chip-list display with a table that includes a rating input per team:

After the existing team chips section, add rating management:

```html
<div th:if="${!season.seasonTeams.isEmpty()}" style="margin-top:12px;">
    <table>
        <thead>
            <tr><th>Team</th><th>Rating</th><th>Actions</th></tr>
        </thead>
        <tbody>
            <tr th:each="st : ${season.seasonTeams}">
                <td>
                    <a th:href="@{/admin/teams/{id}(id=${st.team.id})}" th:text="${st.team.shortName}"></a>
                </td>
                <td>
                    <form th:action="@{/admin/seasons/{id}/update-rating(id=${season.id})}" method="post"
                          style="display:flex;gap:4px;align-items:center;">
                        <input type="hidden" name="seasonTeamId" th:value="${st.id}">
                        <input type="number" name="rating" th:value="${st.rating}" min="0" max="99"
                               style="width:60px;padding:4px 8px;border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--bg-input);color:var(--white);font-size:13px;">
                        <button type="submit" class="btn btn-primary btn-sm">Save</button>
                    </form>
                </td>
                <td>
                    <div style="display:flex;gap:4px;">
                        <div th:if="${st.team.primaryColor != null}" style="display:flex;gap:2px;">
                            <span th:style="'display:inline-block;width:16px;height:16px;border-radius:3px;background:' + ${st.effectivePrimaryColor}"></span>
                            <span th:style="'display:inline-block;width:16px;height:16px;border-radius:3px;background:' + ${st.effectiveSecondaryColor}"></span>
                            <span th:style="'display:inline-block;width:16px;height:16px;border-radius:3px;background:' + ${st.effectiveAccentColor}"></span>
                        </div>
                    </div>
                </td>
            </tr>
        </tbody>
    </table>
</div>
```

- [ ] **Step 2: Add update-rating endpoint to SeasonController**

```java
@PostMapping("/{id}/update-rating")
public String updateRating(@PathVariable UUID id,
                           @RequestParam UUID seasonTeamId,
                           @RequestParam(required = false) Integer rating,
                           RedirectAttributes redirectAttributes) {
    var seasonTeam = seasonTeamRepository.findById(seasonTeamId).orElseThrow();
    seasonTeam.setRating(rating);
    seasonTeamRepository.save(seasonTeam);
    redirectAttributes.addFlashAttribute("successMessage",
            "Rating updated: " + seasonTeam.getTeam().getShortName());
    return "redirect:/admin/seasons/" + id;
}
```

Add `SeasonTeamRepository` to SeasonController's dependencies.

- [ ] **Step 3: Run tests**

Run: `./mvnw verify`
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/admin/season-detail.html \
       src/main/java/org/ctc/admin/controller/SeasonController.java
git commit -m "$(cat <<'EOF'
Season-Detail: Rating-Verwaltung pro SeasonTeam

Tabelle mit Inline-Rating-Eingabe und Farb-Preview pro Team.
Neuer Endpunkt /admin/seasons/{id}/update-rating.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Docker & Finale Verifikation

**Files:**
- Modify: `Dockerfile`
- Modify: `docker-compose.yml` (ggf. Volume)

- [ ] **Step 1: Update Dockerfile for Chromium**

Add Playwright browser installation to the Dockerfile. After the JAR copy step:

```dockerfile
# Install Playwright Chromium browser for team card generation
RUN java -cp app.jar -Dloader.main=com.microsoft.playwright.CLI org.springframework.boot.loader.launch.PropertiesLauncher install chromium
```

Or if using a multi-stage build, add:

```dockerfile
RUN apt-get update && apt-get install -y \
    libnss3 libatk-bridge2.0-0 libdrm2 libxkbcommon0 libgbm1 \
    libpango-1.0-0 libcairo2 libasound2 libxshmfence1 \
    && rm -rf /var/lib/apt/lists/*
```

The exact approach depends on the current Dockerfile structure. Read the Dockerfile first and adapt accordingly.

- [ ] **Step 2: Run full test suite**

Run: `./mvnw verify`
Expected: All tests PASS.

- [ ] **Step 3: Start dev server and visually verify**

Run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`

Verify with `playwright-cli`:
1. Open `/admin/teams` — teams listed
2. Edit a team — color pickers + logo upload visible
3. Open `/admin/seasons/{id}` — rating column visible
4. Open `/admin/tools/team-cards` — season selector, team grid
5. Generate a card — thumbnail appears
6. Download PNG — correct 1080x1920 image
7. Download ZIP — contains all generated cards

- [ ] **Step 4: Commit Dockerfile changes**

```bash
git add Dockerfile docker-compose.yml
git commit -m "$(cat <<'EOF'
Docker: Chromium fuer Team Card Generierung installieren

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 5: Final commit — update documentation**

Update `CLAUDE.md` Key Files section to include:
- `TeamCardService.java` — Card generation via Playwright
- `SeasonTeam.java` — Season-Team-Verknuepfung mit Rating und Farb-/Logo-Overrides

```bash
git add CLAUDE.md
git commit -m "$(cat <<'EOF'
CLAUDE.md: TeamCardService und SeasonTeam dokumentieren

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```
