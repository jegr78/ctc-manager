# Phase 56: Model & Schema Foundation - Pattern Map

**Mapped:** 2026-04-26
**Files analyzed:** 13 (8 create + 3 modify + 2 tests)
**Analogs found:** 13 / 13

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/domain/model/SeasonPhase.java` | entity (parent-with-children) | CRUD | `src/main/java/org/ctc/domain/model/Season.java` | exact |
| `src/main/java/org/ctc/domain/model/SeasonPhaseGroup.java` | entity (child of phase) | CRUD | `src/main/java/org/ctc/domain/model/Matchday.java` | exact (same OneToMany child shape) |
| `src/main/java/org/ctc/domain/model/PhaseTeam.java` | entity (join table with extras) | CRUD | `src/main/java/org/ctc/domain/model/SeasonTeam.java` | exact |
| `src/main/java/org/ctc/domain/model/PhaseType.java` | enum (top-level) | n/a | `src/main/java/org/ctc/domain/model/SeasonFormat.java` | exact |
| `src/main/java/org/ctc/domain/model/PhaseLayout.java` | enum (top-level) | n/a | `src/main/java/org/ctc/domain/model/SeasonFormat.java` | exact |
| `src/main/java/org/ctc/domain/repository/SeasonPhaseRepository.java` | repository (CRUD-only) | CRUD | `src/main/java/org/ctc/domain/repository/PlayoffRepository.java` | role-match (default JpaRepository) |
| `src/main/java/org/ctc/domain/repository/SeasonPhaseGroupRepository.java` | repository (CRUD-only) | CRUD | `src/main/java/org/ctc/domain/repository/PlayoffRepository.java` | role-match |
| `src/main/java/org/ctc/domain/repository/PhaseTeamRepository.java` | repository (CRUD-only) | CRUD | `src/main/java/org/ctc/domain/repository/SeasonTeamRepository.java` | exact |
| `src/main/resources/db/migration/V3__add_season_phase_tables.sql` | migration (DDL additive) | batch | `src/main/resources/db/migration/V1__initial_schema.sql` (DDL style) + `V2__add_fk_indexes.sql` (index style) | exact |
| `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` | test (persistence integration) | CRUD | `src/test/java/org/ctc/domain/model/BaseEntityAuditTest.java` | exact (`@SpringBootTest` + `@ActiveProfiles("dev")` is the project's persistence test convention; **note: the codebase has no `@DataJpaTest` examples** — D-06 / CONTEXT-recommendation should be re-mapped to `@SpringBootTest`) |
| `src/test/java/org/ctc/domain/model/PhaseTeamUniquenessIntegrationTest.java` | test (DB constraint) | CRUD | `src/test/java/org/ctc/domain/model/BaseEntityAuditTest.java` + `DataIntegrityViolationException` usage from `DriverServiceTest.java:21,452` | partial (constraint-violation pattern is mocked elsewhere; no real-DB precedent) |
| **MODIFY:** `src/main/java/org/ctc/domain/model/Season.java` | entity field add | CRUD | self (existing `Season.matchdays`) | exact (template inside the same file) |
| **MODIFY:** `src/main/java/org/ctc/domain/model/Matchday.java` | entity field add | CRUD | self (existing `Matchday.season`) | exact |
| **MODIFY:** `src/main/java/org/ctc/domain/model/Playoff.java` | entity field add | CRUD | self (existing `Playoff.season` with `unique = true`) | exact |

## Pattern Assignments

### `src/main/java/org/ctc/domain/model/SeasonPhase.java` (entity, CRUD)

**Analog:** `src/main/java/org/ctc/domain/model/Season.java` (parent-with-children + scoring-FK + format-enum + collection-of-children)

**Imports pattern** (Season.java:1-12):
```java
package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.util.*;
```

**Class-level annotations** (Season.java:14-20):
```java
@Entity
@Table(name = "seasons")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"matchdays", "seasonDrivers", "seasonTeams", "cars", "tracks", "raceScoring", "matchScoring"})
public class Season extends BaseEntity {
```

For `SeasonPhase`: `@Table(name = "season_phases")`, `@ToString(exclude = {"season", "groups", "raceScoring", "matchScoring"})`.

**ID + UUID** (Season.java:22-24):
```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;
```

**Owning ManyToOne back to parent** (Matchday.java:26-28 — analog for `SeasonPhase.season`):
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "season_id", nullable = false)
private Season season;
```

**Sort index field** (Matchday.java:34-35):
```java
@Column(nullable = false)
private int sortIndex;
```

**Enum field with VARCHAR(20) + STRING strategy** (Season.java:45-47):
```java
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private SeasonFormat format = SeasonFormat.LEAGUE;
```

For `SeasonPhase` apply this **three times**: `phaseType` (PhaseType, no default — required), `layout` (PhaseLayout, no default — required), `format` (SeasonFormat reused per D-04).

**Movable Season fields that get carried over to SeasonPhase** (Season.java:49-63 — copy verbatim, same column names, same nullability):
```java
private Integer totalRounds;

@Column(nullable = false)
private int legs = 1;

@Column(name = "event_duration_minutes")
private Integer eventDurationMinutes;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "race_scoring_id", nullable = false)
private RaceScoring raceScoring;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "match_scoring_id", nullable = false)
private MatchScoring matchScoring;
```

Plus `private LocalDate startDate;` and `private LocalDate endDate;` (Season.java:38-40 — both nullable).

**OneToMany child collection (groups)** (Season.java:65-67 — template for `SeasonPhase.groups`):
```java
@OneToMany(mappedBy = "season", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("sortIndex ASC")
private List<Matchday> matchdays = new ArrayList<>();
```

For `SeasonPhase`: `mappedBy = "phase"`, type `List<SeasonPhaseGroup>`.

**Convenience constructor** (Season.java:89-97):
```java
public Season(String name) { this.name = name; }
public Season(String name, int year, int number) { ... }
```

For `SeasonPhase` add: `public SeasonPhase(Season season, PhaseType phaseType, PhaseLayout layout, int sortIndex)`.

---

### `src/main/java/org/ctc/domain/model/SeasonPhaseGroup.java` (entity, CRUD)

**Analog:** `src/main/java/org/ctc/domain/model/Matchday.java` (small child entity: parent FK + label/name + sortIndex + nothing else complex)

**Full file template** (Matchday.java:1-48):
```java
package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "matchdays")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"season", "matches", "races"})
public class Matchday extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "season_id", nullable = false)
	private Season season;

	@NotBlank
	@Column(nullable = false)
	private String label;

	@Column(nullable = false)
	private int sortIndex;

	// ...child collections...

	public Matchday(Season season, String label, int sortIndex) {
		this.season = season;
		this.label = label;
		this.sortIndex = sortIndex;
	}
}
```

For `SeasonPhaseGroup`:
- `@Table(name = "season_phase_groups")`
- `@ToString(exclude = {"phase"})` (no children)
- Parent FK is `phase` → `@JoinColumn(name = "phase_id", nullable = false)` of type `SeasonPhase`
- Field is `name` (not `label`) per CONTEXT.md
- Constructor: `(SeasonPhase phase, String name, int sortIndex)`

---

### `src/main/java/org/ctc/domain/model/PhaseTeam.java` (entity, CRUD — join entity)

**Analog:** `src/main/java/org/ctc/domain/model/SeasonTeam.java` (join entity with UUID PK + two `@ManyToOne` parents + UNIQUE business key)

**Full file template** (SeasonTeam.java:1-50, structural lines only):
```java
package org.ctc.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "season_teams")
@Getter
@Setter
@NoArgsConstructor
public class SeasonTeam extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "season_id", nullable = false)
	private Season season;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	// ...optional payload columns...

	public SeasonTeam(Season season, Team team) {
		this.season = season;
		this.team = team;
	}
}
```

For `PhaseTeam`:
- `@Table(name = "phase_teams")`
- Add `@ToString` (omit) — SeasonTeam has none either, so PhaseTeam may also omit; if added, exclude `{"phase", "team", "group"}`
- Three FKs:
  - `@JoinColumn(name = "phase_id", nullable = false)` → `SeasonPhase phase`
  - `@JoinColumn(name = "team_id", nullable = false)` → `Team team`
  - `@JoinColumn(name = "group_id")` (nullable!) → `SeasonPhaseGroup group`
- Constructor: `(SeasonPhase phase, Team team)` (group optional via setter)

**UNIQUE business-key pattern** is enforced **at the DB level** (see V3 migration below); SeasonTeam.java does not declare the UNIQUE in the entity (only in V1__initial_schema.sql:97 `CONSTRAINT uk_season_team UNIQUE (season_id, team_id)`). Follow the same approach for PhaseTeam — no `@Table(uniqueConstraints = …)` on the entity.

---

### `src/main/java/org/ctc/domain/model/PhaseType.java` and `PhaseLayout.java` (enum)

**Analog:** `src/main/java/org/ctc/domain/model/SeasonFormat.java` (top-level enum, one file, no annotations)

**Full template** (SeasonFormat.java:1-7):
```java
package org.ctc.domain.model;

public enum SeasonFormat {
	LEAGUE,
	SWISS,
	ROUND_ROBIN
}
```

For `PhaseType`: values `REGULAR`, `PLAYOFF`, `PLACEMENT`.
For `PhaseLayout`: values `LEAGUE`, `GROUPS`, `BRACKET`.

`AttachmentType.java` (also a 3-line top-level enum) confirms the convention is universal.

---

### `src/main/java/org/ctc/domain/repository/SeasonPhaseRepository.java` (repository, CRUD)

**Analog:** `src/main/java/org/ctc/domain/repository/PlayoffRepository.java` (smallest existing repo — confirms the "default CRUD only" baseline locked by D-06)

**Full file template** (PlayoffRepository.java:1-11, stripped of custom finders):
```java
package org.ctc.domain.repository;

import org.ctc.domain.model.Playoff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlayoffRepository extends JpaRepository<Playoff, UUID> {
}
```

For `SeasonPhaseRepository`: same shape with `SeasonPhase`/`UUID`. **No custom finders, no `@EntityGraph`** in Phase 56 (D-06). Same for `SeasonPhaseGroupRepository` and `PhaseTeamRepository`.

If a planner-discretion seed `@EntityGraph` is wanted later (CONTEXT.md "Claude's Discretion"), the canonical reference is `SeasonRepository.java:15` — but per D-06 default is **omit**.

---

### `src/main/resources/db/migration/V3__add_season_phase_tables.sql` (migration, additive DDL)

**Analog A — DDL style:** `src/main/resources/db/migration/V1__initial_schema.sql`

**Header comment** (V1:1-2):
```sql
-- CTC Manager: Complete schema
-- Compatible with H2 2.x and MariaDB 10.7+
```

**Header comment for V3** (use same style):
```sql
-- Add Season Phase tables (Season → Phase → Group hierarchy)
-- Adds nullable phase_id/group_id FKs on matchdays and playoffs (additive; non-null flip in V?? after data migration)
-- Compatible with H2 2.x and MariaDB 10.7+
```

**CREATE TABLE pattern with audit columns + FK constraint + UNIQUE** (V1:24-43, the `seasons` table — mirror this almost verbatim for `season_phases`):
```sql
CREATE TABLE seasons (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    season_year INT NOT NULL,
    season_number INT NOT NULL,
    description VARCHAR(255),
    start_date DATE,
    end_date DATE,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    format VARCHAR(20) DEFAULT 'LEAGUE' NOT NULL,
    total_rounds INT,
    legs INT NOT NULL DEFAULT 1,
    event_duration_minutes INT,
    race_scoring_id UUID NOT NULL,
    match_scoring_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_season_race_scoring FOREIGN KEY (race_scoring_id) REFERENCES race_scorings(id),
    CONSTRAINT fk_season_match_scoring FOREIGN KEY (match_scoring_id) REFERENCES match_scorings(id)
);
```

For `season_phases` use:
- `id UUID PRIMARY KEY`
- `season_id UUID NOT NULL` (+ `CONSTRAINT fk_seasonphase_season FOREIGN KEY ... REFERENCES seasons(id)`)
- `sort_index INT NOT NULL`
- `phase_type VARCHAR(20) NOT NULL`
- `layout VARCHAR(20) NOT NULL`
- `format VARCHAR(20) DEFAULT 'LEAGUE' NOT NULL`
- `total_rounds INT`, `legs INT NOT NULL DEFAULT 1`, `event_duration_minutes INT`
- `start_date DATE`, `end_date DATE`
- `race_scoring_id UUID NOT NULL`, `match_scoring_id UUID NOT NULL`
- audit columns (verbatim two lines)
- `CONSTRAINT fk_seasonphase_race_scoring FOREIGN KEY (race_scoring_id) REFERENCES race_scorings(id)`
- `CONSTRAINT fk_seasonphase_match_scoring FOREIGN KEY (match_scoring_id) REFERENCES match_scorings(id)`
- `CONSTRAINT uk_season_phase_type UNIQUE (season_id, phase_type)` — D-03

**Join-entity-with-extras CREATE pattern** (V1:81-98, the `season_teams` table — direct analog for `phase_teams`):
```sql
CREATE TABLE season_teams (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL,
    team_id UUID NOT NULL,
    rating INTEGER,
    primary_color VARCHAR(7),
    -- ...
    CONSTRAINT fk_st_season FOREIGN KEY (season_id) REFERENCES seasons(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_st_team FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT fk_st_successor FOREIGN KEY (successor_season_team_id) REFERENCES season_teams(id),
    CONSTRAINT uk_season_team UNIQUE (season_id, team_id)
);
```

For `phase_teams` apply:
```sql
CREATE TABLE phase_teams (
    id UUID PRIMARY KEY,
    phase_id UUID NOT NULL,
    team_id UUID NOT NULL,
    group_id UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_phaseteam_phase FOREIGN KEY (phase_id) REFERENCES season_phases(id),
    CONSTRAINT fk_phaseteam_team FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT fk_phaseteam_group FOREIGN KEY (group_id) REFERENCES season_phase_groups(id),
    CONSTRAINT uk_phase_team UNIQUE (phase_id, team_id)
);
```

**Simple child-of-parent CREATE pattern** (V1:138-146, the `matchdays` table — direct analog for `season_phase_groups`):
```sql
CREATE TABLE matchdays (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL,
    label VARCHAR(255) NOT NULL,
    sort_index INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_md_season FOREIGN KEY (season_id) REFERENCES seasons(id)
);
```

For `season_phase_groups`:
```sql
CREATE TABLE season_phase_groups (
    id UUID PRIMARY KEY,
    phase_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    sort_index INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_seasonphasegroup_phase FOREIGN KEY (phase_id) REFERENCES season_phases(id)
);
```

**Adding nullable FK column to existing table** — V1 has no analog (initial schema only); standard ANSI SQL works on H2+MariaDB:
```sql
ALTER TABLE matchdays ADD COLUMN phase_id UUID;
ALTER TABLE matchdays ADD COLUMN group_id UUID;
ALTER TABLE matchdays ADD CONSTRAINT fk_matchday_phase FOREIGN KEY (phase_id) REFERENCES season_phases(id);
ALTER TABLE matchdays ADD CONSTRAINT fk_matchday_group FOREIGN KEY (group_id) REFERENCES season_phase_groups(id);

ALTER TABLE playoffs ADD COLUMN phase_id UUID;
ALTER TABLE playoffs ADD CONSTRAINT fk_playoff_phase FOREIGN KEY (phase_id) REFERENCES season_phases(id);
ALTER TABLE playoffs ADD CONSTRAINT uk_playoff_phase UNIQUE (phase_id);
```

The `playoffs` UNIQUE on `phase_id` mirrors V1:173 `CONSTRAINT uk_playoff_season UNIQUE (season_id)` — same idea (one phase = at most one playoff).

**Analog B — FK index style:** `src/main/resources/db/migration/V2__add_fk_indexes.sql`

**Header comment** (V2:1-2):
```sql
-- Add indexes on all foreign key columns for query performance
-- Uses IF NOT EXISTS for H2 and MariaDB compatibility
```

**Naming + `IF NOT EXISTS` pattern** (V2:5, 22, 30):
```sql
CREATE INDEX IF NOT EXISTS idx_seasons_race_scoring_id ON seasons(race_scoring_id);
CREATE INDEX IF NOT EXISTS idx_matchdays_season_id ON matchdays(season_id);
CREATE INDEX IF NOT EXISTS idx_playoffs_season_id ON playoffs(season_id);
```

For V3 add — at the end of the file, after CREATE TABLEs and ALTER TABLEs — these eight indexes (all FK columns introduced by V3):
```sql
CREATE INDEX IF NOT EXISTS idx_season_phases_season_id ON season_phases(season_id);
CREATE INDEX IF NOT EXISTS idx_season_phases_race_scoring_id ON season_phases(race_scoring_id);
CREATE INDEX IF NOT EXISTS idx_season_phases_match_scoring_id ON season_phases(match_scoring_id);
CREATE INDEX IF NOT EXISTS idx_season_phase_groups_phase_id ON season_phase_groups(phase_id);
CREATE INDEX IF NOT EXISTS idx_phase_teams_phase_id ON phase_teams(phase_id);
CREATE INDEX IF NOT EXISTS idx_phase_teams_team_id ON phase_teams(team_id);
CREATE INDEX IF NOT EXISTS idx_phase_teams_group_id ON phase_teams(group_id);
CREATE INDEX IF NOT EXISTS idx_matchdays_phase_id ON matchdays(phase_id);
CREATE INDEX IF NOT EXISTS idx_matchdays_group_id ON matchdays(group_id);
CREATE INDEX IF NOT EXISTS idx_playoffs_phase_id ON playoffs(phase_id);
```

**FK constraint naming convention** — observed across V1: `fk_<short>_<target>`, where `<short>` is the source table abbreviated (`md`, `st`, `pm`, `pr`, `rr`, `rl`, `ra`, `ps`, `sd`, `sc`). Phase 56 uses unabbreviated forms (`fk_matchday_phase`, `fk_phaseteam_phase`, …) per CONTEXT.md D-02 — this is a deliberate departure for readability of the new constraints. Index naming uses **full table names** (`idx_matchdays_phase_id`) — that is V2's pattern.

**`ON DELETE` policy** — V1 uses **plain `REFERENCES`** (default = RESTRICT) for non-cascade FKs; only `season_cars`, `season_tracks`, `psn_aliases`, `race_attachments` use `ON DELETE CASCADE`. CONTEXT.md D-02 locks all V3 FKs to **DEFAULT (RESTRICT)** — JPA `orphanRemoval=true` handles cascade at the application layer. **Do NOT add `ON DELETE CASCADE`** to any V3 FK.

---

### `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` (test, persistence integration)

**Analog:** `src/test/java/org/ctc/domain/model/BaseEntityAuditTest.java`

**IMPORTANT FINDING:** The codebase has **zero `@DataJpaTest` usages**. The single existing persistence-layer integration test (`BaseEntityAuditTest`) uses `@SpringBootTest` + `@ActiveProfiles("dev")`. CONTEXT.md "Claude's Discretion" recommends `@DataJpaTest`, but the project precedent is `@SpringBootTest` + `dev` profile (H2 in-memory, full Spring context, AuditingEntityListener active). **Recommendation for the planner: follow the project precedent unless there's a strong reason to introduce `@DataJpaTest` here.**

**Full template** (BaseEntityAuditTest.java:1-32):
```java
package org.ctc.domain.model;

import org.ctc.domain.repository.DriverRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class BaseEntityAuditTest {

	@Autowired
	private DriverRepository driverRepository;

	@Test
	void givenNewEntity_whenSaved_thenCreatedAtAndUpdatedAtAreSet() {
		// given
		var driver = new Driver("audit_test_psn", "Audit Test");
		assertNull(driver.getCreatedAt());
		assertNull(driver.getUpdatedAt());

		// when
		var saved = driverRepository.save(driver);

		// then
		assertNotNull(saved.getCreatedAt());
		assertNotNull(saved.getUpdatedAt());
	}
}
```

**Pure-POJO entity test pattern (no Spring)** — `src/test/java/org/ctc/domain/model/SeasonTeamTest.java` is the cleanest analog: no annotation, plain JUnit 5, `@Nested` classes for grouping, AssertJ. Use this for **constructor + relationship-wiring** smoke tests that don't need persistence.

**Test method naming** (BaseEntityAuditTest.java:19, 34): `givenContext_whenAction_thenExpectedResult` — mandated by CLAUDE.md "Test Naming (Given-When-Then)". With `// given` / `// when` / `// then` body comments.

---

### `src/test/java/org/ctc/domain/model/PhaseTeamUniquenessIntegrationTest.java` (test, DB constraint)

**Analog A:** `BaseEntityAuditTest` (Spring boot harness — see above).
**Analog B:** `src/test/java/org/ctc/domain/service/DriverServiceTest.java:21,452` (the `DataIntegrityViolationException` import + assertion idiom — but mocked there, not real-DB).

**Constraint-violation real-DB pattern (no in-tree precedent)** — derive from AssertJ + Spring's `DataIntegrityViolationException`:
```java
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test
void givenDuplicatePhaseTeam_whenSave_thenViolatesUniqueConstraint() {
	// given: a phase + team + first PhaseTeam saved
	// when / then
	assertThatThrownBy(() -> phaseTeamRepository.saveAndFlush(duplicate))
			.isInstanceOf(DataIntegrityViolationException.class);
}
```

**Note:** combine `// when / then` for exception assertions per CLAUDE.md ("For exception tests: `// when / then` combined for `assertThatThrownBy`.").

---

### MODIFY: `src/main/java/org/ctc/domain/model/Season.java` — add `phases` collection

**Analog:** the existing `Season.matchdays` field on the same file.

**Excerpt to copy** (Season.java:65-67):
```java
@OneToMany(mappedBy = "season", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("sortIndex ASC")
private List<Matchday> matchdays = new ArrayList<>();
```

**Apply as:**
```java
@OneToMany(mappedBy = "season", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("sortIndex ASC")
private List<SeasonPhase> phases = new ArrayList<>();
```

**Also update `@ToString(exclude = …)`** — the existing list (Season.java:19) is `{"matchdays", "seasonDrivers", "seasonTeams", "cars", "tracks", "raceScoring", "matchScoring"}`. Add `"phases"` to the front.

**Do NOT** modify any movable field on Season (CONTEXT.md D-01: those stay until Phase 61).

---

### MODIFY: `src/main/java/org/ctc/domain/model/Matchday.java` — add `phase` and `group` ManyToOne

**Analog:** the existing `Matchday.season` field on the same file.

**Excerpt to copy** (Matchday.java:26-28):
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "season_id", nullable = false)
private Season season;
```

**Apply as two new fields (both nullable per D-01):**
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "phase_id")
private SeasonPhase phase;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "group_id")
private SeasonPhaseGroup group;
```

**Also update `@ToString(exclude = …)`** — current list is `{"season", "matches", "races"}` (Matchday.java:19). Add `"phase", "group"`.

---

### MODIFY: `src/main/java/org/ctc/domain/model/Playoff.java` — add `phase` ManyToOne (UNIQUE)

**Analog:** the existing `Playoff.season` field on the same file (which already uses `unique = true` — exact precedent for the UNIQUE on `phase_id`).

**Excerpt to copy** (Playoff.java:28-31):
```java
@NotNull
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "season_id", nullable = false, unique = true)
private Season season;
```

**Apply as (nullable in Phase 56 — no `@NotNull`, no `nullable = false`; UNIQUE stays):**
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "phase_id", unique = true)
private SeasonPhase phase;
```

**Also update `@ToString(exclude = …)`** — current list is `{"season", "seasons", "rounds", "seeds"}` (Playoff.java:21). Add `"phase"`.

**Do NOT** touch `Playoff.season`, `Playoff.seasons`, or the `playoff_seasons` join table (CONTEXT.md "Deferred Ideas" — Phase 61).

---

## Shared Patterns

### Lombok Annotations on Entities
**Source:** every entity in `src/main/java/org/ctc/domain/model/` (Season:14-19, Matchday:14-19, SeasonTeam:11-15)
**Apply to:** all three new entities
```java
@Entity
@Table(name = "<plural_snake_case>")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {<lazy collections + parent ManyToOne fields>})
public class <Entity> extends BaseEntity {
```
- `@ToString(exclude = …)` is **mandatory** wherever there's a `@ManyToOne` parent or a `@OneToMany` collection — prevents lazy-init triggers and circular references (CONVENTIONS.md §"Lombok Usage").
- `SeasonTeam.java` shows it's acceptable to **omit `@ToString` entirely** for a small join entity (no `@OneToMany`, parents already cycle-safe). For `PhaseTeam` either omission or explicit exclude of `{"phase","team","group"}` is fine.

### UUID Primary Key
**Source:** `BaseEntity` is **not** the source — every entity declares its own `id` (Season:22-24, Matchday:22-24, SeasonTeam:18-20).
**Apply to:** all three new entities — copy verbatim:
```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;
```

### LAZY ManyToOne with snake_case JoinColumn
**Source:** Matchday.java:26-28, SeasonTeam.java:22-28, Season.java:57-63
**Apply to:** every parent FK on new entities — `fetch = FetchType.LAZY` is **mandatory** (CONVENTIONS.md §"Relationships").
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "<entity>_id", nullable = …)
private <ParentEntity> <field>;
```

### Audit Columns via BaseEntity
**Source:** `BaseEntity.java:18-27` + `V1__initial_schema.sql:10-11` (every CREATE TABLE).
**Apply to:** all three new tables — every CREATE TABLE in V3 must end with:
```sql
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
```

### H2 + MariaDB Compatibility
**Source:** `V1__initial_schema.sql:1-2`, `V2__add_fk_indexes.sql:1-2`
**Apply to:** V3 — header comment, plain ANSI SQL, no MariaDB-specific syntax (no `IF EXISTS` on `ALTER TABLE … ADD CONSTRAINT` — H2 does not support it on constraints; do support it on indexes via `CREATE INDEX IF NOT EXISTS`).
- `UUID` type works on both.
- `VARCHAR(20)` for enum strings (V1:33 — the `format` column).
- `TIMESTAMP DEFAULT CURRENT_TIMESTAMP` works on both.
- **No checksum changes to V1/V2** — they are frozen (CLAUDE.md §"Constraints").

### Test Method Naming
**Source:** CLAUDE.md §"Test Naming (Given-When-Then)" + BaseEntityAuditTest.java:19,34 + SeasonTeamTest (every method)
**Apply to:** both test files
- Method: `givenContext_whenAction_thenExpectedResult()`
- Body: `// given` / `// when` / `// then` block comments
- Exceptions: `// when / then` combined with `assertThatThrownBy`

### Repository Default-CRUD Baseline
**Source:** PlayoffRepository.java:11 (when stripped of custom finders)
**Apply to:** all three new repositories per D-06
```java
public interface <Entity>Repository extends JpaRepository<<Entity>, UUID> {
}
```
No `@EntityGraph` annotations in Phase 56. Phase 58 introduces them when service queries materialise.

### Import Order (CONVENTIONS.md §"Import Organization")
**Apply to:** all three new entity files + tests
1. Project (`org.ctc.*`) — only when not in same package
2. Jakarta (`jakarta.persistence.*`, `jakarta.validation.*`)
3. Lombok (`lombok.*`)
4. Spring (`org.springframework.*`)
5. Java standard library (`java.*`)

Wildcard for own package + `java.util.*` is acceptable (Season.java:11 uses `import java.util.*;`).

---

## No Analog Found

Files with no close match in the codebase:

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| (none) | — | — | All Phase 56 files have direct in-tree analogs. |

**Caveats / partial matches** (planner should be aware):

| File | Concern | Mitigation |
|------|---------|------------|
| `SeasonPhaseEntityIntegrationTest.java` | No `@DataJpaTest` usage in the codebase | Use `@SpringBootTest @ActiveProfiles("dev")` per `BaseEntityAuditTest.java`; if planner insists on `@DataJpaTest`, this would be the first one — flag in PR description |
| `PhaseTeamUniquenessIntegrationTest.java` | No real-DB constraint-violation test in the codebase (only mocked) | Combine the `BaseEntityAuditTest` harness with `assertThatThrownBy(...).isInstanceOf(DataIntegrityViolationException.class)` — derived pattern, not copied |
| `V3` ALTER TABLE for adding FK columns | No precedent (V1 is initial, V2 only adds indexes) | Plain ANSI `ALTER TABLE … ADD COLUMN` + `ADD CONSTRAINT` — supported by both H2 and MariaDB; avoid `IF NOT EXISTS` on `ADD CONSTRAINT` |
| FK constraint naming `fk_seasonphase_*` (full names) | V1 uses abbreviations (`fk_md_season`, `fk_st_team`, etc.) | CONTEXT.md D-02 deliberately specifies full names — note the deviation in the migration file header comment |

---

## Metadata

**Analog search scope:**
- `src/main/java/org/ctc/domain/model/` (entities + enums)
- `src/main/java/org/ctc/domain/repository/` (repositories)
- `src/main/resources/db/migration/` (Flyway migrations)
- `src/test/java/org/ctc/domain/` (entity + persistence tests)

**Files scanned:** 6 entity files, 5 repository files, 2 migrations, 4 tests.
**Files read end-to-end:** Season.java, Matchday.java, Playoff.java, SeasonTeam.java, SeasonFormat.java, AttachmentType.java, BaseEntity.java, SeasonRepository.java, MatchdayRepository.java, SeasonTeamRepository.java, PlayoffRepository.java, SeasonDriverRepository.java, V1__initial_schema.sql, V2__add_fk_indexes.sql, BaseEntityAuditTest.java, SeasonTeamTest.java, SeasonTest.java (partial).

**Pattern extraction date:** 2026-04-26
