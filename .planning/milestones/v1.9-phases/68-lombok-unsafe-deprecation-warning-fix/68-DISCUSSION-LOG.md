# Phase 68: Lombok Unsafe Deprecation Warning Fix - Discussion Log

> **Audit trail only.** Decisions live in CONTEXT.md.

**Date:** 2026-05-07
**Phase:** 68-lombok-unsafe-deprecation-warning-fix
**Mode:** `--auto --chain` (no AskUserQuestion calls)
**Areas:** Strategy, Version selection, Verification surface, Test impact, Plan structure, Rollback plan

---

## Strategy

| Option | Description | Selected |
|---|---|---|
| Lombok version pin via `<lombok.version>` property override | Use the project's existing extension hook | ✓ |
| JVM workarounds (`--add-opens java.base/sun.misc=ALL-UNNAMED`) | Suppresses symptom; keeps upstream debt | |
| Replace Lombok with hand-written getters / Java records | Massive refactor across every `@Getter`/`@Slf4j` site | |
| Spring Boot framework upgrade (4.0.6+) | Broad compatibility risk for one warning class | |

**Auto-selected:** Property override (D-01..D-04).
**Rationale:** pom.xml already references `${lombok.version}` for the annotation-processor path declaration; property override is the canonical, minimal hook. CLAUDE.md "Don't add features, refactor, or introduce abstractions beyond what the task requires."

---

## Version selection

| Option | Description | Selected |
|---|---|---|
| Latest stable Lombok release with Permit→MethodHandles refactor | Default; lowest risk | ✓ (with research-confirmed exact version) |
| Edge / snapshot release | Required only if no stable carries the fix | (fallback) |
| Stay on 1.18.44 with JVM `--add-opens` flags | Symptom suppression — see Strategy | |

**Auto-selected:** Latest stable; researcher confirms exact version + Spring Boot 4.0.5 + Java 25 compatibility.
**Rationale:** D-05 — research deferred. Version locked once researcher returns concrete version.

---

## Verification surface

| Option | Description | Selected |
|---|---|---|
| Three-context check: `./mvnw verify` + `spring-boot:run` startup + isolated test invocation | Full coverage of all warning emission sites | ✓ |
| `./mvnw verify` only | Misses `spring-boot:run` runtime check | |
| `./mvnw verify` + smoke test app launch | No isolated test smoke | |

**Auto-selected:** Three-context (D-07).
**Rationale:** Lombok 1.18.44's Permit::Unsafe call could fire from any context that loads the Lombok JARs. Three contexts cover compile, runtime, and isolated test surfaces.

---

## Test impact

| Option | Description | Selected |
|---|---|---|
| Behavior gate: `./mvnw verify` exits 0; tests-run = 1231; JaCoCo ≥ 0.82 | Strict — no regression tolerance | ✓ |
| Behavior gate: tests pass — coverage delta ignored | Loose — risks JaCoCo drift | |

**Auto-selected:** Strict behavior gate (D-09, D-10).
**Rationale:** Property pin should produce zero behavior delta; if any drift surfaces, that's a Lombok regression and the fix gets reverted (D-13).

---

## Plan structure

| Option | Description | Selected |
|---|---|---|
| Single plan, 3 tasks (pin / verify / spring-boot:run gate) | Atomic; minimal | ✓ |
| Single plan, 1 task (just pin + verify) | Misses runtime gate | |
| Three plans (one per verification context) | Plan-overhead overkill for 1-line pom change | |

**Auto-selected:** Single plan, 3 tasks (D-11, D-12).
**Rationale:** Hot-fix scope; one atomic commit covers the property override and gates.

---

## Rollback plan

| Option | Description | Selected |
|---|---|---|
| If verify breaks: revert; surface to user as blocker | Strict; don't ship half-fix | ✓ |
| If 1 of 3 contexts still emits warnings: accept partial | Pragmatic but loose | (override D-14 documents acceptance) |
| If verify breaks: try next-older candidate version | Could cascade through versions | |

**Auto-selected:** Strict revert + escalate (D-13, D-14).
**Rationale:** Phase goal is "warnings gone everywhere." Half-fix shipping erodes the gate value of the next quality phase.

---

## Claude's Discretion

- **D-16:** stable vs. edge release — left to RESEARCH.md.
- **D-17:** property vs. dependencyManagement override style — left to planner (canonical Spring Boot pattern is the property route).

## Deferred Ideas

- Lombok elimination → separate quality phase if team wants to drop annotation processor.
- JVM `--add-opens` band-aid → explicitly rejected here; could be revisited if upstream fix is unavailable.
- Spring Boot 4.0.6+ upgrade → Dependabot tracks separately.
- Other JDK 25 deprecation warnings → file as separate phases per warning class.
