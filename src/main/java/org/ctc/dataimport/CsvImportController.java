package org.ctc.dataimport;

import java.io.IOException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.dataimport.exception.AuthGoogleApiException;
import org.ctc.dataimport.exception.GoogleApiException;
import org.ctc.dataimport.exception.NotFoundGoogleApiException;
import org.ctc.dataimport.exception.PermissionGoogleApiException;
import org.ctc.dataimport.exception.TransientGoogleApiException;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.ValidationException;
import org.ctc.domain.service.SeasonManagementService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/import")
@RequiredArgsConstructor
public class CsvImportController {

	private final CsvImportService csvImportService;
	private final GoogleSheetsService googleSheetsService;
	private final ScorecardParser scorecardParser;
	private final SeasonManagementService seasonManagementService;

	@GetMapping
	public String showImportForm(Model model) {
		addCommonAttributes(model);
		return "admin/import";
	}

	@PostMapping("/preview")
	public String preview(@RequestParam("file") MultipartFile file,
	                      @RequestParam UUID seasonId,
	                      @RequestParam(required = false) String matchdayLabel,
	                      @RequestParam(required = false) UUID matchdayId,
	                      @RequestParam(required = false) UUID playoffMatchupId,
	                      Model model) {
		try {
			var metadata = new CsvImportService.ImportMetadata(seasonId, matchdayLabel, null, null, playoffMatchupId, matchdayId);
			var preview = csvImportService.parseAndPreview(file.getInputStream(), metadata);

			csvImportService.checkDuplicate(preview);
			model.addAttribute("preview", preview);
			model.addAttribute("metadata", metadata);
			seasonManagementService.findByIdOptional(seasonId).ifPresent(s -> model.addAttribute("seasonDisplayLabel", s.getDisplayLabel()));
			model.addAttribute("source", "csv");
			addMatchdayName(model, metadata);
			addCommonAttributes(model);
			return "admin/import-preview";
		} catch (IOException | IllegalArgumentException | IllegalStateException e) {
			log.error("Error parsing CSV", e);
			addCommonAttributes(model);
			model.addAttribute("errorMessage", "Error reading CSV: " + e.getMessage());
			return "admin/import";
		}
	}

	@PostMapping("/preview-sheet")
	public String previewSheet(@RequestParam String sheetUrl,
	                           @RequestParam UUID seasonId,
	                           @RequestParam(required = false) String matchdayLabel,
	                           @RequestParam(required = false) UUID matchdayId,
	                           @RequestParam(required = false) UUID playoffMatchupId,
	                           Model model) {
		try {
			var spreadsheetId = googleSheetsService.extractSpreadsheetId(sheetUrl);
			var sheetNames = googleSheetsService.getSheetNames(spreadsheetId);
			var raceSheets = googleSheetsService.filterRaceSheets(sheetNames);

			if (raceSheets.isEmpty()) {
				addCommonAttributes(model);
				model.addAttribute("errorMessage", "No usable sheets found in spreadsheet (all sheets appear to be summary/overall sheets)");
				return "admin/import";
			}

			var metadata = new CsvImportService.ImportMetadata(seasonId, matchdayLabel, null, null, playoffMatchupId, matchdayId);

			// Read all race sheet data
			var sheetDataMap = new java.util.HashMap<String, java.util.List<java.util.List<Object>>>();
			for (String raceName : raceSheets) {
				var data = googleSheetsService.readRangeFromSheet(spreadsheetId, raceName, "A:H");
				sheetDataMap.put(raceName, data);
			}

			// Parse multiple races
			var previews = scorecardParser.parseMultipleRaces(sheetDataMap, raceSheets, metadata);

			if (previews.isEmpty()) {
				addCommonAttributes(model);
				model.addAttribute("errorMessage", "Unable to parse any race sheets from the spreadsheet");
				return "admin/import";
			}

			// Check for duplicates in all races
			for (var preview : previews) {
				csvImportService.checkDuplicate(preview);
			}

			model.addAttribute("previews", previews);
			model.addAttribute("raceSheetNames", raceSheets);
			model.addAttribute("isMultiRace", raceSheets.size() > 1);
			model.addAttribute("metadata", metadata);
			seasonManagementService.findByIdOptional(seasonId).ifPresent(s -> model.addAttribute("seasonDisplayLabel", s.getDisplayLabel()));
			model.addAttribute("source", "sheet");
			model.addAttribute("sheetUrl", sheetUrl);
			addMatchdayName(model, metadata);
			addCommonAttributes(model);
			return "admin/import-preview";
		} catch (AuthGoogleApiException e) {
			log.error("Google Sheets authentication failed during CSV import preview-sheet", e);
			addCommonAttributes(model);
			model.addAttribute("errorMessage", "Authentication problem — re-link Google account");
			model.addAttribute("errorCategory", "AUTH");
			return "admin/import";
		} catch (NotFoundGoogleApiException e) {
			log.error("Google Sheet not found during CSV import preview-sheet", e);
			addCommonAttributes(model);
			model.addAttribute("errorMessage", "Sheet not found — check ID");
			model.addAttribute("errorCategory", "NOT_FOUND");
			return "admin/import";
		} catch (PermissionGoogleApiException e) {
			log.error("Permission denied on Google Sheet during CSV import preview-sheet", e);
			addCommonAttributes(model);
			model.addAttribute("errorMessage", "Access denied — share the sheet with the service account");
			model.addAttribute("errorCategory", "PERMISSION");
			return "admin/import";
		} catch (TransientGoogleApiException e) {
			log.warn("Transient Google API failure during CSV import preview-sheet", e);
			addCommonAttributes(model);
			model.addAttribute("errorMessage", "Connection problem — retry");
			model.addAttribute("errorCategory", "TRANSIENT");
			return "admin/import";
		} catch (GoogleApiException e) {
			// Defensive catch on the sealed base — unreachable at runtime (the 4
			// permits above are exhaustive) but required by javac since sealed
			// exhaustiveness on catch blocks is not yet a language feature.
			log.error("Unexpected GoogleApiException subtype during CSV import preview-sheet", e);
			addCommonAttributes(model);
			model.addAttribute("errorMessage", "Connection problem — retry");
			model.addAttribute("errorCategory", "TRANSIENT");
			return "admin/import";
		} catch (IllegalArgumentException | IllegalStateException e) {
			log.error("Error reading Google Sheet", e);
			addCommonAttributes(model);
			model.addAttribute("errorMessage", "Error reading Google Sheet: " + e.getMessage());
			return "admin/import";
		}
	}

	@PostMapping("/execute")
	public String execute(@RequestParam UUID seasonId,
	                      @RequestParam(required = false) String matchdayLabel,
	                      @RequestParam(required = false) UUID matchdayId,
	                      @RequestParam(required = false) UUID playoffMatchupId,
	                      @RequestParam(required = false, defaultValue = "csv") String source,
	                      @RequestParam(required = false) String sheetUrl,
	                      @RequestParam(required = false) MultipartFile file,
	                      @RequestParam(required = false, defaultValue = "false") boolean overwrite,
	                      @RequestParam(required = false) Map<String, String> allParams,
	                      RedirectAttributes redirectAttributes) {
		try {
			var metadata = new CsvImportService.ImportMetadata(seasonId, matchdayLabel, null, null, playoffMatchupId, matchdayId);

			// Re-parse from original source
			var previews = new ArrayList<CsvImportService.ImportPreview>();
			if ("sheet".equals(source) && sheetUrl != null && !sheetUrl.isBlank()) {
				var spreadsheetId = googleSheetsService.extractSpreadsheetId(sheetUrl);
				var sheetNames = googleSheetsService.getSheetNames(spreadsheetId);
				var raceSheets = googleSheetsService.filterRaceSheets(sheetNames);

				if (raceSheets.isEmpty()) {
					redirectAttributes.addFlashAttribute("errorMessage",
							"No race sheets found in spreadsheet");
					return "redirect:/admin/import";
				}

				// Read all race sheet data
				var sheetDataMap = new HashMap<String, List<List<Object>>>();
				for (String raceName : raceSheets) {
					var data = googleSheetsService.readRangeFromSheet(spreadsheetId, raceName, "A:H");
					sheetDataMap.put(raceName, data);
				}

				// Parse multiple races
				previews = new ArrayList<>(scorecardParser.parseMultipleRaces(sheetDataMap, raceSheets, metadata));
			} else {
				if (file == null || file.isEmpty()) {
					redirectAttributes.addFlashAttribute("errorMessage", "No CSV file provided");
					return "redirect:/admin/import";
				}
				var preview = csvImportService.parseAndPreview(file.getInputStream(), metadata);
				previews.add(preview);
			}

			if (previews.isEmpty()) {
				redirectAttributes.addFlashAttribute("errorMessage", "Unable to parse any data from the import source");
				return "redirect:/admin/import";
			}

			// Collect confirmed fuzzy matches and new driver decisions (shared across all races)
			Map<String, UUID> confirmedMatches = new HashMap<>();
			Set<String> createNewDrivers = new HashSet<>();

			if (allParams != null) {
				for (var entry : allParams.entrySet()) {
					if (entry.getKey().startsWith("confirm_")) {
						var psnId = entry.getKey().substring("confirm_".length());
						if ("new".equals(entry.getValue())) {
							createNewDrivers.add(psnId);
						} else {
							confirmedMatches.put(psnId, UUID.fromString(entry.getValue()));
						}
					}
				}
			}

			// Execute import for all races (handles multi-race reuse of matches correctly)
			var cumulativeResult = csvImportService.executeMultiRaceImport(previews, confirmedMatches, createNewDrivers, overwrite);

			if (cumulativeResult.hasErrors()) {
				redirectAttributes.addFlashAttribute("errorMessage",
						"Import with errors: " + String.join(", ", cumulativeResult.getErrors()));
			} else {
				var msg = "Import successful: " + cumulativeResult.getImportedRaces().size() + " races, " +
						cumulativeResult.getNewDriversCreated() + " new drivers";
				if (cumulativeResult.getLineupCount() > 0) {
					msg += ", " + cumulativeResult.getLineupCount() + " lineup entries";
				}
				redirectAttributes.addFlashAttribute("successMessage", msg);
			}
		} catch (AuthGoogleApiException e) {
			log.error("Google Sheets authentication failed during CSV import execute", e);
			redirectAttributes.addFlashAttribute("errorMessage", "Authentication problem — re-link Google account");
			redirectAttributes.addFlashAttribute("errorCategory", "AUTH");
		} catch (NotFoundGoogleApiException e) {
			log.error("Google Sheet not found during CSV import execute", e);
			redirectAttributes.addFlashAttribute("errorMessage", "Sheet not found — check ID");
			redirectAttributes.addFlashAttribute("errorCategory", "NOT_FOUND");
		} catch (PermissionGoogleApiException e) {
			log.error("Permission denied on Google Sheet during CSV import execute", e);
			redirectAttributes.addFlashAttribute("errorMessage", "Access denied — share the sheet with the service account");
			redirectAttributes.addFlashAttribute("errorCategory", "PERMISSION");
		} catch (TransientGoogleApiException e) {
			log.warn("Transient Google API failure during CSV import execute", e);
			redirectAttributes.addFlashAttribute("errorMessage", "Connection problem — retry");
			redirectAttributes.addFlashAttribute("errorCategory", "TRANSIENT");
		} catch (GoogleApiException e) {
			// Defensive catch on the sealed base — unreachable at runtime (the 4
			// permits above are exhaustive) but required by javac.
			log.error("Unexpected GoogleApiException subtype during CSV import execute", e);
			redirectAttributes.addFlashAttribute("errorMessage", "Connection problem — retry");
			redirectAttributes.addFlashAttribute("errorCategory", "TRANSIENT");
		} catch (BusinessRuleException | ValidationException | IllegalArgumentException e) {
			log.error("Error executing CSV import", e);
			redirectAttributes.addFlashAttribute("errorMessage", "Import failed: " + e.getMessage());
		} catch (DataIntegrityViolationException e) {
			log.error("CSV import hit DB constraint — transaction rolled back, no rows inserted", e);
			redirectAttributes.addFlashAttribute("errorMessage",
					"Import failed due to a database constraint. Nothing was imported. See server logs for details.");
		} catch (IllegalStateException | IOException | DataAccessException e) {
			log.error("Error executing CSV import", e);
			redirectAttributes.addFlashAttribute("errorMessage", "Import failed due to an internal error. See server logs for details.");
		}
		return "redirect:/admin/import";
	}

	private void addMatchdayName(Model model, CsvImportService.ImportMetadata metadata) {
		if (metadata.hasMatchdayId()) {
			csvImportService.getMatchdayLabel(metadata.matchdayId())
					.ifPresent(label -> model.addAttribute("matchdayName", label));
		}
	}

	private void addCommonAttributes(Model model) {
		model.addAttribute("seasons", csvImportService.getAllSeasons());
		model.addAttribute("sheetsAvailable", googleSheetsService.isAvailable());
		model.addAttribute("playoffMatchups", csvImportService.getPlayoffMatchups());
	}
}
