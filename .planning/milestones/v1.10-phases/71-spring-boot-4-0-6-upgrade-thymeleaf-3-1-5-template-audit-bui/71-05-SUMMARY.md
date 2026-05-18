---
phase: 71-spring-boot-4-0-6-upgrade-thymeleaf-3-1-5-template-audit-bui
plan: 05
status: complete
started: 2026-05-11
completed: 2026-05-11
requirements: [PLAT-04, PLAT-07]
---

# Plan 71-05 Summary — Maven Build-Guard for Thymeleaf Fragment-Call Expressions

## What Shipped

PLAT-04 source-level safety net for the Wave-1 refactor: a lightweight `exec-maven-plugin` execution bound to the `validate` phase that fails the build the moment a `${…}` expression slips back into a Thymeleaf fragment-call argument (`th:replace`/`insert`/`include` with `~{…(…)}` syntax).

## How It Works

Inserted into the default `<build><plugins>` section of `pom.xml`:

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>exec-maven-plugin</artifactId>
  <executions>
    <execution>
      <id>template-fragment-call-guard</id>
      <phase>validate</phase>
      <goals><goal>exec</goal></goals>
      <configuration>
        <executable>bash</executable>
        <arguments>
          <argument>-c</argument>
          <argument><![CDATA[
violations=$(grep -rE 'th:(replace|insert|include)="~\{[^"]*\(.*\$\{.*\}.*\)\}"' src/main/resources/templates/ | grep -v 'layout(${pageTitle}' || true);
if [ -n "$violations" ]; then
  echo "[PLAT-07 build-guard] Forbidden Thymeleaf fragment-call expression detected (Plan 05):";
  echo "$violations";
  echo "Move the value to the controller via model.addAttribute(\"pageTitle\", ...) and use ~{layout :: layout(\${pageTitle}, ~{::section})}.";
  echo "See .planning/phases/71-*/71-CONTEXT.md Decisions D-05 + D-12 for the canonical fix.";
  exit 1;
fi;
echo "[PLAT-07 build-guard] OK - no Thymeleaf fragment-call expression offenders.";
exit 0;
]]></argument>
        </arguments>
      </configuration>
    </execution>
  </executions>
</plugin>
```

The regex `th:(replace|insert|include)="~\{[^"]*\(.*\$\{.*\}.*\)\}"` matches a fragment-call (`~{frag :: name(…)`) whose argument list contains a `${…}` expression. The whitelist `grep -v 'layout(${pageTitle}'` allows the *new* canonical pattern that Wave 1 wired in (where `pageTitle` is the controller-supplied model attribute). Any other shape fails the build with a pointer to the fix recipe in `71-CONTEXT.md` (D-05 + D-12).

## Verification Results

| Check | Result |
|-------|--------|
| `./mvnw validate` on clean tree | **PASS** — `[PLAT-07 build-guard] OK` |
| `./mvnw validate` with injected offender | **FAIL** — exit code 1, clear error message printing the offending line |
| `./mvnw validate` post-cleanup | **PASS** — guard returns to OK |
| `./mvnw verify` (full lifecycle with guard) | **BUILD SUCCESS** in 8:08 min (1227 + 112 tests, JaCoCo 89.44 %) |

### Manual negative test

```bash
$ cat > src/main/resources/templates/_buildguard-test.html <<'EOF'
<div th:replace="~{layout :: layout(${foo}, ~{::section})}"></div>
EOF

$ ./mvnw -q validate
[PLAT-07 build-guard] Forbidden Thymeleaf fragment-call expression detected (Plan 05):
src/main/resources/templates/_buildguard-test.html:1:<div th:replace="~{layout :: layout(${foo}, ~{::section})}"></div>
Move the value to the controller via model.addAttribute("pageTitle", ...) and use ~{layout :: layout(${pageTitle}, ~{::section})}.
See .planning/phases/71-*/71-CONTEXT.md Decisions D-05 + D-12 for the canonical fix.

[ERROR] Failed to execute goal … (Exit value: 1)

$ rm src/main/resources/templates/_buildguard-test.html
$ ./mvnw -q validate
[PLAT-07 build-guard] OK - no Thymeleaf fragment-call expression offenders.
```

## Files Modified

| File | Change |
|------|--------|
| `pom.xml` | New `<plugin>org.codehaus.mojo:exec-maven-plugin</plugin>` execution `template-fragment-call-guard` bound to `validate` |

## Plan Deviation

None functionally. One IDE warning surfaced (`org.codehaus.mojo:exec-maven-plugin` missing `<version>`) — exec-maven-plugin is not in the Spring Boot BOM, so Maven uses the latest available (`3.6.3` at resolution). The current verify resolves and runs cleanly; pinning the version is a follow-up cleanup, not a Plan-71-05 deliverable.

## Recovery Note

The original agent (`aa3609940461c50d4`) stalled mid-SUMMARY-write. Its actual code commit (`d796011 feat(71-05): add exec-maven-plugin build-guard for Thymeleaf fragment-call expressions`) was complete and correct — the orchestrator merged the worktree branch and validated the guard directly without re-spawning the agent.

## Carry-Forward

Combined with Plan 71-04's `TemplateRenderingSmokeIT`, the codebase now has *two* layers of protection against Wave-1 regression:

1. **Source-level (this plan, fail fast):** `./mvnw validate` rejects the bad pattern before compile starts — instant feedback, no test suite needed.
2. **Runtime (Plan 71-04, broad coverage):** `TemplateRenderingSmokeIT` exercises every `/admin/**` GET handler against a smoke fixture and asserts no `TemplateProcessingException` slips through.

## Self-Check: PASSED

- ✅ `pom.xml` carries the `exec-maven-plugin` `template-fragment-call-guard` execution bound to the `validate` phase.
- ✅ Guard pattern correctly matches forbidden `${…}` in fragment-call args, with allowlist for `layout(${pageTitle}` (the new canonical shape).
- ✅ Clean tree → guard PASSES with informative log line.
- ✅ Injected offender → guard FAILS with actionable error message + fix recipe pointer.
- ✅ Cleanup → guard returns to OK.
- ✅ Full `./mvnw verify` BUILD SUCCESS with guard active (no false positives on legitimate templates).
