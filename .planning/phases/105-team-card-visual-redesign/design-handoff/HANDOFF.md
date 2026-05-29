# Team Card Redesign — V4 „Carbon HUD" · Handoff

## Was sich ändert
Ersatz für `src/main/resources/templates/admin/team-card-render.html`.
Gleiche Maße (1080×1920), gleiche Schrift (Conthrax), gleiche Thymeleaf-Variablen —
**keine** Pflicht-Änderung an `TeamCardService` nötig. Das neue Template nutzt zusätzlich
`accentColor` (war schon vorhanden) und rendert datengetrieben aus primary/secondary/accent.

### Verwendete Variablen (alle bereits vorhanden)
`teamName`, `subTeamLabel`, `rating`, `points`, `record`,
`primaryColor`, `secondaryColor`, `accentColor`, `gradientColor`, `logoBase64`, `fontBase64`.

- **CTC-Signatur**: das echte CTC-Logo ist unten als dezente (weiß invertierte) Marke eingebettet —
  als Base64 direkt im Template (selbstständig, im Editor lauffähig). Liefert der Service eine
  Variable `ctcLogoBase64` (Helfer `encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png")` existiert
  bereits), wird automatisch diese genutzt; sonst greift das eingebettete Logo.

### Designprinzipien
- **Logo prominent**: zentrale Querformat-Zone (860×480) fängt breite *und* quadratische Logos ab
  (`object-fit: contain`). Doppelte Kontur (dunkler + heller Halo) hält **dunkle wie helle Logos**
  auf dem dunklen Grund lesbar.
- **3 Teamfarben tragen die Karte**: primary = Seitenschienen + Rating-Fläche + Punkte-Wert;
  secondary = Hintergrund-Glow (über `gradientColor`); accent = Pinstripes, HUD-Rahmen,
  Klammern, Stat-Labels, Sub-Team-Badge.
- **Rating oben** als Parallelogramm, gut sichtbar (Label „OVERALL" entfernt — nur Zahl).
- **Symmetrisch** (seiten-agnostisch), da Home/Away dieselbe Karte rendern und erst die
  Matchup-Services (Lineup/Results/Settings) sie links/rechts platzieren.

---

## Optionaler Patch (empfohlen) — perfekte Farb-Robustheit
Zwei Randfälle lassen sich nur serverseitig sauber lösen (CSS kann Luminanz nicht verzweigen):

1. **Sehr dunkler Akzent** (z. B. `#000000`) verschwindet auf dem dunklen Grund.
2. **Helle Primärfarbe** (Gelb/Grün) braucht dunkle Rating-Schrift statt weiß.

> **Dunkle Primärfarbe** (z. B. AHR `#000000`): bereits rein per CSS gelöst —
> `--primary-vis: oklch(from var(--primary) max(l, 0.62) c h)` hebt sehr dunkle
> Primärfarben für Punkte-Wert & Seitenschienen auf eine sichtbare Helligkeit an
> (Farbton bleibt). Kein Backend nötig.

Das Template fällt ohne Patch elegant zurück (roher Akzent / weiße Rating-Schrift).
Mit diesem Mini-Patch in `TeamCardService.java` wird es exakt — Stil analog zum
bereits vorhandenen `computeGradientColor`:

```java
// in generateCard(...), direkt nach gradientColor:
ctx.setVariable("accentVisColor", computeAccentVisColor(accentColor, primaryColor));
ctx.setVariable("onPrimaryColor", contrastColor(primaryColor));

// --- neue Helfer (relativeLuminance existiert bereits) ---

/** Sehr dunkler Akzent -> auf primary ausweichen, damit er auf dunklem Grund sichtbar bleibt. */
String computeAccentVisColor(String accent, String primary) {
    if (accent == null) return primary;
    return relativeLuminance(accent) < 28 ? primary : accent;   // 28 ≈ #1a1a1a
}

/** Lesbare Textfarbe auf einer Fläche: dunkel auf hell, weiß auf dunkel. */
String contrastColor(String hex) {
    return relativeLuminance(hex) > 140 ? "#0b0b10" : "#ffffff"; // 140 ≈ mittelhell
}
```

> Hinweis: `relativeLuminance` liefert hier 0–255 (0.2126·R + 0.7152·G + 0.0722·B),
> daher die Schwellen 28 bzw. 140 statt 0–1.

---

## Komposit-Grafiken — Carbon-Redesign (Option B)
Einheitliches Aufpeppen der fünf Grafiken **ohne Layout-/Format-Änderung**.
Drop-in-Ersatz in `src/main/resources/templates/admin/`:
`settings-render.html`, `lineup-render.html`, `results-render.html` (Scorecard),
`match-results-render.html`, `provisional-scores-render.html`.

**Alle `th:*`-Bindings bleiben unverändert** — bei 4 der 5 Templates ist es ein reiner
Style-Austausch. Gemeinsames System:
- **Carbon-Kopf-/Fußleisten** (dunkler Verlauf) statt Hellgrau, mit **Gold-Keyline** (Marken-Akzent `#f5c542`).
- **Carbon-Vignette** im Mittelteil statt flachem `#111`.
- **Gold-Akzente** (neutral, keine Teamfarben): Titel-Klammern, HUD-Eckbrackets um den Inhalt,
  Doppelpunkt im Score, Sieger-Unterstreichung, Tabellen-Kopf/Overall.
- Einzeilige Titel, lesbarere Subtitel.

> **Hinweis — CTC-Logo:** `ctcLogoBase64` ist die weiße Münze (`ctc-logo-white.png`) und wird auf den
> dunklen Carbon-Leisten **ohne Filter** verwendet (kein Invert).

### Provisional Scores — zusätzliche Anpassungen (Markup)
- **Teamname entfernt** (geht aus der Karte hervor).
- Titel **einzeilig** (kleinere Schrift, da längster Titel).
- **Race-Label aus dem Header entfernt** → als dezenter **Chip unten rechts**
  (`.race-chip`), und **nur sichtbar wenn `raceLabel != null`**.
  → Empfehlung: Service setzt `raceLabel` **nur bei Matches mit > 1 Rennen** (sonst `null`),
  damit der Chip bei Ein-Rennen-Matches verschwindet. Aktuell setzt
  `ProvisionalScoresGraphicService` `raceLabel = "Race " + raceIndex` immer.
- Größere Karte mit Gold-Trennlinie zur Tabelle, Zebra-Zeilen, HUD-Eckbracket je Block,
  neutrale Gold-Blockkante (statt blau/rot).

---

## Matchday-/Listen-Grafiken — Carbon-Redesign
Drop-in-Ersatz in `src/main/resources/templates/admin/`:
`matchday-schedule-render.html`, `matchday-overview-render.html`,
`standings-render.html`, `matchday-results-render.html`, `power-rankings-render.html`.

Gleiches System wie die Komposite (Carbon-Vignette, Gold-Akzent, Gold-Keyline unter dem Kopf,
Titel-Klammern). **Alle `th:*`-Bindings bleiben**, kleine Anpassungen:
- **Team-Zeilen entflacht:** statt vollflächigem Farbverlauf → **Carbon-Zeile + Teamfarben-Kante + dezenter Glow**.
  Der `th:style` füttert jetzt `--c: <teamColor>` statt des Gradienten; die sichtbare Akzentfarbe entsteht
  in CSS via `--cv: oklch(from var(--c) max(l,0.5) c h)` (hebt sehr dunkle Farben wie `#000` an — Chromium-only, ok da Playwright).
- **Logos in Chips:** dunkler Coin + Teamfarben-Ring + heller/dunkler Halo → jedes Logo (hell wie dunkel) lesbar.
- **Standings:** nutzt weiterhin die serverseitigen `rowHeightPx`/`fontSizePx`/`logoSizePx`/`posFontSizePx`
  → alle Teams passen immer (dynamische Zeilenhöhe). Gold statt Blau, Zebra-Zeilen, Leader-Zeile hervorgehoben.
- **CTC-Logo im Kopf** wird hier **nicht** invertiert (es ist bereits eine weiße Münze auf dunklem Grund).

## Stream Overlay — Carbon (Option A)
Drop-in-Ersatz für `overlay-render.html`. **Geometrie & Positionen exakt unverändert**
(top-bar 921×120 @ 500/0, bottom-bar 1275×148 @ 218/924, Skew, CTC-Ecke), **Hintergrund transparent**.
Nur Füllung/Akzente angepasst: halbtransparente Carbon-Bars + Teamfarben-Kante/Glow, Logo-Chips,
Gold-Records, gold-gerahmte VS-Box (echtes `vsBadgeBase64`), Carbon-Bottom-Bar mit Gold-Keyline.
`th:*`-Bindings (inkl. `vsBadgeBase64`, `commentatorBase64`, `ctcLogoBase64` weiß, ohne Invert) unverändert.

---

## Dateien in diesem Projekt
- `handoff-templates/` — **alle 12 Produktions-Templates** (1:1 nach `src/main/resources/templates/admin/`):
  Karte (`team-card-render`), Komposite (`settings-`, `lineup-`, `results-`, `match-results-`, `provisional-scores-render`),
  Matchday/Listen (`matchday-schedule-`, `matchday-overview-`, `standings-`, `matchday-results-`, `power-rankings-render`),
  Stream-Overlay (`overlay-render`).
- `index.html` + `v4.css` — Karten-Vorschau (alle Teams, echte Logos).
- `redesign.html` + `composite.css` + `composites.js` — Vorschau der 5 Komposite (Richtung A/B).
- `graphics-proposals.html` — Vorschau der 5 Matchday/Listen-Grafiken (14 echte Teams).
- `logos/` — echte Test-Logos.

## Test
Live im Admin-Template-Editor (`/admin/tools/template-editors?tab=team-cards`) einfügen
→ Vorschau (Sample „Team Alpha"). Danach echte Karten via
`/admin/tools/team-cards` → *Generate* prüfen.
