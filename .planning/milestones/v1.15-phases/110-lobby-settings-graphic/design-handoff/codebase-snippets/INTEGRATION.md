# GT7 Lobby Settings Übersicht — Integration in CTC Manager

Neue Grafik **„Lobby Settings"** (Variante C / Ledger), die ALLE GT7-Lobby-Einstellungen
als **1920×1080 PNG** rendert — über exakt dieselbe Pipeline wie die bestehende
Race-Settings-Grafik (Thymeleaf → Playwright-Screenshot). **Keine neue Bibliothek nötig.**

## Dateien aus diesem Projekt

| Datei hier | Ziel in der Code Base |
|---|---|
| `codebase/lobby-settings-render.html` | `src/main/resources/templates/admin/lobby-settings-render.html` |
| `codebase/template-editor-tab.html` | Snippet zum Einfügen in `templates/admin/template-editors.html` |

> Die `<img>`-Tags im Template haben einen natürlichen Fallback-`src`
> (`../../static/admin/img/ctc-logo-white.png`). Beim Thymeleaf-Render überschreibt
> `th:src="${ctcLogoBase64}"` das; im Playwright-Screenshot wird das Base64-Logo verwendet.

---

## Das „fest ↔ match-spezifisch"-Prinzip

Das Template ist ein **Natural Template**: Default-Werte stehen als **fester Text** im
Element. Match-spezifische Werte tragen zusätzlich ein `th:text` mit Default-Fallback:

```html
<!-- FEST (League-Default, im Template gepflegt) -->
<div class="row"><span class="set-label">Boost</span><span class="set-value">Off</span></div>

<!-- MATCH-SPEZIFISCH (zur Laufzeit aus dem Race befüllt, Default als Fallback) -->
<div class="row"><span class="set-label">Track</span>
  <span class="set-value" th:text="${v.track} ?: 'Circuit de Sainte-Croix · B Reverse'">Circuit de Sainte-Croix · B Reverse</span></div>
```

- **Fest → spezifisch:** `th:text="${v.key} ?: 'aktueller Wert'"` an den Wert hängen.
- **Spezifisch → fest:** das `th:text` entfernen.
- Fehlt ein `v.*`-Key im Render, greift der `?:`-Default → **kein** Template-Fehler.

So entscheidest du **pro Liga/Saison im Template-Editor**, welche Properties fest sind und
welche aus dem Match kommen — ohne alle ~60 Properties in einer DB-Tabelle zu pflegen.
Variabel ist nur, was du explizit als `${v.*}` markierst (Vorschlag siehe unten).

---

## Service (mirror von `SettingsGraphicService`)

`org.ctc.admin.service.LobbySettingsGraphicService` — 1:1 am bestehenden Muster:

```java
@Slf4j
@Service
public class LobbySettingsGraphicService extends AbstractGraphicService implements TemplateManageable {

    private static final String DEFAULT_TEMPLATE = "templates/admin/lobby-settings-render.html";
    private static final String CUSTOM_TEMPLATE_FILE = "lobby-settings-template.html";

    public LobbySettingsGraphicService(TemplateEngine templateEngine,
                                       @Value("${app.upload-dir:uploads}") String uploadDir) {
        super(templateEngine, uploadDir);
    }

    public String generateLobbySettings(Race race) throws IOException {
        if (race.getTrack() == null || race.getSettings() == null) {
            throw new IllegalStateException("Race has no track / settings");
        }
        var s        = race.getSettings();
        var season   = race.getMatchday().getSeason();
        var home     = race.getHomeTeam();
        var away     = race.getAwayTeam();

        // Match-spezifische Werte → eine Map "v" (nur was variabel sein soll!)
        Map<String, Object> v = new HashMap<>();
        v.put("roomName",     "CTC – " + season.getYear() + " – " + race.getMatchday().getLabel()
                              + (home != null && away != null
                                 ? " – " + home.getShortName() + " vs. " + away.getShortName() : ""));
        v.put("track",        race.getTrack().getName());
        v.put("laps",         s.getNumberOfLaps());
        v.put("weather",      s.getWeather());
        v.put("timeOfDay",    s.getTimeOfDay());
        v.put("timeSpeed",    s.getTimeProgressionMultiplier() + "×");
        v.put("tyreWear",     s.getTyreWearMultiplier() + "×");
        v.put("fuelRate",     s.getFuelConsumptionMultiplier() + "×");
        v.put("refuelSpeed",  s.getRefuelingSpeed() + " Litre/sec");
        v.put("initialFuel",  s.getInitialFuel());
        v.put("minPitStops",  s.getNumberOfRequiredPitStops());
        v.put("usableTyres",  s.getAvailableTyres());
        v.put("requiredTyre", s.getMandatoryTyres());
        // v.put("category", race.getCar() != null ? race.getCar().getCategory() : null); // falls vorhanden
        // v.put("mechDamage", ...);                                                       // falls gepflegt

        var ctx = new Context();
        ctx.setVariable("fontBase64",    encodeClasspathResource(FONT_CLASSPATH, "font/woff2"));
        ctx.setVariable("ctcLogoBase64", encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"));
        ctx.setVariable("seasonYear",    String.valueOf(season.getYear()));
        ctx.setVariable("matchdayName",  race.getMatchday().getLabel()
                                          + (home != null && away != null
                                             ? " · " + home.getShortName() + " vs. " + away.getShortName() : ""));
        ctx.setVariable("seasonName",    season.getName() + " · " + season.getYear());
        ctx.setVariable("v",             v);

        String html = renderTemplate(ctx);

        Path raceDir = uploadDir.resolve("races").resolve(race.getId().toString());
        Files.createDirectories(raceDir);
        Path out = raceDir.resolve("lobby-settings.png");
        renderScreenshot(html, out);                 // bestehender 1920×1080-Screenshot, fullPage:false
        return "/uploads/races/" + race.getId() + "/lobby-settings.png";
    }

    // renderTemplate / loadTemplate / loadDefaultTemplate / saveTemplate / resetTemplate /
    // hasCustomTemplate: identisch zu SettingsGraphicService, nur mit DEFAULT_TEMPLATE /
    // CUSTOM_TEMPLATE_FILE von oben und templateEngine.process("admin/lobby-settings-render", ctx).
}
```

**Wichtig — Map statt Einzelvariablen:** `${v.foo}` auf einer `Map` ergibt `null` (statt
Exception), wenn der Key fehlt. Zusammen mit `?:` macht das das Custom-Template-Editing
robust: Admins dürfen Variablen frei zu-/wegschalten, ohne Render-Fehler zu riskieren.

---

## Restliche Verdrahtung (knapp)

1. **Controller** `RaceController`: Methode analog zu `generateSettings` —
   `raceGraphicService.generateLobbySettings(id)` bzw. neuer Aufruf; Button auf
   `race-detail.html` („Generate Lobby Settings Graphic").
2. **RaceGraphicService**: `generateLobbySettings(UUID)` →
   `generateAndSaveGraphic(raceId, "LobbySettings", lobbySettingsGraphicService::generateLobbySettings)`.
3. **TemplateEditorController**: `"lobby-settings"` in die Type→Service-Map aufnehmen
   (load/save/reset/preview), `lobbySettingsTemplate` + `lobbySettingsIsCustom` ins Model.
4. **template-editors.html**: Snippet aus `codebase/template-editor-tab.html` einfügen.
5. **Discord**: in `DiscordPostService` denselben `readPng(...generateLobbySettings(race))`-Pfad
   nutzen wie bei der Settings-Grafik → direkt als PNG-Attachment embedbar.

---

## Default-Vorschlag: welche Werte match-spezifisch (`${v.*}`)?

Bereits im Template als spezifisch markiert (mit `<!-- spec -->`):
Room Name · Track · No. of Laps · Preset Weather · Time of the Day ·
Variable Time Speed Rate · Mechanical Damage · Tyre Wear Rate · Fuel Consumption Rate ·
Refuelling Speed · Initial Fuel · Minimum No. of Pit Stops · Filter by Category ·
Usable Tyre & Types · Required Tyre Type.

Alles andere ist fester League-Default — im Template-Editor jederzeit umschaltbar.
