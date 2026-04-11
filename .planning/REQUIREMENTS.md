# Requirements: CTC Manager

**Defined:** 2026-04-07
**Core Value:** Architektur-Konsistenz: Alle Controller delegieren an Services, Exception Handling ist zentral, und die Prod-Umgebung ist abgesichert.

## v1.2 Requirements

Requirements for Driver Merge milestone. Each maps to roadmap phases.

### Merge UI

- [ ] **MERGE-01**: Admin kann auf der Fahrer-Detailseite einen Merge starten
- [ ] **MERGE-02**: Admin kann den Ziel-Fahrer auswaehlen, in den gemergt wird
- [ ] **MERGE-03**: Admin sieht eine Vorschau der betroffenen Referenzen vor dem Merge
- [ ] **MERGE-04**: Admin muss den Merge explizit bestaetigen

### FK Reassignment

- [ ] **MERGE-05**: Alle SeasonDriver-Eintraege werden vom Quell- auf den Ziel-Fahrer umgehaengt
- [ ] **MERGE-06**: Alle RaceLineup-Eintraege werden vom Quell- auf den Ziel-Fahrer umgehaengt
- [ ] **MERGE-07**: Alle RaceResult-Eintraege werden vom Quell- auf den Ziel-Fahrer umgehaengt
- [ ] **MERGE-08**: Alle PsnAlias-Eintraege werden auf den Ziel-Fahrer uebertragen
- [ ] **MERGE-09**: PSN-ID des Quell-Fahrers wird als neuer Alias am Ziel-Fahrer angelegt
- [ ] **MERGE-10**: Quell-Fahrer wird nach erfolgreichem Merge geloescht

### Duplikat-Handling

- [ ] **MERGE-11**: Unique-Constraint-Konflikte bei SeasonDriver (gleiche Season) werden erkannt und geloest
- [ ] **MERGE-12**: Unique-Constraint-Konflikte bei RaceLineup (gleiches Race) werden erkannt und geloest
- [ ] **MERGE-13**: Unique-Constraint-Konflikte bei RaceResult (gleiches Race) werden erkannt und geloest

### Audit

- [ ] **MERGE-14**: Merge-Aktion wird geloggt (Quell-Fahrer, Ziel-Fahrer, Zeitpunkt, betroffene Referenzen)

## Future Requirements

(None deferred for this milestone)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Automatischer Duplikat-Erkennung | Manueller Merge reicht fuer Datenuebernahme |
| Bulk-Merge (mehrere Fahrer gleichzeitig) | Einzelner Merge pro Vorgang genuegt |
| Undo/Rollback von Merges | Komplexitaet zu hoch, Backup vor Import reicht |
| Merge von Teams | Nur Fahrer-Merge im Scope |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| MERGE-01 | — | Pending |
| MERGE-02 | — | Pending |
| MERGE-03 | — | Pending |
| MERGE-04 | — | Pending |
| MERGE-05 | — | Pending |
| MERGE-06 | — | Pending |
| MERGE-07 | — | Pending |
| MERGE-08 | — | Pending |
| MERGE-09 | — | Pending |
| MERGE-10 | — | Pending |
| MERGE-11 | — | Pending |
| MERGE-12 | — | Pending |
| MERGE-13 | — | Pending |
| MERGE-14 | — | Pending |

**Coverage:**
- v1.2 requirements: 14 total
- Mapped to phases: 0
- Unmapped: 14

---
*Requirements defined: 2026-04-07*
*Last updated: 2026-04-07 after initial definition*
