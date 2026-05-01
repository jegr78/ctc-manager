package org.ctc.dataimport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.dataimport.DriverMatchingService.MatchResult;
import org.ctc.dataimport.DriverMatchingService.MatchType;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.service.SeasonManagementService;
import org.ctc.domain.service.SeasonPhaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Preview service for bulk driver import from Google Sheets.
 * Given a sheet URL, fetches year-numbered tabs, categorizes every data row
 * into one of six buckets, and returns a typed DriverSheetImportPreview.
 * No DB writes — idempotent and safe to call multiple times.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverSheetImportService {

    // Accept legacy ^\d{4}$ AND new ^\d{4}_S\d+$ patterns.
    // group(1) = year (always present); group(2) = seasonNum (null for legacy form)
    private static final Pattern YEAR_TAB_PATTERN = Pattern.compile("^(\\d{4})(?:_S(\\d+))?$");

    private final GoogleSheetsService googleSheetsService;
    private final DriverMatchingService driverMatchingService;
    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final DriverRepository driverRepository;

    // Group-resolution dependencies
    private final SeasonManagementService seasonManagementService;
    private final SeasonPhaseService seasonPhaseService;
    private final PhaseTeamRepository phaseTeamRepository;

    /**
     * Builds a preview of all year-numbered tabs in the given Google Sheet.
     * Only tabs whose name matches {@code ^(\d{4})(?:_S(\d+))?$} are included.
     * Every data row (after the header) is categorized into exactly one of
     * six buckets via the priority order: BLANK_PSN → BLANK_TEAM → UNKNOWN_TEAM
     * → DUPLICATE_IN_TAB → FUZZY_SUGGESTION → EXACT/NEW_ASSIGNMENT/UNCHANGED → NEW_DRIVER.
     *
     * @param sheetUrl Google Sheets URL or bare spreadsheet ID
     * @return typed preview — no DB writes performed
     * @throws IOException if Google Sheets API call fails
     */
    @Transactional(readOnly = true)
    public DriverSheetImportPreview preview(String sheetUrl) throws IOException {
        String spreadsheetId = googleSheetsService.extractSpreadsheetId(sheetUrl);
        log.info("Building driver sheet import preview for spreadsheet {}", spreadsheetId);

        List<String> allTabs = googleSheetsService.getSheetNames(spreadsheetId);
        List<String> yearTabs = allTabs.stream()
                .filter(name -> YEAR_TAB_PATTERN.matcher(name).matches())
                // Sort by year extracted from regex group(1); legacy 4-digit names still sort correctly.
                .sorted(Comparator.comparingInt(name -> {
                    var m = YEAR_TAB_PATTERN.matcher(name);
                    m.matches();
                    return Integer.parseInt(m.group(1));
                }))
                .toList();

        log.debug("Found {} year-numbered tabs: {}", yearTabs.size(), yearTabs);

        List<TabPreview> tabPreviews = new ArrayList<>();
        for (String tabName : yearTabs) {
            tabPreviews.add(buildTabPreview(spreadsheetId, tabName));
        }

        return new DriverSheetImportPreview(tabPreviews);
    }

    /**
     * Executes a transactional bulk driver import from the given Google Sheet.
     * Re-fetches the preview inside the transaction, then walks all tabs and
     * rows, applying skip/accept decisions from {@code allParams}.
     *
     * @param sheetUrl  Google Sheets URL or bare spreadsheet ID
     * @param allParams form parameters from the execute POST (seasonId_&lt;year&gt;,
     *                  skip_&lt;psnId&gt;_&lt;year&gt;, accept_&lt;psnId&gt;_&lt;year&gt;=&lt;driverUUID&gt;)
     * @return accumulated result counters
     */
    @Transactional
    public ExecuteResult execute(String sheetUrl, Map<String, String> allParams) {
        log.info("Executing driver sheet import: sheetUrl={}", sheetUrl);
        if (allParams == null) allParams = Map.of();
        DriverSheetImportPreview fullPreview;
        try {
            fullPreview = this.preview(sheetUrl);
        } catch (IOException e) {
            throw new IllegalStateException("Sheet read failed: " + e.getMessage(), e);
        }

        ExecuteResult result = new ExecuteResult();
        Map<String, Driver> crossTabCreatedDrivers = new HashMap<>();

        for (TabPreview tab : fullPreview.tabPreviews()) {
            String seasonIdStr = allParams.get("seasonId_" + tab.year());
            if (seasonIdStr == null || seasonIdStr.isBlank()) {
                result.addSkippedTab(tab.year());
                continue;
            }
            Season season = seasonRepository
                    .findById(UUID.fromString(seasonIdStr))
                    .orElseThrow(() -> new IllegalArgumentException("Season not found: " + seasonIdStr));

            // NEW_DRIVER rows
            for (NewDriverRow row : tab.newDrivers()) {
                Driver driver = crossTabCreatedDrivers.computeIfAbsent(row.psnId(), psnId -> {
                    Driver d = new Driver(psnId, psnId);
                    d.setActive(true);
                    Driver saved = driverRepository.save(d);
                    result.incrementNewDrivers();
                    return saved;
                });
                Team team = teamRepository.findByShortName(row.teamShortName())
                        .orElseThrow(() -> new IllegalArgumentException("Team not found: " + row.teamShortName()));
                seasonDriverRepository.save(new SeasonDriver(season, driver, team));
                result.incrementNewAssignments();
            }

            // NEW_ASSIGNMENT rows
            for (NewAssignmentRow row : tab.newAssignments()) {
                Driver driver = crossTabCreatedDrivers.computeIfAbsent(row.psnId(),
                        psnId -> driverRepository.findById(row.existingDriverId())
                                .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + row.existingDriverId())));
                Team team = teamRepository.findByShortName(row.teamShortName())
                        .orElseThrow(() -> new IllegalArgumentException("Team not found: " + row.teamShortName()));
                boolean alreadyAssigned = seasonDriverRepository
                        .findBySeasonIdAndDriverId(season.getId(), driver.getId())
                        .isPresent();
                if (!alreadyAssigned) {
                    seasonDriverRepository.save(new SeasonDriver(season, driver, team));
                    result.incrementNewAssignments();
                }
            }

            // CONFLICT rows
            for (ConflictRow row : tab.conflicts()) {
                String skipKey = "skip_" + row.psnId() + "_" + tab.year();
                if ("on".equals(allParams.get(skipKey))) {
                    result.incrementConflictsSkipped();
                } else {
                    SeasonDriver sd = seasonDriverRepository
                            .findById(row.existingSeasonDriverId())
                            .orElseThrow(() -> new IllegalArgumentException("SeasonDriver not found: " + row.existingSeasonDriverId()));
                    Team newTeam = teamRepository.findByShortName(row.sheetTeamShortName())
                            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + row.sheetTeamShortName()));
                    sd.setTeam(newTeam);
                    seasonDriverRepository.save(sd);
                    result.incrementConflictsOverwritten();
                }
            }

            // FUZZY_SUGGESTION rows
            for (FuzzySuggestionRow row : tab.fuzzySuggestions()) {
                String acceptKey = "accept_" + row.psnId() + "_" + tab.year();
                String acceptValue = allParams.get(acceptKey);
                Driver driver;
                if (acceptValue != null && !acceptValue.isBlank()) {
                    UUID suggestedDriverId = UUID.fromString(acceptValue);
                    // Use a tab-scoped cache key for the accept path so that different year-tabs
                    // can independently accept different drivers for the same sheet PSN.
                    driver = crossTabCreatedDrivers.computeIfAbsent(row.psnId() + "_accept_" + tab.year(),
                            ignored -> driverRepository.findById(suggestedDriverId)
                                    .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + suggestedDriverId)));
                } else {
                    driver = crossTabCreatedDrivers.computeIfAbsent(row.psnId(), psnId -> {
                        Driver d = new Driver(psnId, psnId);
                        d.setActive(true);
                        Driver saved = driverRepository.save(d);
                        result.incrementNewDrivers();
                        return saved;
                    });
                }
                Team team = teamRepository.findByShortName(row.teamShortName())
                        .orElseThrow(() -> new IllegalArgumentException("Team not found: " + row.teamShortName()));
                boolean alreadyAssigned = seasonDriverRepository
                        .findBySeasonIdAndDriverId(season.getId(), driver.getId())
                        .isPresent();
                if (!alreadyAssigned) {
                    seasonDriverRepository.save(new SeasonDriver(season, driver, team));
                    result.incrementNewAssignments();
                }
            }

            // UNCHANGED (no DB write)
            result.addUnchanged(tab.unchanged().size());

            // ERRORS (never imported, UX-06)
            result.addErrors(tab.errors().size());

            log.debug("Tab {} processed: {} new drivers, {} new assignments",
                    tab.year(), result.getNewDriversCount(), result.getNewAssignmentsCount());
        }

        return result;
    }

    private TabPreview buildTabPreview(String spreadsheetId, String tabName) throws IOException {
        // Parse tab name via union pattern; group(2) is null for legacy form.
        var matcher = YEAR_TAB_PATTERN.matcher(tabName);
        matcher.matches(); // upstream filter already proved this matches
        int year = Integer.parseInt(matcher.group(1));
        Integer number = matcher.group(2) == null ? null : Integer.parseInt(matcher.group(2));

        // Resolve season via SeasonManagementService.findUnique.
        UUID suggestedSeasonId;
        String ambiguousReason;
        try {
            Optional<Season> resolved = (number != null)
                    ? seasonManagementService.findUnique(year, number)
                    : seasonManagementService.findUnique(year);
            if (resolved.isPresent()) {
                suggestedSeasonId = resolved.get().getId();
                ambiguousReason = null;
            } else {
                suggestedSeasonId = null;
                ambiguousReason = (number != null)
                        ? "No season found for (" + year + ", " + number + ")"
                        : "No season found for year " + year;
            }
        } catch (BusinessRuleException ex) {
            // Multi-hit → surface as ambiguousReason (NOT a 5xx).
            suggestedSeasonId = null;
            ambiguousReason = ex.getMessage();
        }

        // Resolve REGULAR phase ONCE per tab, cache locally.
        SeasonPhase regularPhase = null;
        if (suggestedSeasonId != null) {
            try {
                regularPhase = seasonPhaseService.findRegularPhase(suggestedSeasonId);
            } catch (EntityNotFoundException ex) {
                log.debug("No REGULAR phase for season {}; group resolution disabled", suggestedSeasonId);
            }
        }

        List<List<Object>> rows = googleSheetsService.readRangeFromSheet(spreadsheetId, tabName, "A:C");

        List<NewDriverRow> newDrivers = new ArrayList<>();
        List<NewAssignmentRow> newAssignments = new ArrayList<>();
        List<ConflictRow> conflicts = new ArrayList<>();
        List<FuzzySuggestionRow> fuzzySuggestions = new ArrayList<>();
        List<UnchangedRow> unchanged = new ArrayList<>();
        List<ErrorRow> errors = new ArrayList<>();
        List<TabWarning> warnings = new ArrayList<>();
        Set<String> warnedTeams = new LinkedHashSet<>();   // dedupe key per team short name

        // Track PSNs seen in this tab for DUPLICATE_IN_TAB detection.
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

            // Step 1: Blank PSN
            if (rawPsnId.isBlank()) {
                errors.add(new ErrorRow(rawPsnId, rawTeamCode, ErrorReason.BLANK_PSN_ID));
                continue;
            }

            // Step 2: Blank team code
            if (rawTeamCode.isBlank()) {
                errors.add(new ErrorRow(rawPsnId, rawTeamCode, ErrorReason.BLANK_TEAM_CODE));
                continue;
            }

            // Step 3: Unknown team short code
            Optional<Team> teamOpt = teamRepository.findByShortName(rawTeamCode);
            if (teamOpt.isEmpty()) {
                errors.add(new ErrorRow(rawPsnId, rawTeamCode, ErrorReason.UNKNOWN_TEAM_CODE));
                continue;
            }
            Team team = teamOpt.get();

            // Step 4: Duplicate PSN in tab — first occurrence wins.
            // rawPsnId is already trimmed by cellToString.
            if (seenPsnIds.contains(rawPsnId)) {
                errors.add(new ErrorRow(rawPsnId, rawTeamCode, ErrorReason.DUPLICATE_IN_TAB));
                continue;
            }
            seenPsnIds.add(rawPsnId);

            // Resolve group via PhaseTeam(REGULAR) — null when team is not in the REGULAR roster.
            String resolvedGroupName = null;
            if (regularPhase != null) {
                Optional<org.ctc.domain.model.PhaseTeam> ptOpt =
                        phaseTeamRepository.findByPhaseIdAndTeamId(regularPhase.getId(), team.getId());
                if (ptOpt.isPresent() && ptOpt.get().getGroup() != null) {
                    resolvedGroupName = ptOpt.get().getGroup().getName();
                } else if (ptOpt.isEmpty() && warnedTeams.add(rawTeamCode)) {
                    // Emit ONE warning per missing team, not per row.
                    warnings.add(new TabWarning(
                            WarningType.TEAM_NOT_IN_REGULAR_PHASE,
                            rawTeamCode,
                            "Team " + rawTeamCode + " has no PhaseTeam in REGULAR phase of target season"));
                }
            }

            // Steps 5-7: driver matching via DriverMatchingService.
            MatchResult matchResult = driverMatchingService.findDriver(rawPsnId);

            if (matchResult.type() == MatchType.FUZZY) {
                // Step 5: FUZZY_SUGGESTION
                fuzzySuggestions.add(new FuzzySuggestionRow(
                        rawPsnId,
                        matchResult.driver().getId(),
                        matchResult.driver().getPsnId(),
                        matchResult.driver().getNickname(),
                        matchResult.similarity(),
                        rawTeamCode,
                        resolvedGroupName));
            } else if (matchResult.type() == MatchType.EXACT) {
                // Step 6: EXACT — look up SeasonDriver for UNCHANGED / CONFLICT / NEW_ASSIGNMENT.
                var matchedDriver = matchResult.driver();
                if (suggestedSeasonId != null) {
                    Optional<org.ctc.domain.model.SeasonDriver> sdOpt =
                            seasonDriverRepository.findBySeasonIdAndDriverId(suggestedSeasonId, matchedDriver.getId());
                    if (sdOpt.isPresent()) {
                        var sd = sdOpt.get();
                        if (sd.getTeam().getId().equals(team.getId())) {
                            // Same team → UNCHANGED
                            unchanged.add(new UnchangedRow(rawPsnId, matchedDriver.getId(), sd.getId(),
                                    rawTeamCode, resolvedGroupName));
                        } else {
                            // Different team → CONFLICT
                            conflicts.add(new ConflictRow(
                                    rawPsnId,
                                    matchedDriver.getId(),
                                    sd.getId(),
                                    sd.getTeam().getShortName(),
                                    rawTeamCode,
                                    resolvedGroupName));
                        }
                    } else {
                        // No SeasonDriver found → NEW_ASSIGNMENT
                        newAssignments.add(new NewAssignmentRow(rawPsnId, matchedDriver.getId(),
                                rawTeamCode, resolvedGroupName));
                    }
                } else {
                    // No suggested season → treat as NEW_ASSIGNMENT (cannot check SeasonDriver)
                    newAssignments.add(new NewAssignmentRow(rawPsnId, matchedDriver.getId(),
                            rawTeamCode, resolvedGroupName));
                }
            } else {
                // Step 7: MatchType.NONE → NEW_DRIVER
                newDrivers.add(new NewDriverRow(rawPsnId, rawTeamCode, resolvedGroupName));
            }
        }

        log.debug("Tab {}: newDrivers={}, newAssignments={}, conflicts={}, fuzzy={}, unchanged={}, errors={}, warnings={}",
                tabName, newDrivers.size(), newAssignments.size(), conflicts.size(),
                fuzzySuggestions.size(), unchanged.size(), errors.size(), warnings.size());

        return new TabPreview(tabName, year, number, suggestedSeasonId, ambiguousReason, warnings,
                newDrivers, newAssignments, conflicts, fuzzySuggestions, unchanged, errors);
    }

    /**
     * Safely extracts a cell from a row as a trimmed string.
     * Returns empty string if row is too short or cell is null.
     */
    private String cellToString(List<Object> row, int index) {
        if (row == null || index >= row.size()) {
            return "";
        }
        Object cell = row.get(index);
        return cell == null ? "" : cell.toString().trim();
    }

    // ---------------------------------------------------------------------------
    // Public inner types
    // ---------------------------------------------------------------------------

    public record DriverSheetImportPreview(
            List<TabPreview> tabPreviews
    ) {}

    public record TabPreview(
            String tabName,
            int year,
            Integer number,                       // null for legacy ^\d{4}$ tabs
            UUID suggestedSeasonId,
            String ambiguousReason,
            List<TabWarning> warnings,            // tab-level warnings, deduped per team
            List<NewDriverRow> newDrivers,
            List<NewAssignmentRow> newAssignments,
            List<ConflictRow> conflicts,
            List<FuzzySuggestionRow> fuzzySuggestions,
            List<UnchangedRow> unchanged,
            List<ErrorRow> errors
    ) {}

    public record NewDriverRow(String psnId, String teamShortName, String resolvedGroupName) {}

    public record NewAssignmentRow(
            String psnId,
            UUID existingDriverId,
            String teamShortName,
            String resolvedGroupName
    ) {}

    public record ConflictRow(
            String psnId,
            UUID existingDriverId,
            UUID existingSeasonDriverId,
            String existingTeamShortName,
            String sheetTeamShortName,
            String resolvedGroupName
    ) {}

    public record FuzzySuggestionRow(
            String psnId,
            UUID suggestedDriverId,
            String suggestedPsnId,
            String suggestedNickname,
            double similarity,
            String teamShortName,
            String resolvedGroupName
    ) {}

    public record UnchangedRow(
            String psnId,
            UUID existingDriverId,
            UUID existingSeasonDriverId,
            String teamShortName,
            String resolvedGroupName
    ) {}

    public record ErrorRow(
            String psnId,
            String teamCode,
            ErrorReason reason
    ) {}

    // Tab-level warning + enum, sibling style to ErrorRow / ErrorReason.
    public record TabWarning(
            WarningType type,
            String teamShortName,
            String message
    ) {}

    public enum WarningType {
        TEAM_NOT_IN_REGULAR_PHASE("Team has no PhaseTeam in REGULAR phase");

        private final String message;

        WarningType(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

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

    // ---------------------------------------------------------------------------
    // ExecuteResult — mutable accumulator for transactional execute() walk
    // ---------------------------------------------------------------------------

    @lombok.Getter
    public static class ExecuteResult {
        private int newDriversCount;
        private int newAssignmentsCount;
        private int conflictsOverwrittenCount;
        private int conflictsSkippedCount;
        private int unchangedCount;
        private int errorCount;
        private final java.util.List<Integer> skippedTabYears = new java.util.ArrayList<>();

        void incrementNewDrivers()           { newDriversCount++; }
        void incrementNewAssignments()       { newAssignmentsCount++; }
        void incrementConflictsOverwritten() { conflictsOverwrittenCount++; }
        void incrementConflictsSkipped()     { conflictsSkippedCount++; }
        void addUnchanged(int n)             { unchangedCount += n; }
        void addErrors(int n)                { errorCount += n; }
        void addSkippedTab(int year)         { skippedTabYears.add(year); }

        public boolean hasSkippedTabs() { return !skippedTabYears.isEmpty(); }
    }
}
