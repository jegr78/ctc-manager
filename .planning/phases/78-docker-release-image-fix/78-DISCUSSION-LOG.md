# Phase 78: Docker Release Image Fix - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-11
**Phase:** 78-docker-release-image-fix
**Areas discussed:** Pin-Tightness, Verifikationstiefe lokal, Regression-Guard im CI, PR-Time Docker-Build

---

## Pin-Tightness

| Option | Description | Selected |
|--------|-------------|----------|
| Nur `-noble` Suffix (Recommended) | `FROM eclipse-temurin:25-jdk-noble` + `25-jre-noble`. Löst das Playwright-Problem dauerhaft, Temurin-Patches fließen weiter ein. Matches success criterion 1 exactly. Minimal-Diff. | ✓ |
| `-noble` + SHA256-Digest | `FROM eclipse-temurin:25-jdk-noble@sha256:<digest>`. Voll reproduzierbar, aber manueller Patch-Tracking-Aufwand. | |
| Spezifische Patch-Version | `FROM eclipse-temurin:25.0.1_9-jdk-noble`. Bindet an exakte JDK-Patch-Version, überspringt Security-Updates. | |

**User's choice:** Nur `-noble` Suffix (Recommended)
**Notes:** Reines OS-Suffix-Pinning. Security-Patches sollen automatisch fließen, Digest-Overhead nicht gerechtfertigt für Single-App-Repo. Roadmap-Kriterium 1 wird damit 1:1 erfüllt.

---

## Verifikationstiefe lokal

| Option | Description | Selected |
|--------|-------------|----------|
| Build + Container-Health (Recommended) | `docker build .` + `docker compose up app` + `curl /actuator/health == 200`. ~3-5 min, trifft "E2E bei Verifikation"-Präferenz ohne Full-E2E-Overkill. | ✓ |
| Nur `docker build .` | Build-time reicht; Container-Runtime wird vom Release-Workflow + Deployment getestet. Schnellste Variante. | |
| Build + Container + Team-Card-Smoke | Zusätzlich `/admin/teams/{id}/card` triggern, um Chromium-Rendering im Container zu verifizieren. ~8-10 min, braucht Test-Daten. | |

**User's choice:** Build + Container-Health (Recommended)
**Notes:** Build-Step beweist Playwright-Install, Health-Step beweist Runtime + JAR-Boot. Team-Card-Smoke nicht nötig — wird vom späteren Deployment + Manual-UAT abgedeckt.

---

## Regression-Guard im CI

| Option | Description | Selected |
|--------|-------------|----------|
| Build-Guard im CI (Recommended) | Shell-Step in `ci.yml` grept `Dockerfile` nach `^FROM eclipse-temurin:` und failt bei fehlendem `-noble` Suffix. Mirror der Phase-71-05 build-guard pattern. ~5 Zeilen YAML. | |
| Inline-Kommentar im Dockerfile | Kommentar über FROM-Zeilen erklärt das Warum, verhindert die Änderung aber nicht aktiv. | |
| **Beides — Guard + Kommentar** | Build-Guard im CI failt bei Drift + Kommentar im Dockerfile erklärt das Warum. Belt + suspenders. | ✓ |
| Nur Code-Review-Disziplin | Keine Automatisierung. Risiko: Future-Contributor unpinned irgendwann wieder. | |

**User's choice:** Beides — Guard + Kommentar
**Notes:** Strukturell verhindern UND Kontext für Reviewer/Maintainer geben. Konsistent mit memory "Phase-Overwrite-Prevention" und "Plan-Quality-Gates" (strukturelle Guards bevorzugt).

---

## PR-Time Docker-Build

| Option | Description | Selected |
|--------|-------------|----------|
| **Ja, PR-Build hinzufügen (Recommended)** | Neuer Job in `ci.yml`: `docker build .` ohne push. +1-3 min CI/PR, Fail-Fast bei Base-Image-Drift, apt-Paket-Drift, Multi-Stage-Bugs. | ✓ |
| Ja, aber nur wenn Dockerfile geändert wurde | Path-Filter (`on.pull_request.paths: ['Dockerfile']`). Pragmatischer Mittelweg. | |
| Nein, Build-Guard reicht | Der Guard aus Gray Area 3 fängt das Hauptrisiko ab. Schnellste CI. | |
| Build + Container-Smoke im PR-CI | Wie "Ja", zusätzlich `docker run` + `/actuator/health` als CI-Step. ~+5 min CI. | |

**User's choice:** Ja, PR-Build hinzufügen (Recommended)
**Notes:** Genau diese Drift-Klasse wäre auf PR aufgefallen, wenn der Job existiert hätte. Cost (+1-3 min) explizit akzeptiert. Path-Filter und Container-Smoke bewusst NICHT genommen — Simplicity gewinnt; eskaliert werden kann später.

---

## Claude's Discretion

- Exact YAML structure of the new `ci.yml` job (job name, runner, action versions, Maven dependency cache reuse) — planner picks idiomatic patterns matching the existing `ci.yml` style.
- Exact wording of the build-guard failure message (must reference Phase 78 + the `-noble` pin rationale).
- Whether the build-guard runs as a step inside the new `docker build` job or as a separate, faster `dockerfile-lint`-style job — planner judgement.
- Whether to use `docker/setup-buildx-action` + caching to keep PR build time low — planner judgement; not load-bearing on success criteria.

## Deferred Ideas

- SHA256 digest pinning + Renovate/Dependabot — rejected here, revisit if Noble retag breaks suffix pin.
- Team-Card-Generation smoke as CI gate — natural fit for a future "container E2E" phase if needed.
- Path-filter for the new docker-build job — revisit only if CI runtime becomes a real complaint.
- Docker-compose `eclipse-temurin:` audit — only relevant if a future phase introduces sidecar containers pulling Temurin directly; build-guard regex would need extending then.
