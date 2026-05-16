package org.ctc.dataimport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.dataimport.CsvImportService.ImportMetadata;
import org.ctc.dataimport.CsvImportService.ImportPreview;
import org.ctc.dataimport.CsvImportService.ImportRow;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScorecardParser {

	private final DriverMatchingService driverMatchingService;

	/**
	 * Parses raw Google Sheets data (List of rows, each row a List of cell values)
	 * into an ImportPreview. Detects team blocks dynamically by looking for header rows
	 * where column B contains "Position".
	 */
	public ImportPreview parse(List<List<Object>> sheetData, ImportMetadata metadata) {
		var preview = new ImportPreview(metadata);

		if (sheetData == null || sheetData.isEmpty()) {
			preview.addError("Sheet data is empty");
			return preview;
		}

		String currentTeam = null;
		boolean inTeamBlock = false;

		for (int i = 0; i < sheetData.size(); i++) {
			var row = sheetData.get(i);

			// Skip empty rows
			if (row == null || row.isEmpty()) {
				inTeamBlock = false;
				currentTeam = null;
				continue;
			}

			// Check if this is a header row (team block start)
			if (isHeaderRow(row)) {
				currentTeam = cleanTeamName(cellToString(row.get(0)));
				inTeamBlock = true;
				log.debug("Team block detected: '{}' at row {}", currentTeam, i + 1);
				continue;
			}

			// Check if this is the "Overall" summary row (team block end)
			if (isOverallRow(row)) {
				log.debug("Overall row at row {} — team block '{}' ended", i + 1, currentTeam);
				inTeamBlock = false;
				currentTeam = null;
				continue;
			}

			// If we're inside a team block, parse driver row
			if (inTeamBlock && currentTeam != null) {
				parseDriverRow(row, currentTeam, i + 1, preview);
			}
		}

		log.info("Scorecard parsed: {} driver rows, {} errors",
				preview.getRows().size(), preview.getErrors().size());
		return preview;
	}

	/**
	 * Parses multiple race sheets from a Google Spreadsheet.
	 * Reads each race sheet (sheet name provided) and returns a list of ImportPreview objects.
	 *
	 * @param sheetDataMap   map of sheet names to raw sheet data (List of rows)
	 * @param raceSheetNames list of race sheet names to parse (in order)
	 * @param metadata       import metadata (applied to all races)
	 * @return list of ImportPreview objects, one per race sheet
	 */
	public List<ImportPreview> parseMultipleRaces(Map<String, List<List<Object>>> sheetDataMap,
	                                              List<String> raceSheetNames,
	                                              ImportMetadata metadata) {
		var previews = new ArrayList<ImportPreview>();

		for (String sheetName : raceSheetNames) {
			var sheetData = sheetDataMap.get(sheetName);
			if (sheetData == null) {
				log.warn("Race sheet '{}' not found in data map, skipping", sheetName);
				continue;
			}

			log.info("Parsing race sheet: {}", sheetName);
			var preview = parse(sheetData, metadata);
			previews.add(preview);
		}

		log.info("Parsed {} race sheets", previews.size());
		return previews;
	}

	private boolean isHeaderRow(List<Object> row) {
		if (row.size() < 4) {
			return false;
		}
		var colB = cellToString(row.get(1));
		return "position".equalsIgnoreCase(colB.trim());
	}

	private boolean isOverallRow(List<Object> row) {
		var colA = cellToString(row.get(0));
		return "overall".equalsIgnoreCase(colA.trim());
	}

	private String cleanTeamName(String teamName) {
		if (teamName == null) {
			return "";
		}
		return teamName.trim();
	}

	private void parseDriverRow(List<Object> row, String teamShortName, int rowNumber, ImportPreview preview) {
		if (row.size() < 4) {
			preview.addError("Row " + rowNumber + ": Too few columns (expected at least 4: PSN ID, Position, Quali, FL)");
			return;
		}

		var psnId = cellToString(row.get(0)).trim();
		if (psnId.isEmpty()) {
			preview.addError("Row " + rowNumber + ": PSN ID is empty");
			return;
		}

		var positionStr = cellToString(row.get(1)).trim();
		var qualiStr = cellToString(row.get(2)).trim();

		Integer position = parseIntSafe(positionStr, "Position", rowNumber, preview);
		Integer qualiPosition = parseIntSafe(qualiStr, "Quali", rowNumber, preview);

		if (position == null || qualiPosition == null) {
			return;
		}

		boolean fastestLap = parseFastestLap(row.get(3));

		var matchResult = driverMatchingService.findDriver(psnId);
		preview.addRow(new ImportRow(teamShortName, psnId, position, qualiPosition, fastestLap, matchResult));

		log.debug("Driver parsed: {} (Team: {}, P{}, Q{}, FL: {})",
				psnId, teamShortName, position, qualiPosition, fastestLap);
	}

	private Integer parseIntSafe(String value, String fieldName, int rowNumber, ImportPreview preview) {
		try {
			// Handle decimal strings from Sheets API (e.g. "2.0")
			if (value.contains(".")) {
				return (int) Double.parseDouble(value);
			}
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			preview.addError("Row " + rowNumber + ": Invalid value for " + fieldName + ": " + value);
			return null;
		}
	}

	/**
	 * Parses the fastest-lap cell. Google Sheets checkboxes arrive as Boolean;
	 * other sources may send String representations.
	 */
	private boolean parseFastestLap(Object cell) {
		if (cell instanceof Boolean b) {
			return b;
		}
		var str = cellToString(cell);
		return "true".equalsIgnoreCase(str.trim());
	}

	private String cellToString(Object cell) {
		if (cell == null) {
			return "";
		}
		return cell.toString();
	}
}
