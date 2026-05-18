package org.ctc.dataimport;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GoogleSheetsService {

	private static final String APPLICATION_NAME = "CTC Manager";
	private static final Pattern SPREADSHEET_URL_PATTERN =
			Pattern.compile("https://docs\\.google\\.com/spreadsheets/d/([a-zA-Z0-9_-]+)");
	private static final Pattern SPREADSHEET_ID_PATTERN =
			Pattern.compile("^[a-zA-Z0-9_-]+$");

	private final String credentialsPath;
	private Sheets sheetsClient;

	public GoogleSheetsService(@Value("${google.sheets.credentials-path:}") String credentialsPath) {
		this.credentialsPath = credentialsPath;
	}

	@PostConstruct
	void logAvailability() {
		if (isAvailable()) {
			log.info("Google Sheets integration available (credentials: {})", credentialsPath);
		} else {
			log.info("Google Sheets integration not available (no credentials configured)");
		}
	}

	/**
	 * Checks whether Google Sheets integration is available.
	 * Returns true when a credentials path is configured and the file exists.
	 */
	public boolean isAvailable() {
		return credentialsPath != null
				&& !credentialsPath.isBlank()
				&& Files.exists(Path.of(credentialsPath));
	}

	/**
	 * Reads a cell range from a Google Spreadsheet.
	 *
	 * @param spreadsheetId the spreadsheet ID
	 * @param range         the A1 notation range (e.g. "Sheet1!A1:E10")
	 * @return list of rows, each row being a list of cell values
	 */
	public List<List<Object>> readRange(String spreadsheetId, String range) throws IOException {
		var client = getSheetsClient();
		ValueRange response = client.spreadsheets().values()
				.get(spreadsheetId, range)
				.execute();
		List<List<Object>> values = response.getValues();
		return values != null ? values : List.of();
	}

	/**
	 * Reads a cell range from a specific sheet in a Google Spreadsheet.
	 *
	 * @param spreadsheetId the spreadsheet ID
	 * @param sheetName     the sheet name (e.g. "Race 1")
	 * @param range         the A1 notation range (e.g. "A:H")
	 * @return list of rows, each row being a list of cell values
	 */
	public List<List<Object>> readRangeFromSheet(String spreadsheetId, String sheetName, String range) throws IOException {
		String fullRange = sheetName + "!" + range;
		return readRange(spreadsheetId, fullRange);
	}

	/**
	 * Lists all sheet names in a spreadsheet.
	 *
	 * @param spreadsheetId the spreadsheet ID
	 * @return list of sheet names in order
	 */
	public List<String> getSheetNames(String spreadsheetId) throws IOException {
		var client = getSheetsClient();
		var spreadsheet = client.spreadsheets().get(spreadsheetId).execute();
		return spreadsheet.getSheets().stream()
				.map(sheet -> sheet.getProperties().getTitle())
				.collect(Collectors.toList());
	}

	/**
	 * Filters sheet names to return race sheets for import.
	 * Defensive approach: tries to find sheets with "Race" in name first,
	 * but if none found, falls back to using any sheet (excluding "Overall").
	 * This ensures single-tab sheets work regardless of tab naming.
	 *
	 * @param sheetNames list of all sheet names
	 * @return filtered list of race sheet names for import
	 */
	public List<String> filterRaceSheets(List<String> sheetNames) {
		// First try: find sheets with "Race" in the name (multi-race scenario)
		var raceSheets = sheetNames.stream()
				.filter(name -> name.toLowerCase().contains("race") && !name.toLowerCase().contains("overall"))
				.collect(Collectors.toList());

		// Fallback: if no "Race" sheets found, use any sheet except "Overall"
		// This handles single-tab sheets with arbitrary names
		if (raceSheets.isEmpty()) {
			raceSheets = sheetNames.stream()
					.filter(name -> !name.toLowerCase().contains("overall"))
					.collect(Collectors.toList());
		}

		return raceSheets;
	}

	/**
	 * Extracts the spreadsheet ID from a Google Sheets URL or returns the input
	 * if it already looks like a bare spreadsheet ID.
	 *
	 * @param url a Google Sheets URL or bare spreadsheet ID
	 * @return the extracted spreadsheet ID
	 * @throws IllegalArgumentException if the URL format is not recognized
	 */
	public String extractSpreadsheetId(String url) {
		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("URL must not be null or blank");
		}

		var trimmed = url.trim();

		// Try URL pattern first
		var matcher = SPREADSHEET_URL_PATTERN.matcher(trimmed);
		if (matcher.find()) {
			return matcher.group(1);
		}

		// Check if it's a bare spreadsheet ID
		if (SPREADSHEET_ID_PATTERN.matcher(trimmed).matches()) {
			return trimmed;
		}

		throw new IllegalArgumentException("Invalid Google Sheets URL or ID: " + url);
	}

	private synchronized Sheets getSheetsClient() throws IOException {
		if (sheetsClient == null) {
			if (!isAvailable()) {
				throw new IllegalStateException(
						"Google Sheets credentials not configured or file not found");
			}
			try (var credentialsStream = new FileInputStream(credentialsPath)) {
				GoogleCredentials credentials = GoogleCredentials
						.fromStream(credentialsStream)
						.createScoped(SheetsScopes.SPREADSHEETS_READONLY);

				sheetsClient = new Sheets.Builder(
						GoogleNetHttpTransport.newTrustedTransport(),
						GsonFactory.getDefaultInstance(),
						new HttpCredentialsAdapter(credentials))
						.setApplicationName(APPLICATION_NAME)
						.build();

				log.info("Google Sheets API client initialized");
			} catch (GeneralSecurityException e) {
				throw new IOException("Failed to initialize Google Sheets API client", e);
			}
		}
		return sheetsClient;
	}
}
