package org.ctc.dataimport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

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

    private final GoogleSheetsService googleSheetsService;
    private final DriverMatchingService driverMatchingService;
    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final SeasonDriverRepository seasonDriverRepository;

    /**
     * Builds a preview of all year-numbered tabs in the given Google Sheet.
     * Categorizes every data row into one of six buckets per D-12 precedence.
     *
     * @param sheetUrl Google Sheets URL or bare spreadsheet ID
     * @return typed preview — no DB writes performed
     * @throws IOException if Google Sheets API call fails
     */
    public DriverSheetImportPreview preview(String sheetUrl) throws IOException {
        throw new UnsupportedOperationException("implemented in Task 3");
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
