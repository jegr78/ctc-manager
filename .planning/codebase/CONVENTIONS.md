# Coding Conventions

**Analysis Date:** 2026-05-18

## Naming Patterns

**Files:**
- Entity classes: Singular noun, PascalCase (e.g., `Season.java`, `Team.java`, `Driver.java`)
- Repository classes: `{Entity}Repository` (e.g., `SeasonRepository.java`, `TeamRepository.java`)
- Service classes: `{Domain}Service` (e.g., `RaceScoringService.java`, `PlayoffService.java`)
- Controller classes: `{Entity}Controller` (e.g., `MatchScoringController.java`, `CarController.java`)
- DTO/Form classes: `{Entity}Form` for input DTOs (e.g., `MatchScoringForm.java`), `{Entity}Dto` for display DTOs
- Exception classes: descriptive name ending in `Exception` (e.g., `BusinessRuleException.java`, `EntityNotFoundException.java`)
- Inner record/DTO classes: `{ClassName}$XxxData`, `{ClassName}$XxxFormData` (e.g., `Service$XxxStanding`)

**Functions/Methods:**
- Verb-first, camelCase (e.g., `findById()`, `save()`, `calculatePoints()`, `generateMatchdays()`)
- Query methods: start with `find`, `get`, or `exists` (e.g., `findById()`, `findAll()`, `existsByName()`)
- Mutation methods: start with `save`, `update`, `delete`, or action verb (e.g., `save()`, `delete()`)

**Variables:**
- camelCase: `userId`, `matchdayId`, `playerName`
- Descriptive names: `raceScoringId` not `rsId`
- Loop counters: `i`, `j` only in simple loops; prefer descriptive names (e.g., `for (Team team : teams)`)

**Types:**
- Enum names: PascalCase (e.g., `RaceFormat`, `PhaseType`)
- Generic type parameters: Single uppercase letter (e.g., `T`, `E`) or descriptive capitalized name (e.g., `RoleType`)
- Interface names: descriptive PascalCase, no `I` prefix (e.g., `interface AuthProvider`, not `IAuthProvider`)

**Booleans:**
- Getter methods: `is` prefix (e.g., `isSubTeam()`, `isActive()`, `hasSubTeams()`)
- Field names: `isActive`, `isVerified` (descriptive, readable in templates/code)

**Database:**
- Tables: Plural, snake_case (e.g., `teams`, `seasons`, `drivers`, `race_scorings`)
- Columns: snake_case (e.g., `created_at`, `updated_at`, `season_year`, `short_name`, `primary_color`)
- Foreign keys: `{entity}_id` (e.g., `season_id`, `team_id`, `parent_team_id`)

## Code Style

**Formatting:**
- No automated formatter (Java/Spring convention-based)
- Imports ordered: Static imports first, then Java stdlib, then Jakarta/Spring, then org.ctc, then third-party
- Line length: Typically 100–120 characters (Spring Boot standard); break long method signatures
- Indentation: 4 spaces (Java convention)

**Linting:**
- SpotBugs 4.9.8.3 with find-sec-bugs 1.14.0 (Spring Security-aware patterns)
- Gate: Medium+ severity findings block the build on `./mvnw verify`
- Config: `.spotbugs-exclude.xml` at `.../config/` with XML rationale comments per suppression

**Annotation Order on Spring Components:**
Alphabetical within scope: `@Slf4j @Service @RequiredArgsConstructor` (correct), NOT `@Service @Slf4j @RequiredArgsConstructor`
- `@Slf4j` (Lombok logging) comes first alphabetically
- Lombok annotations (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@RequiredArgsConstructor`) after Spring
- Example from `RaceScoringService` (lines 15–17): `@Slf4j @Service @RequiredArgsConstructor` ✓

## Import Organization

**Order (Verified in Codebase):**
1. Static imports: `import static org.assertj.core.api.Assertions.*`
2. Java stdlib: `import java.util.*`, `import java.io.*`
3. Jakarta/Spring: `import jakarta.persistence.*`, `import org.springframework.*`
4. Project org.ctc: `import org.ctc.domain.model.*`, `import org.ctc.admin.dto.*`
5. Third-party: `import org.mockito.*`, `import com.microsoft.playwright.*`

Example from `TemplatePreviewService` (lines 3–17):
```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.MatchdayGraphicData;  // org.ctc
import org.springframework.core.io.ClassPathResource;  // Spring
import org.thymeleaf.context.Context;  // Third-party
```

**Path Aliases:**
Not used in Spring Boot configuration; all imports are absolute package paths.

## Lombok Usage

**Entities** (`src/main/java/org/ctc/domain/model/`)
- Annotations in order: `@Getter`, `@Setter`, `@NoArgsConstructor`, `@ToString(exclude = {...})`
- Must extend `BaseEntity` (provides audit fields: `createdAt`, `updatedAt`)
- Exclude bidirectional relationships in `@ToString` to avoid circular references (e.g., `@ToString(exclude = {"seasonDrivers", "parentTeam", "subTeams"})`)
- Example from `Team.java` (lines 13–18):
```java
@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"seasonDrivers", "parentTeam", "subTeams"})
```

**Services & Controllers** (`src/main/java/org/ctc/{domain|admin}/service/`, `.../controller/`)
- Annotations in alphabetical order: `@Slf4j`, then Spring (`@Service`, `@Controller`, `@Component`)
- Constructor injection via `@RequiredArgsConstructor` + `final` fields
- Example from `RaceScoringService` (lines 15–18):
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class RaceScoringService { 
  private final RaceScoringRepository raceScoringRepository;
}
```

**DTOs** (`src/main/java/org/ctc/admin/dto/`)
- `@Getter`, `@Setter`, `@NoArgsConstructor` (form DTOs must be mutable for Spring MVC binding)
- No `@ToString` needed on form DTOs (spring-web-test auto-discovers setters)
- Example from `MatchScoringForm.java` (lines 8–12):
```java
@Getter
@Setter
@NoArgsConstructor
public class MatchScoringForm { ... }
```

**Lombok Configuration Invariant:**
- `lombok.config` at project root sets `lombok.extern.findbugs.addSuppressFBWarnings=true` (must NOT be removed)
- This causes Lombok to emit `@SuppressFBWarnings` on generated methods, reducing ~80 `EI_EXPOSE_REP*` false positives from entity getters
- SpotBugs exclusion filter at `config/spotbugs-exclude.xml` provides belt-and-braces suppression

## Form Binding & DTOs

**Form Input Pattern:**
- Controllers bind HTTP POST data via Form DTOs (never JPA entities directly) — protection against Mass Assignment attacks
- `@Valid` + `BindingResult` for validation
- Example from `MatchScoringController.java` (lines 52–56):
```java
@PostMapping("/save")
public String save(@Valid @ModelAttribute("matchScoringForm") MatchScoringForm form, BindingResult result,
                   RedirectAttributes redirectAttributes) {
    if (result.hasErrors()) {
        return "admin/match-scoring-form";
    }
    // ... proceed with service
}
```

**Display Pattern:**
- GET endpoints pass entities directly to model (OSIV is enabled, lazy loading works in templates)
- Example from `MatchScoringController.java` (line 26):
```java
@GetMapping
public String list(Model model) {
    model.addAttribute("scorings", matchScoringService.findAll());
    return "admin/match-scoring-list";
}
```

**Flash Attributes for Feedback:**
- `redirectAttributes.addFlashAttribute("successMessage", "...")` for success
- `redirectAttributes.addFlashAttribute("errorMessage", "...")` for errors
- Example from `MatchScoringController.java` (lines 60, 62):
```java
redirectAttributes.addFlashAttribute("successMessage", "Match-Scoring saved: " + form.getName());
redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
```

## Error Handling

**Exception Hierarchy:**
- `BusinessRuleException` (checked domain violations — e.g., "Cannot delete — still referenced by a season")
- `EntityNotFoundException` (requested entity not found)
- `ValidationException` (input validation failures)
- All extend `RuntimeException` (unchecked; Spring catches them)

**Patterns:**
- Repositories: Throw `EntityNotFoundException` when `.findById()` returns empty
- Services: Catch `DataIntegrityViolationException` → re-throw as `BusinessRuleException` with user-friendly message
- Controllers: Catch `BusinessRuleException` → add to flash attributes, redirect to list view
- Example from `RaceScoringService.java` (lines 42–59):
```java
try {
    var saved = raceScoringRepository.saveAndFlush(scoring);
    log.info("Updated race scoring: {}", saved.getName());
    return saved;
} catch (DataIntegrityViolationException e) {
    throw new BusinessRuleException("A race scoring with this name already exists");
}
```

## Logging

**Framework:**
- Slf4j (Logback), injected via `@Slf4j` annotation

**Levels:**
- `log.info()` for state changes, user-visible operations (e.g., "Created race scoring: CTC Standard")
- `log.debug()` for calculations, internal logic (e.g., "Calculated driver ranking for phase {}")
- `log.warn()` for unexpected but non-fatal conditions (e.g., "Path traversal attempt in download")
- `log.error()` for exceptions and failures (with exception object)

**Format:**
- Always use parameterized placeholders `{}`, never string concatenation
- Example from `RaceScoringService.java` (line 49):
```java
log.info("Updated race scoring: {}", saved.getName());  // ✓ Parameterized
```
- NOT: `log.info("Updated race scoring: " + saved.getName())` ✗

**Message Style:**
- Past tense for state-changes: "Created", "Updated", "Deleted"
- Include entity identifiers/names for debugging: `"Updated race scoring: {}" → "Updated race scoring: CTC Standard"`
- Example from `TestDataService.java` (line 86):
```java
log.info("Seed data created: {} teams, {} seasons, {} drivers", 
         teamRepository.count(), seasonRepository.count(), driverRepository.count());
```

## Comments

**When to Comment:**
- Explain WHY, not WHAT (code explains WHAT)
- Non-obvious business rules, constraints, or performance considerations
- References to architectural decisions or specs
- Public method/class Javadoc for API clarity

**What NOT to Comment:**
- Obvious method names (e.g., `save()`, `findById()`)
- Repeating parameter/variable names (e.g., `String name` → no "the name of the entity")
- Block comments summarizing code that is already self-explanatory

**Javadoc/JavaDoc:**
- Used on public classes and public methods (esp. services, controllers)
- Describe parameters, return value, and exceptions
- Example from `V4MigrationSmokeIT.java` (lines 20–36): Multi-line explanation of test purpose and behavior

## Templates (Thymeleaf)

**Lean Template Pattern:**
- No complex SpEL expressions, collection projections, or nested conditions
- No calculations or derived data in templates
- Data preparation belongs in services — templates are for presentation only
- Example of GOOD template from `match-scoring-list.html` (line 18):
```html
<td><strong th:text="${s.name}"></strong></td>  <!-- Simple variable output ✓ -->
```
- Example of AVOIDED pattern:
```html
<!-- ✗ Not done: -->
<td th:text="${s.results.stream().filter(r -> r.isValid()).count()}"></td>
<!-- ✓ Instead: pre-calculate count in controller/service -->
```

**OSIV Enabled:**
- `spring.jpa.open-in-view=true` is deliberately enabled
- Thymeleaf can safely render lazy-loaded fields without extra service calls
- No workarounds needed (e.g., no `@EntityGraph` needed in GET methods unless optimizing queries)

## CSS Guidelines

**Pattern: CSS Classes Over Inline Styles**
- All buttons use CSS classes from `admin.css`, NEVER inline `style="..."`
- Button classes: `.btn-xs`, `.btn-sm`, `.btn-lg`, `.btn-tab` (size variants)
- Example GOOD from `match-scoring-list.html` (line 23):
```html
<a class="btn btn-secondary btn-sm">Edit</a>  <!-- ✓ Use CSS classes -->
```
- Example BAD (AVOIDED):
```html
<button style="padding:6px 12px; font-size:13px;">Edit</button>  <!-- ✗ Inline styles -->
```

**Button CSS Classes Available** (from `admin.css` lines 234–246):
- `.btn-primary` (blue, #1976d2)
- `.btn-secondary` (dark gray, #2a2a2a)
- `.btn-danger` (red, #d32f2f)
- `.btn-success` (green, #2e7d32)
- `.btn-xs` (tiny: 2px 8px, 11px font)
- `.btn-sm` (small: 6px 12px, 13px font)
- `.btn-lg` (large: 12px 24px, 16px font)
- `.btn-tab` (top-border radius, no bottom border)

**CSS Class Coverage:**
- When refactoring inline styles to CSS classes, verify that JavaScript setting `element.className = '...'` includes the new classes
- All color/spacing rules are defined as CSS variables in `:root` (--bg, --accent, --success, --danger, etc.)

**UI Language:**
- All UI text, labels, buttons, and error messages: English only
- No exceptions; German used only in code comments/documentation
- Example from controllers: "Match-Scoring saved", "Really delete this scoring?", "New Match-Scoring"

## Module Design

**Exports:**
- Services export business logic via public methods
- Controllers export HTTP routes via `@RequestMapping` + `@GetMapping`/`@PostMapping`
- Repositories are autowired into services (never directly in controllers)

**Barrel Files / Re-exports:**
- Not used in this project; all imports are explicit package paths

**API Stability:**
- Public method signatures are kept stable — breaking changes require architectural review
- Backward-compatible changes: add new overloads, deprecate old ones if needed
- Example: `save(UUID id, String name, ...)` is stable contract
