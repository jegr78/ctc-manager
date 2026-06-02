# Handoff: GT7 „Lobby Settings"-Übersicht (CTC Manager)

## Überblick
Neue Admin-Grafik **„Lobby Settings"**, die **alle** GT7-Lobby-Einstellungen (9 Sektionen,
~60 Werte) als **1920×1080 PNG** rendert — direkt in Discord einbettbar. Sie ersetzt die
bisher per Google Sheet gepflegte Lobby-Übersicht und nutzt **exakt dieselbe Pipeline**
wie die bestehende Race-Settings-Grafik (Thymeleaf-Template → Playwright-Screenshot).
**Keine neue Bibliothek** (kein PDF-Renderer) nötig.

## Zu den Dateien in diesem Bundle
Anders als ein typischer Design-Handoff sind das **keine** in ein anderes Framework zu
übersetzenden HTML-Mockups. Eure Zielumgebung **ist** server-seitiges Thymeleaf +
Playwright — die hier gelieferte Vorlage ist **direkt einsatzbereit** und folgt dem
Muster von `SettingsGraphicService` / `settings-render.html`. Aufgabe ist „einhängen &
verdrahten", nicht „neu bauen".

| Datei im Bundle | Ziel im Repo |
|---|---|
| `templates/lobby-settings-render.html` | `src/main/resources/templates/admin/lobby-settings-render.html` |
| `java/LobbySettingsGraphicService.java` | `src/main/java/org/ctc/admin/service/LobbySettingsGraphicService.java` |
| `snippets/template-editor-tab.html` | Snippet → `templates/admin/template-editors.html` |
| `preview/Lobby Settings - Render Preview.html` | nur Referenz (aufgelöstes PNG-Vorbild) |

## Fidelity
**High-fidelity, drop-in.** Endgültige Farben, Typografie, Spacing und Layout. Pixelmaße
sind exakt; das Template ist fertig. Verifiziert: 1920×1080, alle 8 Sektionen in 4
balancierten Spalten, kein Clipping (auch bei langen Werten wie Custom-Weather-Sequenzen).

---

## Das „fest ↔ match-spezifisch"-Prinzip (Kern der Anforderung)
Das Template ist ein **Natural Template**:

```html
<!-- FEST (League-Default, nur im Template gepflegt) -->
<div class="row"><span class="set-label">Boost</span><span class="set-value">Off</span></div>

<!-- MATCH-SPEZIFISCH (zur Laufzeit aus dem Race, Default als Fallback) -->
<div class="row"><span class="set-label">Track</span>
  <span class="set-value" th:text="${v.track} ?: 'Circuit de Sainte-Croix · B Reverse'">Circuit de Sainte-Croix · B Reverse</span></div>
```

- **fest → spezifisch:** `th:text="${v.key} ?: 'aktueller Default'"` an den `.set-value` hängen.
- **spezifisch → fest:** das `th:text` entfernen.
- Fehlt ein `v.*`-Key beim Render → `?:`-Default greift, **kein** Template-Fehler
  (deshalb `v` als `Map`: `${v.foo}` ist `null` statt Exception, wenn der Key fehlt).

Admins steuern das komfortabel über den **Template-Editor** (neuer Tab, s.u.) — es muss
**nicht** jede der ~60 Properties in einer DB-Tabelle gepflegt werden.

---

## Integration — Schritt für Schritt

### 1. Template ablegen
`templates/lobby-settings-render.html` → `src/main/resources/templates/admin/`.

### 2. Service anlegen
`java/LobbySettingsGraphicService.java` → `src/main/java/org/ctc/admin/service/`.
Bean-Name ist automatisch `lobbySettingsGraphicService`; durch `implements TemplateManageable`
landet er automatisch in der `Map<String, TemplateManageable> templateServices` des
`TemplateEditorController`.

> **Daten-Mappings:** „Filter by Category" (fest „—") und „Mechanical Damage" (fest
> „Light") sind im Template hart verdrahtet — kein Mapping nötig. Optional bleibt nur
> `v.customWeather` (im Service als `TODO` auskommentiert; ohne Wert greift der Default „—").

### 3. `RaceGraphicService` erweitern
`src/main/java/org/ctc/admin/service/RaceGraphicService.java`:

```java
// Feld ergänzen (Klasse ist @RequiredArgsConstructor):
private final LobbySettingsGraphicService lobbySettingsGraphicService;

// Methode ergänzen (neben generateSettings):
@Transactional
public void generateLobbySettings(UUID raceId) {
    generateAndSaveGraphic(raceId, "LobbySettings", lobbySettingsGraphicService::generateLobbySettings);
}
```
(`generateAndSaveGraphic` benennt den Anhang u.a. aus `race.getHomeTeam().getShortName()` —
wie bei den anderen Grafiken sind dafür Teams am Race vorausgesetzt.)

### 4. `RaceController` — Endpoint
`src/main/java/org/ctc/admin/controller/RaceController.java`, analog zu `generateSettings`:

```java
@PostMapping("/{id}/generate-lobby-settings")
public String generateLobbySettings(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
        raceGraphicService.generateLobbySettings(id);
        redirectAttributes.addFlashAttribute("successMessage", "Lobby settings graphic generated");
    } catch (RuntimeException e) {
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    }
    return "redirect:/admin/races/" + id;
}
```

### 5. Button auf `race-detail.html`
In den Button-Block (nach dem „Settings Button") einfügen:

```html
<!-- Lobby Settings Button -->
<form th:action="@{/admin/races/{id}/generate-lobby-settings(id=${race.id})}" method="post">
    <button type="submit" class="btn btn-primary">Generate Lobby Settings Graphic</button>
</form>
```
Immer aktiv; bei unvollständigen Settings kommt eine `errorMessage`-Flash-Meldung. Optional
analog zu `canGenerateSettings` ein `canGenerateLobbySettings`-Flag in `RaceService` gaten
(Lobby-Grafik braucht **keine** Team-Cards, nur vollständige Settings).

### 6. ⚠️ `TemplateEditorController` — drei Maps erweitern (BUILD-BREAKER beachten!)
`src/main/java/org/ctc/admin/controller/TemplateEditorController.java`.
Die drei `Map.of(...)` haben **je genau 10 Einträge** — `Map.of` unterstützt **maximal 10**
Paare. Der 11. Eintrag bricht den Build. Daher **alle drei auf `Map.ofEntries(...)`
umstellen** und je einen Eintrag ergänzen:

```java
import static java.util.Map.entry;   // oben ergänzen

private static final Map<String, String> TEMPLATE_TYPE_TO_BEAN = Map.ofEntries(
    entry("team-cards",        "teamCardService"),
    entry("lineup",            "lineupGraphicService"),
    entry("settings",          "settingsGraphicService"),
    entry("race-results",      "resultsGraphicService"),
    entry("match-results",     "matchResultsGraphicService"),
    entry("matchday-overview", "matchdayOverviewGraphicService"),
    entry("matchday-schedule", "matchdayScheduleGraphicService"),
    entry("matchday-results",  "matchdayResultsGraphicService"),
    entry("overlay",           "overlayGraphicService"),
    entry("power-rankings",    "powerRankingsGraphicService"),
    entry("lobby-settings",    "lobbySettingsGraphicService")   // NEU
);

private static final Map<String, String> TEMPLATE_TYPE_TO_ATTR = Map.ofEntries(
    entry("team-cards",        "teamCard"),
    entry("lineup",            "lineup"),
    entry("settings",          "settings"),
    entry("race-results",      "raceResults"),
    entry("match-results",     "matchResults"),
    entry("matchday-overview", "matchdayOverview"),
    entry("matchday-schedule", "matchdaySchedule"),
    entry("matchday-results",  "matchdayResults"),
    entry("overlay",           "overlay"),
    entry("power-rankings",    "powerRankings"),
    entry("lobby-settings",    "lobbySettings")                 // NEU → Model-Attrs: lobbySettingsTemplate / lobbySettingsIsCustom
);

private static final Map<String, String> TEMPLATE_TYPE_TO_LABEL = Map.ofEntries(
    entry("team-cards",        "Team card"),
    entry("lineup",            "Lineup"),
    entry("settings",          "Settings"),
    entry("race-results",      "Race results"),
    entry("match-results",     "Match results"),
    entry("matchday-overview", "Matchday overview"),
    entry("matchday-schedule", "Matchday schedule"),
    entry("matchday-results",  "Matchday results"),
    entry("overlay",           "Overlay"),
    entry("power-rankings",    "Power rankings"),
    entry("lobby-settings",    "Lobby settings")                // NEU
);
```

### 7. `TemplatePreviewService` — Preview-Kontext fürs Editor-Live-Preview
`src/main/java/org/ctc/admin/service/TemplatePreviewService.java`.
Ohne diesen Case wirft die Editor-Vorschau „Unknown template type". Im `switch` in
`renderPreview(...)` ergänzen:

```java
case "lobby-settings" -> buildLobbySettingsContext();
```

und die Methode hinzufügen:

```java
private Context buildLobbySettingsContext() {
    var ctx = new Context();
    ctx.setVariable("fontBase64", getFontBase64());
    ctx.setVariable("ctcLogoBase64", getLogoBase64());
    ctx.setVariable("seasonYear", "2026");
    ctx.setVariable("seasonName", "Community Team Cup · 2026");
    var v = new java.util.HashMap<String, Object>();
    v.put("roomName", "CTC – 2026 – MD4 – P1R vs. VEZ");
    v.put("track", "Circuit de Sainte-Croix · B Reverse");
    v.put("laps", 19);
    v.put("weather", "S01");
    v.put("timeOfDay", "Afternoon");
    v.put("timeSpeed", "3×");
    v.put("tyreWear", "4×");
    v.put("fuelRate", "3×");
    v.put("refuelSpeed", "3 Litre/sec");
    v.put("initialFuel", "Default");
    v.put("minPitStops", 0);
    v.put("usableTyres", "Soft, Medium, Hard, Intermediate, Wet");
    v.put("requiredTyre", "None");
    ctx.setVariable("v", v);
    return ctx;
}
```
(Die Security-Whitelist in `validateTemplateContent` ist unkritisch — das Template nutzt
nur `${v.*}` und `?:`, keine blockierten Tokens / `T(` / `@`-Zugriffe.)

### 8. Editor-Tab im UI
Snippet aus `snippets/template-editor-tab.html` in `templates/admin/template-editors.html`
einfügen: (a) Tab-Button in die `.editor-tab-bar`, (b) Tab-Content-Block. Erwartet die
Model-Attribute `lobbySettingsTemplate` / `lobbySettingsIsCustom` (aus Schritt 6).

### 9. (Optional) Discord
In `DiscordPostService` denselben Pfad wie bei der Settings-Grafik nutzen, z.B.
`readPng(lobbySettingsGraphicService.generateLobbySettings(race))`, um das PNG direkt als
Attachment zu posten.

---

## Design-Tokens
- **Font:** Conthrax (`ConthraxSb.woff2`, bereits im Classpath) für Header/Labels/Titel;
  Werte in `Helvetica Neue, Arial, sans-serif` (Lesbarkeit bei dichter Tabelle).
- **Akzent / Keyline:** `#f5c542` (Gold).
- **Flächen:** Header/Footer `linear-gradient(#202028 → #121217)`; Body
  `radial-gradient(#15151b → #08080b)` + diagonale Carbon-Streifen.
- **Sektionskarte:** bg `rgba(255,255,255,.022)`, border `rgba(255,255,255,.05)`, radius 10px.
- **Zeilen:** Label `#82828e` (uppercase, 10.5px Conthrax), Wert `#ecedf2` (15px),
  Zebra-Streifen `rgba(255,255,255,.022)`, Wertspalte max. 58% Breite (rechtsbündig, umbrechend).
- **Canvas:** Body fix **1920×1080** (= Playwright-Viewport, `fullPage:false`), 4 Spalten
  (`column-count: 4; column-fill: balance`).

## Sektionen & match-spezifische Defaults
Sektionen (Reihenfolge wie im Sheet): General · Track Settings · Time / Weather Settings ·
Race Settings · Qualifier Settings · Regulation Settings · Penalty Settings · Driving Options
Limitations.

Im Template bereits als match-spezifisch markiert (mit `<!-- spec -->`, via `${v.*}`):
Room Name · Track · No. of Laps · Preset Weather · Time of the Day · Variable Time Speed
Rate · Custom Weather · Tyre Wear Rate · Fuel Consumption Rate · Refuelling Speed ·
Initial Fuel · Minimum No. of Pit Stops · Usable Tyre & Types · Required Tyre Type. Alles
andere ist fester League-Default — u.a. **Filter by Category** („—") und **Mechanical
Damage** („Light").

## Assets
- Conthrax-Font & CTC-Logo werden zur Laufzeit aus dem **Classpath** als Base64 eingebettet
  (`encodeClasspathResource`) — wie bei den bestehenden Grafiken. Im Bundle-Preview sind sie
  fest eingebettet, damit es offline korrekt aussieht.
