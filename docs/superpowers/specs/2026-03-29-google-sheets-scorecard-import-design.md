# Google Sheets Scorecard Import

## Kontext

Die CTC Rennliga verwaltet ihre Scorecard-Ergebnisse in Google Sheets (je eine Scorecard pro Match). Derzeit können Ergebnisse nur per manuell erstellter CSV-Datei importiert werden. Ziel ist es, Scorecards direkt aus Google Sheets zu lesen und in das bestehende Import-Format zu überführen — primär für die Migration historischer Saisons (Regular Season und Playoffs).

## Scorecard-Format (Google Sheet)

Zwei Team-Blöcke pro Sheet, jeweils gleicher Aufbau:

```
[Team-Name]   Position  Quali  FL  Points Race  Points Quali  Points FL  Points
[PSN-ID]      [1-12]    [1-12] [☐] (berechnete Felder...)
...           (n Fahrer pro Team, variabel)
Overall       (Summen)
(Leerzeilen)
[Team-Name]   Position  Quali  FL  ...
...           (n Fahrer)
Overall       (Summen)
```

Relevante Daten: Spalten A-D (PSN-ID, Position, Quali, FL) + Team-Namen aus der ersten Spalte jedes Blocks. Die Anzahl der Fahrer pro Team ist variabel.

## Architektur

Der bestehende Import-Flow (`CsvImportService` → Preview → Execute) bleibt unverändert. Google Sheets wird als **alternative Datenquelle** hinzugefügt, die Sheet-Daten in dasselbe `ImportPreview`-Format transformiert.

```
[Import-Formular]
    ├─ CSV Upload → CsvImportService.parseAndPreview()
    └─ Google Sheet URL → GoogleSheetsService.readSheet()
                            → ScorecardParser.parse()
                            → ImportPreview (identisch zum CSV-Flow)
                                → Preview-Seite → Execute
```

## Komponenten

### 1. `GoogleSheetsService` (neuer Service in `org.ctc.dataimport`)

Liest Zelldaten per Google Sheets API v4.

- **Konfiguration** per Spring Properties:
  ```properties
  google.sheets.credentials-path=   # Pfad zur Service Account JSON-Datei
  ```
- Feature ist **verfügbar wenn** `credentials-path` gesetzt und die Datei existiert. Unabhängig vom Spring-Profil — auch in `dev` testbar.
- **Methoden:**
  - `boolean isAvailable()` — prüft ob Credentials konfiguriert sind
  - `List<List<Object>> readRange(String spreadsheetId, String range)` — liest einen Zellbereich
  - `String extractSpreadsheetId(String url)` — extrahiert die ID aus einer Google Sheets URL

### 2. `ScorecardParser` (neuer Service in `org.ctc.dataimport`)

Transformiert das Scorecard-Layout in flache Import-Rows.

- **Input:** `List<List<Object>>` (Rohdaten aus Google Sheets API)
- **Output:** `ImportPreview` (identisch zum CSV-Flow)
- **Mapping-Logik:**
  - Erkennt Team-Blöcke dynamisch: Eine Zeile mit Header-Spalten (Position, Quali, FL) startet einen neuen Block
  - Team-Name: Spalte A der Block-Header-Zeile
  - Fahrer-Zeilen: Alle Zeilen nach dem Header bis zur nächsten "Overall"-Zeile oder Leerzeile
  - Checkbox-Werte (`TRUE`/`FALSE`) werden zu boolean konvertiert
  - "Overall"-Zeilen und leere Zeilen werden übersprungen
  - Anzahl der Fahrer pro Team ist variabel (nicht auf 6 festgelegt)
  - Ergebnis: `n` `ImportRow`-Objekte mit Team, PSN-ID, Position, Quali, FL
- **Team-Matching:** Normalisierung von Team-Namen (Leerzeichen ↔ Unterstriche, case-insensitive). Nutzt das gleiche Fuzzy-Matching-Muster wie `DriverMatchingService` — falls kein exakter Match, wird die beste Übereinstimmung im Preview zur Bestätigung angeboten.

### 3. Erweiterung `CsvImportController`

Neuer Endpunkt für den Google Sheets Import:

- `POST /admin/import/preview-sheet` — nimmt `sheetUrl`, `seasonName`, `matchdayLabel`, `track`, `car` und optional `playoffMatchupId` entgegen
- Ruft `GoogleSheetsService.readRange()` auf, dann `ScorecardParser.parse()`
- Gibt dasselbe `import-preview.html` Template zurück wie der CSV-Flow
- Der Execute-Endpunkt bleibt unverändert (arbeitet bereits auf dem `ImportPreview`-Objekt)

### Playoff-Support

Das Datenmodell unterscheidet Regular Season und Playoff:

- **Regular Season:** `Race` → `Matchday` → `Season`
- **Playoff:** `Race` → `PlayoffMatchup` → `PlayoffRound` → `Playoff` → `Season`

Das Scorecard-Format ist identisch. Der Unterschied liegt im Import-Ziel:

- `ImportMetadata` wird um ein optionales `playoffMatchupId` (UUID) erweitert
- Im Import-Formular: Auswahl zwischen "Regular Season Matchday" und "Playoff Matchup"
  - Bei Regular Season: Matchday-Label wie bisher
  - Bei Playoff: Dropdown mit verfügbaren Playoff-Matchups der gewählten Season
- `CsvImportService.executeImport()` prüft ob `playoffMatchupId` gesetzt ist:
  - Wenn ja: Race wird mit dem `PlayoffMatchup` verknüpft (statt nur mit Matchday)
  - Wenn nein: Standard-Flow (Race → Matchday)
- Dieser Playoff-Support gilt für beide Import-Wege (CSV und Google Sheet)

### 4. Erweiterung Import-Template (`import.html`)

Das bestehende Formular wird um eine **Google Sheets Option** erweitert:

- Tab-basierte Auswahl: "CSV Upload" | "Google Sheet" (nur sichtbar wenn `sheetsAvailable` = true)
- Bei "Google Sheet": URL-Eingabefeld statt File-Upload
- Metadata-Felder (Season, Matchday, Track, Car) bleiben identisch
- JavaScript-Toggle zwischen den beiden Eingabemodi

### 5. Maven Dependencies

```xml
<!-- Google Sheets API v4 -->
<dependency>
    <groupId>com.google.api-client</groupId>
    <artifactId>google-api-client</artifactId>
    <version>2.7.2</version>
</dependency>
<dependency>
    <groupId>com.google.apis</groupId>
    <artifactId>google-api-services-sheets</artifactId>
    <version>v4-rev20250106-2.0.0</version>
</dependency>
<dependency>
    <groupId>com.google.auth</groupId>
    <artifactId>google-auth-library-oauth2-http</artifactId>
    <version>1.33.1</version>
</dependency>
```

## Konfiguration & Sicherheit

- `credentials.json` wird per `.gitignore` ausgeschlossen
- Service Account benötigt nur **Lesezugriff** (Viewer) auf die geteilten Scorecards
- Spreadsheet-ID wird per Regex aus der URL validiert
- Kein Zugriff auf andere Sheets möglich (nur Sheets, die explizit mit dem Service Account geteilt wurden)

## Google Cloud Setup (einmalig, Anleitung für User)

1. Google Cloud Projekt erstellen (kostenlos)
2. Google Sheets API aktivieren
3. Service Account erstellen → JSON-Key herunterladen
4. CTC Drive-Ordner mit Service Account E-Mail teilen (Lesezugriff)
5. JSON-Key-Pfad in `application.properties` oder Umgebungsvariable setzen

## Dateien

### Neu erstellen
- `src/main/java/org/ctc/dataimport/GoogleSheetsService.java`
- `src/main/java/org/ctc/dataimport/ScorecardParser.java`
- `src/test/java/org/ctc/dataimport/ScorecardParserTest.java`
- `src/test/java/org/ctc/dataimport/GoogleSheetsServiceTest.java`
- `src/test/java/org/ctc/e2e/GoogleSheetsImportE2eTest.java`

### Modifizieren
- `pom.xml` — Google API Dependencies hinzufügen
- `src/main/java/org/ctc/dataimport/CsvImportController.java` — neuer Endpunkt `preview-sheet`
- `src/main/resources/templates/admin/import.html` — Google Sheet URL-Eingabe hinzufügen
- `src/main/resources/application.properties` — Property-Defaults
- `.gitignore` — Credentials-Datei ausschließen

### Wiederverwenden
- `CsvImportService.ImportPreview`, `ImportRow`, `ImportResult` — identischer Preview/Execute-Flow
- `DriverMatchingService` — Fahrer-Matching für Google Sheet Daten
- `ScoringService.calculatePoints()` — Punkteberechnung nach Import

## Verifikation

1. **Unit-Tests:** `ScorecardParserTest` — testet Transformation des Sheet-Layouts in Import-Rows (verschiedene Konstellationen: vollständig, unterschiedliche Fahrerzahlen, unterschiedliche Team-Namen)
2. **Unit-Tests:** `GoogleSheetsServiceTest` — testet URL-Parsing, Spreadsheet-ID-Extraktion
3. **E2E-Test (Playwright):** `GoogleSheetsImportE2eTest` — testet den gesamten UI-Flow (Import-Seite → Google Sheet URL eingeben → Preview → Execute) mit einem gemockten `GoogleSheetsService` im `dev`-Profil. So wird der Controller + Template + Preview-Flow ohne echte Google-API-Abhängigkeit getestet.
4. **Manueller Integrationstest:**
   - Google Cloud Projekt + Service Account einrichten
   - Credentials-Pfad konfigurieren
   - App starten, Import-Seite öffnen
   - Google Sheet URL einer bestehenden Scorecard eingeben
   - Preview prüfen: korrekte Teams/Fahrer/Positionen
   - Import ausführen, Ergebnisse in der DB verifizieren
