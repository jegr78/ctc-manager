---
phase: 23-dev-seasons-with-results
executed: 2026-04-09T23:35:00Z
server_profile: dev
total: 11
passed: 10
failed: 0
skipped: 1
---

# Auto-UAT Report: Phase 23

## Results

### 1. Cold Start Smoke Test
- **Status:** passed
- **Screenshots:** [seasons-list](.screenshots/auto-uat/test-01-seasons-list.yaml)
- **Evidence:** Server healthy after 15s, 8 seasons visible on /admin/seasons

### 2. Season Formats on Season List
- **Status:** skipped
- **Evidence:** Format field not displayed in UI templates (data model only). Not part of phase scope.

### 3. S1 Group A Team Count
- **Status:** passed
- **Screenshots:** [group-a-detail](.screenshots/auto-uat/test-02-s1-group-a-detail.yaml)
- **Evidence:** Teams (6) — P1R, TCR, ART, MRL, GXR, CLR 1 (Sub)

### 4. S1 Group B Team Count
- **Status:** passed
- **Screenshots:** [group-b-detail](.screenshots/auto-uat/test-04-s1-group-b.yaml)
- **Evidence:** Teams (6) — DTR, VEZ, CLR 2, TNR A, TNR B, AHR 1 (Sub)

### 5. S4 League Match Teams
- **Status:** passed
- **Screenshots:** [s4-league](.screenshots/auto-uat/test-05-s4-league.yaml)
- **Evidence:** Teams (14) — 7 sub-teams + 7 standalone parents, no CLR/TNR/AHR parents

### 6. League Matchdays and Scores
- **Status:** passed
- **Screenshots:** [league-matchday1](.screenshots/auto-uat/test-06-league-matchday1.yaml)
- **Evidence:** 5 matchdays, 7 matches each, scores 65:51, 12 Results per race

### 7. Swiss Matchdays with Two Races
- **Status:** passed
- **Screenshots:** [s2-swiss](.screenshots/auto-uat/test-07-s2-swiss.yaml)
- **Evidence:** 5 matchdays, 10 races each (5 matches x 2 races)

### 8. Round Robin Matchdays
- **Status:** passed
- **Evidence:** Group A: 3 matchdays, 6 races each. Group B: 3 matchdays, 6 races each.

### 9. League Standings Non-Zero Points
- **Status:** passed
- **Screenshots:** [standings](.screenshots/auto-uat/test-09-standings.yaml)
- **Evidence:** 14 teams ranked. Top 7: 9 pts. Bottom 7: 6 pts. 84 drivers with differentiated points.

### 10. Swiss Standings Non-Zero Points
- **Status:** passed
- **Screenshots:** [swiss-standings](.screenshots/auto-uat/test-10-swiss-standings.yaml)
- **Evidence:** 10 teams. Swiss columns (Score/Buchholz/Pts Diff). Scores 9.0/6.0.

### 11. Round Robin Standings Non-Zero Points
- **Status:** passed
- **Screenshots:** [rr-standings](.screenshots/auto-uat/test-11-rr-standings.yaml), [rr-screenshot](.screenshots/auto-uat/test-11-rr-standings-final.png)
- **Evidence:** 6 teams. Top 3: 6 pts, 2W-1L. Bottom 3: 3 pts, 1W-2L.
