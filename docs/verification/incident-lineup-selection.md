# Incident driver selection

Issue #403 replaces the Discord report's driver text input with choices from the exact race
lineup. It uses the existing report payload and archive format. Accepted revisions retain readable
name/team snapshots; changing a lineup does not rewrite earlier revisions.

## Verification

Focused local feedback passed 72 unit tests and 33 integration tests, with no skipped cases:

```sh
./scripts/test-feedback.py integration IncidentInteractionIT,IncidentWorkflowAcceptanceIT \
  --unit IncidentLineupServiceTest,IncidentInteractionHandlerTest,IncidentInteractionViewsTest,IncidentJdaModalContractTest,IncidentReportServiceTest \
  --test-heap-mib 1024 --maven-heap-mib 512
```

The checks cover exact race and participant membership, guests, parent/subteam assignments,
deduplication, ambiguous assignments, forged UUIDs, repeated selections, missing lineups,
canonical names, immutable history, edit defaults and preserved long description/footage values.
Changed lineups require another review for both submit and edit, retaining the other draft fields.
A committed request replays its exact receipt even after its lineup is removed.

Actual JDA modal serialization verifies multi-select defaults and component limits. Up to 25
drivers use one required menu. Larger lineups use two optional team menus, with at least one
selection required by the server across both; each team is bounded to 25 choices. More than 25
on one team is an explicit management setup error. Confirmation locks the owning season before
the reporting window/report and reads current locked lineup/driver/team values.

The existing Gateway-to-disclosure/ruling-to-backup/restore acceptance cases remain enabled for
team and organization reporters. Before-cutoff probes check the full selected incident-driver
snapshot and distinctive private report/evidence/actor text across generated site artifacts,
previews, logs, errors and public payloads. Individual roster names already have public profile
pages and are not themselves private incident evidence.

Complete exact-head CI, coverage, static/security analysis, MariaDB and Docker/native checks
remain required before merge. These focused checks do not activate Discord or send real reports.

## Mac test lab

The isolated four-team lab has three round-robin matchdays, six one-leg races and eight native
lineup assignments per race, including both teams. Owner-created reports, existing Discord
threads, configuration and credentials are retained. Test the new modal only after its official
release is installed; existing reports with older free text require explicit driver reselection.
The Box is outside this rollout.
