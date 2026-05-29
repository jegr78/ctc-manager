# Handoff: CTC 2026 — Grafik-Redesign (Carbon/Gold)

## Overview
Einheitliches visuelles Redesign aller Render-Grafiken des CTC-Managers (Team-Card, Matchup-Komposite,
Matchday-/Listen-Grafiken, Stream-Overlay). Ziel: hochwertigeres, kohärentes „Carbon/Gold"-Look-&-Feel —
**ohne Änderung an Layout, Format oder Datenmodell**.

## Wichtig: Das sind fertige Drop-in-Templates — kein Re-Build
Anders als beim typischen Design-Handoff sind die Dateien in `templates/` **keine HTML-Prototypen, die in einem
anderen Framework nachgebaut werden müssen.** Es sind **produktionsfertige Thymeleaf-Templates**, die die
bestehenden Dateien **1:1 ersetzen**. Sie nutzen exakt dieselben `th:*`-Bindings, Model-Variablen und
Render-Pipeline (Playwright/Chromium-Screenshot) wie die Originale.

> **Aufgabe für Claude Code:** Jede Datei aus `templates/` an den gleichnamigen Pfad unter
> `src/main/resources/templates/admin/` kopieren (bestehende Datei ersetzen). Danach die zwei optionalen
> Backend-Anpassungen (unten) prüfen. Keine weiteren Code-Änderungen nötig.

## Fidelity
**High-fidelity.** Finale Farben, Typografie, Abstände. Maße/Positionen entsprechen exakt den Originalen
(insbesondere das Overlay — dort dürfen Größe/Position NICHT verändert werden, da Elemente bewusst Videoteile
überdecken; Hintergrund bleibt transparent).

## Datei-Mapping (templates/ → Zielpfad)
Alle nach `src/main/resources/templates/admin/`:

| Datei | Grafik |
|---|---|
| `team-card-render.html` | Team-Karte (1080×1920) |
| `settings-render.html` | Settings (Matchup-Komposit) |
| `lineup-render.html` | Lineups |
| `results-render.html` | Scorecard |
| `match-results-render.html` | Match Results (Score) |
| `provisional-scores-render.html` | Provisional Scores |
| `matchday-schedule-render.html` | Matchday Schedule |
| `matchday-overview-render.html` | Matchday Overview (Pairings/Seeds) |
| `standings-render.html` | Standings (dynamische Zeilenhöhe) |
| `matchday-results-render.html` | Matchday Results (Score) |
| `power-rankings-render.html` | Power Rankings (2 Spalten) |
| `overlay-render.html` | Stream-Overlay (transparent) |

## Verwendete Model-Variablen (unverändert)
Es wurden **keine** neuen Pflicht-Variablen eingeführt. Die Templates nutzen die bereits gesetzten:
- Team-Karte: `teamName, subTeamLabel, rating, points, record, primaryColor, secondaryColor, accentColor, gradientColor, logoBase64, fontBase64`
- Komposite: `seasonYear, matchdayName, seasonName, homeCardBase64, awayCardBase64, ctcLogoBase64, fontBase64` + grafikspezifische Felder (`carName`, `raceRows`, `resultRows`, `pairings`, `homeRows/awayRows`, …)
- Matchday/Listen: `data.*` (matches mit `homePrimaryColor`, `homeLogoBase64`, `homeTeamName`, `homeRecord`, `homeSeed`, `homeScore`, …), Standings: `standings` + `rowHeightPx/fontSizePx/logoSizePx/posFontSizePx`, Power Rankings: `data.leftColumn/rightColumn`
- Overlay: `homeTeamNameHtml, homePrimaryColor, homeLogoBase64, homeRecord, …, vsBadgeBase64, commentatorBase64, ctcLogoBase64, fontBase64`

## Zwei optionale Backend-Anpassungen (kein Blocker)

### 1. `TeamCardService` — perfekte Farb-Robustheit (empfohlen, optional)
CSS kann Luminanz nicht verzweigen. Ohne Patch greift ein sauberer Fallback (roher Akzent / weiße Rating-Schrift;
dunkle Primärfarbe wird per CSS-`oklch` für Schienen/Punkte angehoben). Mit zwei Helfern wird es exakt — Stil
analog zum vorhandenen `computeGradientColor`:
```java
// in generateCard(...), nach gradientColor:
ctx.setVariable("accentVisColor", computeAccentVisColor(accentColor, primaryColor));
ctx.setVariable("onPrimaryColor", contrastColor(primaryColor));

/** Sehr dunkler Akzent -> auf primary ausweichen (sichtbar auf dunklem Grund). */
String computeAccentVisColor(String accent, String primary) {
    if (accent == null) return primary;
    return relativeLuminance(accent) < 28 ? primary : accent;   // ~#1a1a1a
}
/** Lesbare Textfarbe auf einer Fläche: dunkel auf hell, weiß auf dunkel. */
String contrastColor(String hex) {
    return relativeLuminance(hex) > 140 ? "#0b0b10" : "#ffffff";
}
```
> `relativeLuminance` (existiert bereits) liefert 0–255, daher Schwellen 28 / 140.
> Das Template liest `accentVisColor`/`onPrimaryColor` per Thymeleaf-Elvis und fällt sonst sauber zurück.

### 2. Provisional Scores — Race-Chip nur bei Mehr-Rennen-Matches
Das Template zeigt unten rechts einen `raceLabel`-Chip **nur wenn `raceLabel != null`**. Empfehlung:
`ProvisionalScoresGraphicService` soll `raceLabel` **nur bei Matches mit > 1 Rennen** setzen (sonst `null`).
Aktuell wird es immer als `"Race " + raceIndex` gesetzt.

## Design-Tokens
- **Marken-Akzent (Gold):** `#f5c542` (Keylines, Titel-Klammern, VS-Doppelpunkt, Sieger-Unterstreichung, Tabellen-Akzente, Rang-/Seed-Zahlen). Deckkraft-Varianten ~.5–.85 bzw. via `color-mix`.
- **Hintergrund (Carbon-Vignette):** `radial-gradient(120% 80% at 50% 30%, #15151b 0%, #0d0d11 56%, #08080b 100%)` + feine Carbon-Streifen `repeating-linear-gradient(118deg, transparent 0 74px, rgba(255,255,255,.013) 74px 76px)`.
- **Leisten (Kopf/Fuß):** `linear-gradient(180deg, #202028, #121217)` + Gold-Keyline `inset 0 ±3px rgba(245,197,66,.75–.85)`.
- **Zeilen/Panels:** Zeile `linear-gradient(180deg,#191920,#121217)`, Radius 13px; Panel `linear-gradient(180deg,#17171d,#111116)`, Radius 14px; Border `1px rgba(255,255,255,.06)`.
- **Text:** Primär `#fff`; sekundär/muted `#9a9aa6` bzw. `#8a8a96`.
- **Schrift:** `Conthrax` (bereits als `fontBase64` im Service eingebettet).
- **Logo-Chip:** Coin `#14141a`, Ring = Teamfarbe (sehr dunkle via `oklch(from var(--c) max(l,.5) c h)` angehoben), Logo `object-fit:contain` + Doppelkontur `drop-shadow(0 0 2px #000) drop-shadow(0 0 3px #fff)` → hell wie dunkel lesbar.
- **Team-Karte primary-vis:** `oklch(from var(--primary) max(l,.62) c h)` für Schienen/Punkte (hebt #000 an).
- **Radien:** Zeilen 13px · Panels 14px · Karten-Rahmen 16px · Chips 50%.

## Render-/Browser-Hinweise
- Gerendert via Playwright-Chromium → moderne CSS-Features genutzt: `color-mix(in srgb, …)` und relative Farben
  `oklch(from …)`. Beides wird vom gebündelten Chromium unterstützt. Falls die Chromium-Version sehr alt ist,
  bitte aktualisieren (sonst greifen Fallback-Farben).
- **CTC-Logo:** `ctcLogoBase64` ist die weiße Münze (`ctc-logo-white.png`) → auf dunklen Leisten **ohne** Filter
  verwendet. Team-Card bettet das Logo selbst als Base64 ein (Service setzt dort kein `ctcLogoBase64`).
- **Overlay:** Hintergrund transparent, Geometrie/Skew exakt erhalten; nutzt echtes `vsBadgeBase64` und `commentatorBase64`.

## Test
- Komposite/Karte live im Admin-Template-Editor (`/admin/tools/template-editors`) einfügbar → Vorschau.
- Echte Karten: `/admin/tools/team-cards` → *Generate*.
- Sonst regulär über die jeweiligen GraphicServices rendern lassen und PNGs prüfen.

## Screenshots (Soll-Ergebnis)
Ordner `screenshots/` enthält gerenderte Beispiele des Ziel-Looks: Team-Karte, Match-Results-Komposit,
Provisional Scores, Matchday Pairings, Standings, Power Rankings. (Stream-Overlay am besten über die
Live-Vorschau ansehen.)

## Referenz-Vorschauen (nur zur Ansicht, nicht deployen)
Im Projektordner (nicht in diesem Bundle): `index.html` (Karten-Galerie), `redesign.html` (Komposite A/B),
`graphics-proposals.html` (Matchday/Listen), `overlay-proposals.html` (Overlay über simuliertem Video).
