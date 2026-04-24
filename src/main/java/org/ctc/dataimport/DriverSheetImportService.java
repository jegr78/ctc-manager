package org.ctc.dataimport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.dataimport.DriverMatchingService.MatchResult;
import org.ctc.dataimport.DriverMatchingService.MatchType;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Preview service for bulk driver import from Google Sheets.
 * Given a sheet URL, fetches year-numbered tabs, categorizes every data row
 * into one of six buckets, and returns a typed DriverSheetImportPreview.
 * No DB writes — idempotent and safe to call multiple times (D-06).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverSheetImportService {

    private static final Pattern YEAR_TAB_PATTERN = Pattern.compile("^\\d{4}$");

    private final GoogleSheetsService googleSheetsService;
    private final DriverMatchingService driverMatchingService;
    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final SeasonDriverRepository seasonDriverRepository;

    /**
     * Builds a preview of all year-numbered tabs in the given Google Sheet.
     * Only tabs whose name matches {@code ^\d{4}$} are included.
     * Every data row (after the header) is categorized into exactly one of
     * six buckets per D-12 precedence (first match wins).
     *
     * @param sheetUrl Google Sheets URL or bare spreadsheet ID
     * @return typed preview — no DB writes performed
     * @throws IOException if Google Sheets API call fails
     */
    public DriverSheetImportPreview preview(String sheetUrl) throws IOException {
        String spreadsheetId = googleSheetsService.extractSpreadsheetId(sheetUrl);
        log.info("Building driver sheet import preview for spreadsheet {}", spreadsheetId);

        List<String> allTabs = googleSheetsService.getSheetNames(spreadsheetId);
        List<String> yearTabs = allTabs.stream()
                .filter(name -> YEAR_TAB_PATTERN.matcher(name).matches())
                .sorted(Comparator.comparingInt(Integer::parseInt))
                .toList();

        log.debug("Found {} year-numbered tabs: {}", yearTabs.size(), yearTabs);

        List<TabPreview> tabPreviews = new ArrayList<>();
        for (String tabName : yearTabs) {
            tabPreviews.add(buildTabPreview(spreadsheetId, tabName));
        }

        return new DriverSheetImportPreview(tabPreviews);
    }

    private TabPreview buildTabPreview(String spreadsheetId, String tabName) throws IOException {
        int year = Integer.parseInt(tabName);

        // D-01/D-02: resolve suggestedSeasonId via findByYear
        List<Season> seasons = seasonRepository.findByYear(year);
        UUID suggestedSeasonId;
        String ambiguousReason;
        if (seasons.size() == 1) {
            suggestedSeasonId = seasons.get(0).getId();
            ambiguousReason = null;
        } else if (seasons.isEmpty()) {
            suggestedSeasonId = null;
            ambiguousReason = "No season found for year " + year;
        } else {
            suggestedSeasonId = null;
            ambiguousReason = "Multiple seasons for year " + year;
        }

        List<List<Object>> rows = googleSheetsService.readRangeFromSheet(spreadsheetId, tabName, "A:C");

        List<NewDriverRow> newDrivers = new ArrayList<>();
        List<NewAssignmentRow> newAssignments = new ArrayList<>();
        List<ConflictRow> conflicts = new ArrayList<>();
        List<FuzzySuggestionRow> fuzzySuggestions = new ArrayList<>();
        List<UnchangedRow> unchanged = new ArrayList<>();
        List<ErrorRow> errors = new ArrayList<>();

        // Track PSNs seen in this tab for DUPLICATE_IN_TAB detection (D-11)
        Set<String> seenPsnIds = new LinkedHashSet<>();

        // Skip header row (row index 0); process data rows from index 1
        boolean headerSkipped = false;
        for (List<Object> row : rows) {
            if (!headerSkipped) {
                headerSkipped = true;
                continue;
            }

            String rawPsnId = cellToString(row, 0);
            String rawTeamCode = cellToString(row, 2);

            // D-12 step 1: Blank PSN
            if (rawPsnId.isBlank()) {
                errors.add(new ErrorRow(rawPsnId, rawTeamCode, ErrorReason.BLANK_PSN_ID));
                continue;
            }

            // D-12 step 2: Blank team code
            if (rawTeamCode.isBlank()) {
                errors.add(new ErrorRow(rawPsnId, rawTeamCode, ErrorReason.BLANK_TEAM_CODE));
                continue;
            }

            // D-12 step 3: Unknown team short code
            Optional<Team> teamOpt = teamRepository.findByShortName(rawTeamCode);
            if (teamOpt.isEmpty()) {
                errors.add(new ErrorRow(rawPsnId, rawTeamCode, ErrorReason.UNKNOWN_TEAM_CODE));
                continue;
            }
            Team team = teamOpt.get();

            // D-12 step 4: Duplicate PSN in tab (D-11 first occurrence wins)
            // rawPsnId is already trimmed by cellToString — no further normalisation needed.
            if (seenPsnIds.contains(rawPsnId)) {
                errors.add(new ErrorRow(rawPsnId, rawTeamCode, ErrorReason.DUPLICATE_IN_TAB));
                continue;
            }
            seenPsnIds.add(rawPsnId);

            // D-12 step 5-7: driver matching via DriverMatchingService
            MatchResult matchResult = driverMatchingService.findDriver(rawPsnId);

            if (matchResult.type() == MatchType.FUZZY) {
                // D-12 step 5: FUZZY_SUGGESTION
                fuzzySuggestions.add(new FuzzySuggestionRow(
                        rawPsnId,
                        matchResult.driver().getId(),
                        matchResult.driver().getPsnId(),
                        matchResult.driver().getNickname(),
                        matchResult.similarity(),
                        rawTeamCode
                ));
            } else if (matchResult.type() == MatchType.EXACT) {
                // D-12 step 6: EXACT — look up SeasonDriver for UNCHANGED / CONFLICT / NEW_ASSIGNMENT
                var matchedDriver = matchResult.driver();
                if (suggestedSeasonId != null) {
                    Optional<org.ctc.domain.model.SeasonDriver> sdOpt =
                            seasonDriverRepository.findBySeasonIdAndDriverId(suggestedSeasonId, matchedDriver.getId());
                    if (sdOpt.isPresent()) {
                        var sd = sdOpt.get();
                        if (sd.getTeam().getId().equals(team.getId())) {
                            // Same team → UNCHANGED
                            unchanged.add(new UnchangedRow(rawPsnId, matchedDriver.getId(), rawTeamCode));
                        } else {
                            // Different team → CONFLICT
                            conflicts.add(new ConflictRow(
                                    rawPsnId,
                                    matchedDriver.getId(),
                                    sd.getId(),
                                    sd.getTeam().getShortName(),
                                    rawTeamCode
                            ));
                        }
                    } else {
                        // No SeasonDriver found → NEW_ASSIGNMENT
                        newAssignments.add(new NewAssignmentRow(rawPsnId, matchedDriver.getId(), rawTeamCode));
                    }
                } else {
                    // No suggested season → treat as NEW_ASSIGNMENT (cannot check SeasonDriver)
                    newAssignments.add(new NewAssignmentRow(rawPsnId, matchedDriver.getId(), rawTeamCode));
                }
            } else {
                // D-12 step 7: MatchType.NONE → NEW_DRIVER
                newDrivers.add(new NewDriverRow(rawPsnId, rawTeamCode));
            }
        }

        log.debug("Tab {}: newDrivers={}, newAssignments={}, conflicts={}, fuzzy={}, unchanged={}, errors={}",
                tabName, newDrivers.size(), newAssignments.size(), conflicts.size(),
                fuzzySuggestions.size(), unchanged.size(), errors.size());

        return new TabPreview(tabName, year, suggestedSeasonId, ambiguousReason,
                newDrivers, newAssignments, conflicts, fuzzySuggestions, unchanged, errors);
    }

    /**
     * Safely extracts a cell from a row as a trimmed string.
     * Returns empty string if row is too short or cell is null (D-12 defensive read).
     */
    private String cellToString(List<Object> row, int index) {
        if (row == null || index >= row.size()) {
            return "";
        }
        Object cell = row.get(index);
        return cell == null ? "" : cell.toString().trim();
    }

    // ---------------------------------------------------------------------------
    // Public inner types (D-04, D-05) — declared verbatim per 54-RESEARCH.md
    // ---------------------------------------------------------------------------

    public record DriverSheetImportPreview(
            List<TabPreview> tabPreviews
    ) {}

    public record TabPreview(
            String tabName,
            int year,
            UUID suggestedSeasonId,
            String ambiguousReason,
            List<NewDriverRow> newDrivers,
            List<NewAssignmentRow> newAssignments,
            List<ConflictRow> conflicts,
            List<FuzzySuggestionRow> fuzzySuggestions,
            List<UnchangedRow> unchanged,
            List<ErrorRow> errors
    ) {}

    public record NewDriverRow(String psnId, String teamShortName) {}

    public record NewAssignmentRow(
            String psnId,
            UUID existingDriverId,
            String teamShortName
    ) {}

    public record ConflictRow(
            String psnId,
            UUID existingDriverId,
            UUID existingSeasonDriverId,
            String existingTeamShortName,
            String sheetTeamShortName
    ) {}

    public record FuzzySuggestionRow(
            String psnId,
            UUID suggestedDriverId,
            String suggestedPsnId,
            String suggestedNickname,
            double similarity,
            String teamShortName
    ) {}

    public record UnchangedRow(
            String psnId,
            UUID existingDriverId,
            String teamShortName
    ) {}

    public record ErrorRow(
            String rawPsnId,
            String rawTeamCode,
            ErrorReason reason
    ) {}

    public enum ErrorReason {
        BLANK_PSN_ID("PSN ID is blank"),
        BLANK_TEAM_CODE("Team short code is blank"),
        UNKNOWN_TEAM_CODE("Team short code not found"),
        DUPLICATE_IN_TAB("PSN already listed earlier in this tab");

        private final String message;

        ErrorReason(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }
}
