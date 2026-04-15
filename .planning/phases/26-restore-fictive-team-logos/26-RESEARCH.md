# Phase 26: Restore Fictive Team Logos - Research

**Researched:** 2026-04-10
**Domain:** Dev seed data / static resources / file management
**Confidence:** HIGH

## Summary

Phase 23 (commit `2ba6f03`) deleted all 10 fictive team logo PNGs (VRX, SGM, ADR, TBR, ICL, SVT, NFR, EGP, HMS, PWR) from `src/main/resources/demo/team-logos/` and replaced them with 10 real CTC logos (AHR, ART, CLR, DTR, GXR, MRL, P1R, TCR, TNR, VEZ). Phase 24 restored the Java code in `TestDataService.java` to use fictive teams but did NOT restore the logo PNG files. The result: `copyDemoLogos()` runs on every dev startup, finds no matching files for any fictive team short name, logs zero logo copies silently, and leaves all teams without a `logoUrl`. TeamCardService downstream then generates team cards without logos.

The fix is a simple file restore: extract the 10 fictive PNGs from git history (commit `3e640f9`) and commit them back into `src/main/resources/demo/team-logos/`, simultaneously removing the 10 stale real CTC logos that no longer have matching teams in seed data.

**Primary recommendation:** Restore 10 fictive team logos from `git show 3e640f9:src/main/resources/demo/team-logos/{SHORTNAME}.png`, remove 10 stale real CTC logos, add one integration test assertion verifying logos are set after seed.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DATA-08 | Fictive team logos exist in `demo/team-logos/` and are correctly copied to uploads on dev startup (copyDemoLogos() succeeds for all fictive teams) | Root cause identified: logos deleted by Phase 23, recoverable from git history at commit `3e640f9`. copyDemoLogos() logic is correct — only the source PNG files are missing. |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- **Test Coverage:** Minimum 82% line coverage — `TestDataService` is excluded from JaCoCo coverage (confirmed in pom.xml). New test assertions go in `TestDataServiceIntegrationTest` which already exists.
- **TDD:** Write tests first (red), then implementation (green). For this phase: add failing assertion for `logoUrl` presence, then restore logos.
- **Flyway:** No Flyway changes needed — this is purely a static resource + test fix.
- **OSIV:** Not relevant for this phase.
- **No Inline Styles:** Not relevant.
- **Profiles:** `copyDemoLogos()` only runs under `@Profile("dev")` (TestDataService has `@Profile("dev")`).
- **Git workflow:** Feature branch, PR, squash merge.
- **Commit messages:** Conventional Commits. Likely: `fix(seed): restore fictive team logo files deleted by Phase 23`.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring ClassPathResource | (Spring Boot 4.x) | Load PNG from classpath | Already used in copyDemoLogos() |
| java.nio.file.Files | JDK 25 | Copy bytes from stream to filesystem | Already used in copyDemoLogos() |
| JUnit 5 + AssertJ | (project standard) | Test logo presence after seed | Project-wide test framework |

No new dependencies required. This is purely a file-restore task with one additional test assertion.

## Architecture Patterns

### Existing copyDemoLogos() Logic

```java
// Source: src/main/java/org/ctc/admin/TestDataService.java:243
private void copyDemoLogos(List<Team> parentTeams) {
    var allTeams = teamRepository.findAll();
    Path uploadBase = Paths.get(uploadDir, "teams").toAbsolutePath().normalize();
    for (var team : allTeams) {
        String logoKey = team.isSubTeam() ? team.getParentTeam().getShortName() : team.getShortName();
        try {
            var resource = new ClassPathResource("demo/team-logos/" + logoKey + ".png");
            if (resource.exists()) {
                Path teamDir = uploadBase.resolve(team.getId().toString());
                Files.createDirectories(teamDir);
                Path target = teamDir.resolve(logoKey + ".png");
                try (var is = resource.getInputStream()) {
                    Files.copy(is, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                team.setLogoUrl("/uploads/teams/" + team.getId() + "/" + logoKey + ".png");
                teamRepository.save(team);
            }
        } catch (IOException e) {
            log.warn("Failed to copy demo logo for {}: {}", team.getShortName(), e.getMessage());
        }
    }
    log.info("Demo logos copied for {} teams", allTeams.size());
}
```

The logic is **correct** for fictive teams. The method looks up `demo/team-logos/{SHORTNAME}.png` for each team (using parent short name for sub-teams). All 10 fictive short names (VRX, SGM, ADR, TBR, ICL, SVT, NFR, EGP, HMS, PWR) match the keys it expects. The only problem is that those PNG files do not exist at runtime — they were deleted by Phase 23.

Sub-teams reuse the parent's logo key, so only 10 PNGs are needed (not 17).

### Logo File Location

```
src/main/resources/demo/team-logos/
├── VRX.png   # 9,908 bytes — available in git commit 3e640f9
├── SGM.png   # 9,795 bytes — available in git commit 3e640f9
├── ADR.png   # 9,861 bytes — available in git commit 3e640f9
├── TBR.png   # 9,028 bytes — available in git commit 3e640f9
├── ICL.png   # 8,857 bytes — available in git commit 3e640f9
├── SVT.png   # 9,479 bytes — available in git commit 3e640f9
├── NFR.png   # 9,168 bytes — available in git commit 3e640f9
├── EGP.png   # 9,470 bytes — available in git commit 3e640f9
├── HMS.png   # 9,693 bytes — available in git commit 3e640f9
└── PWR.png   # 9,648 bytes — available in git commit 3e640f9
```

Currently the directory contains only the 10 stale real CTC logos: AHR, ART, CLR, DTR, GXR, MRL, P1R, TCR, TNR, VEZ.

### Logo Generation Origin

The 10 fictive logos were generated in Phase 22 (commit `3e640f9`: "chore(22): replace team logos with fictive team versions"). The commit message states: "Each logo is a 200x200px circle with team colors and short name." They are stored as binary PNG blobs in git and fully recoverable via `git show 3e640f9:src/main/resources/demo/team-logos/{KEY}.png`.

### Test Pattern (Existing)

```java
// Source: src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class TestDataServiceIntegrationTest {
    // existing tests for DATA-01, DATA-02, DATA-03
    // Phase 26 adds DATA-08 assertion here
}
```

### Anti-Patterns to Avoid

- **Re-generating logos programmatically:** Unnecessary complexity. The exact PNG files are available in git history — restore them directly.
- **Leaving stale real CTC logos in place:** They cause visual confusion and waste classpath resources. Remove AHR, ART, CLR, DTR, GXR, MRL, P1R, TCR, TNR, VEZ.
- **Modifying copyDemoLogos() logic:** The logic is correct. Only the files are wrong.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Logo bytes | Custom PNG generator | `git show 3e640f9:src/.../VRX.png > VRX.png` | Logos already exist in git history, 100% correct |
| File copy | Custom stream logic | Existing `copyDemoLogos()` method | Already implemented and correct |

## Runtime State Inventory

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `teamRepository` — `logoUrl` column is `null` for all fictive teams after seed | Code fix only — `copyDemoLogos()` sets logoUrl when logos exist; restoring PNGs fixes this automatically |
| Live service config | None — dev-only, no external services | None |
| OS-registered state | None | None |
| Secrets/env vars | None | None |
| Build artifacts | `src/main/resources/demo/team-logos/` — contains 10 stale real CTC PNGs (AHR, ART, CLR, DTR, GXR, MRL, P1R, TCR, TNR, VEZ) | Delete stale files, restore fictive files |

## Common Pitfalls

### Pitfall 1: Silent logo miss
**What goes wrong:** copyDemoLogos() uses `if (resource.exists())` — when the PNG is missing it silently skips without error. Tests and app startup appear to succeed, but all teams have `logoUrl = null`.
**Why it happens:** The guard avoids NPE but masks the missing-file problem.
**How to avoid:** Add an integration test assertion that `logoUrl` is non-null for at least one fictive team after seed. TDD: write this test first (it will fail), then restore logos.
**Warning signs:** `log.info("Demo logos copied for {} teams", allTeams.size())` always shows the full team count even when 0 logos were actually copied (the log counts all teams, not successful copies).

### Pitfall 2: Stale real CTC logos left in classpath
**What goes wrong:** Leaving AHR.png etc. in `demo/team-logos/` does no harm (they are never looked up for fictive teams), but confuses future readers about which files are active.
**How to avoid:** Delete the 10 stale files in the same commit.

### Pitfall 3: Sub-team logo key collision
**What goes wrong:** Sub-teams (VRX A, VRX B, SGM B, SGM S, TBR R, TBR B, TBR G) resolve logoKey to their parent's short name. If someone later adds a logo named `VRX A.png` instead of `VRX.png`, sub-teams will miss it.
**How to avoid:** No action needed now — the existing logic is correct and all sub-teams share the parent logo.

## Code Examples

### Restore logos via git (plan action)
```bash
# For each fictive team short name:
git show 3e640f9:src/main/resources/demo/team-logos/VRX.png \
  > src/main/resources/demo/team-logos/VRX.png
# Repeat for SGM, ADR, TBR, ICL, SVT, NFR, EGP, HMS, PWR
# Then delete stale real CTC logos:
git rm src/main/resources/demo/team-logos/AHR.png \
       src/main/resources/demo/team-logos/ART.png \
       src/main/resources/demo/team-logos/CLR.png \
       src/main/resources/demo/team-logos/DTR.png \
       src/main/resources/demo/team-logos/GXR.png \
       src/main/resources/demo/team-logos/MRL.png \
       src/main/resources/demo/team-logos/P1R.png \
       src/main/resources/demo/team-logos/TCR.png \
       src/main/resources/demo/team-logos/TNR.png \
       src/main/resources/demo/team-logos/VEZ.png
```

### Integration test assertion for DATA-08 (TDD: add before restoring logos)
```java
// Source: test pattern from TestDataServiceIntegrationTest
@Test
void givenDevSeed_whenStarted_thenFictiveTeamsHaveLogoUrls() {
    // given
    var fictiveShortNames = Set.of("VRX", "SGM", "ADR", "TBR", "ICL", "SVT", "NFR", "EGP", "HMS", "PWR");

    // when
    var fictiveParentTeams = teamRepository.findAll().stream()
            .filter(t -> t.getParentTeam() == null)
            .filter(t -> fictiveShortNames.contains(t.getShortName()))
            .toList();

    // then
    assertThat(fictiveParentTeams).hasSize(10);
    assertThat(fictiveParentTeams)
            .as("All fictive parent teams must have a logoUrl set by copyDemoLogos()")
            .allSatisfy(t -> assertThat(t.getLogoUrl())
                    .as("Team %s should have a logoUrl", t.getShortName())
                    .isNotNull()
                    .startsWith("/uploads/teams/"));
}
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ (Spring Boot 4.x) |
| Config file | pom.xml (Surefire) |
| Quick run command | `./mvnw test -Dtest=TestDataServiceIntegrationTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DATA-08 | All fictive parent teams have non-null logoUrl after seed | Integration | `./mvnw test -Dtest=TestDataServiceIntegrationTest` | ✅ (add assertion to existing file) |

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest=TestDataServiceIntegrationTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
None — existing test infrastructure covers all phase requirements. Only a new test method is needed within the existing `TestDataServiceIntegrationTest.java`.

## Security Domain

This phase modifies only static dev-profile seed resources (PNG files in classpath) and a dev-profile integration test. No production code, no auth, no HTTP endpoints, no user input. ASVS categories V2-V6 are not applicable.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| git | Logo restore via `git show` | Yes | (project git repo) | None needed |
| Java / Maven | Test execution | Yes | Java 25, Maven wrapper | None needed |

No missing dependencies.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The fictive logos in commit `3e640f9` are byte-for-byte the correct logos to restore (same colors/design as intended by Phase 22) | Standard Stack | LOW — commit message confirms "200x200px circle with team colors and short name"; verified sizes are plausible PNGs |

## Open Questions

None. The problem, root cause, fix, and test strategy are fully understood.

## Sources

### Primary (HIGH confidence)
- `[VERIFIED: git log --all -- src/main/resources/demo/team-logos/]` — commit history confirms Phase 22 added fictive logos, Phase 23 deleted them
- `[VERIFIED: git show 3e640f9 --stat]` — confirmed all 10 fictive PNGs present in that commit
- `[VERIFIED: ls src/main/resources/demo/team-logos/]` — confirmed current directory contains only real CTC logos
- `[VERIFIED: grep copyDemoLogos src/main/java/org/ctc/admin/TestDataService.java]` — logic is correct, no code changes needed
- `[VERIFIED: grep jacoco pom.xml]` — TestDataService excluded from coverage; no coverage threshold impact
- `[VERIFIED: .planning/v1.3-v1.3-MILESTONE-AUDIT.md]` — DATA-08 requirement and evidence of failure documented

## Metadata

**Confidence breakdown:**
- Problem diagnosis: HIGH — confirmed via git history and file system inspection
- Fix strategy: HIGH — logos recoverable from git commit `3e640f9`; no code changes to copyDemoLogos()
- Test strategy: HIGH — existing TestDataServiceIntegrationTest pattern is clear; one new method needed

**Research date:** 2026-04-10
**Valid until:** 2026-05-10 (stable domain)
