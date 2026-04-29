package org.ctc.dataimport;

import org.ctc.dataimport.DriverSheetImportService.DriverSheetImportPreview;
import org.ctc.dataimport.DriverSheetImportService.TabPreview;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Phase 59 hotfix regression IT: proves that {@link DriverSheetImportService#preview(String)}
 * does NOT trigger {@link org.springframework.transaction.UnexpectedRollbackException}
 * when an ambiguous legacy tab causes {@link org.ctc.domain.service.SeasonManagementService#findUnique(int)}
 * to throw {@link org.ctc.domain.exception.BusinessRuleException}.
 *
 * <p>Test-shape note: this class deliberately does <strong>not</strong> carry a class-level
 * {@code @Transactional} annotation. The sibling {@code DriverSheetImportServiceIT} does;
 * that masking auto-rollback is exactly why the production bug went uncaught in CI.
 * Each test here commits its own real transaction at the Spring AOP boundary, so a
 * rollback-only-poisoned outer transaction surfaces as it would in production. Manual
 * cleanup happens in {@link #cleanupExtraSeasons()}.
 */
@SpringBootTest
@ActiveProfiles("dev")
class DriverSheetImportServiceTransactionIT {

    private static final String SHEET_URL = "https://docs.google.com/spreadsheets/d/tx-it-sheet-id";
    private static final String SPREADSHEET_ID = "tx-it-sheet-id";
    private static final int FRESH_YEAR = 2099;

    @MockitoBean
    private GoogleSheetsService googleSheetsService;

    @Autowired private DriverSheetImportService driverSheetImportService;
    @Autowired private SeasonRepository seasonRepository;

    private final List<UUID> createdSeasonIds = new ArrayList<>();

    @BeforeEach
    void seedTwoSeasonsForFreshYear() {
        // Borrow scoring config from any existing season (NOT NULL FKs)
        var template = seasonRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Dev seed empty — cannot borrow RaceScoring/MatchScoring"));

        createdSeasonIds.add(persistFreshSeason("Phase59-TxIT-Extra-A", FRESH_YEAR, 1, template));
        createdSeasonIds.add(persistFreshSeason("Phase59-TxIT-Extra-B", FRESH_YEAR, 2, template));
    }

    @AfterEach
    void cleanupExtraSeasons() {
        for (UUID id : createdSeasonIds) {
            try {
                seasonRepository.deleteById(id);
            } catch (Exception ignore) {
                // best-effort cleanup; a failed test must not block subsequent tests
            }
        }
        createdSeasonIds.clear();
    }

    @Test
    void givenAmbiguousLegacyTab_whenPreview_thenReturnsTabWithAmbiguousReasonWithoutRollbackException() throws IOException {
        // given — two seasons with year=FRESH_YEAR exist; a legacy '<year>' tab
        //         will hit findUnique(FRESH_YEAR) and trigger BusinessRuleException
        String tabName = String.valueOf(FRESH_YEAR);
        when(googleSheetsService.extractSpreadsheetId(SHEET_URL)).thenReturn(SPREADSHEET_ID);
        when(googleSheetsService.getSheetNames(SPREADSHEET_ID)).thenReturn(List.of(tabName));
        // header-only sheet — bug fires on findUnique regardless of data rows
        lenient().when(googleSheetsService.readRangeFromSheet(SPREADSHEET_ID, tabName, "A:C"))
                .thenReturn(List.of(List.of("PSN ID", "Name", "Team")));

        // when / then — preview must complete without UnexpectedRollbackException
        //               at the Spring AOP commit boundary (this is the regression check)
        var ref = new java.util.concurrent.atomic.AtomicReference<DriverSheetImportPreview>();
        assertThatNoException().isThrownBy(() -> ref.set(driverSheetImportService.preview(SHEET_URL)));

        DriverSheetImportPreview preview = ref.get();
        assertThat(preview).isNotNull();
        assertThat(preview.tabPreviews()).hasSize(1);

        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.tabName()).isEqualTo(tabName);
        assertThat(tab.suggestedSeasonId()).isNull();
        assertThat(tab.ambiguousReason())
                .as("BusinessRuleException must surface as ambiguousReason, NOT as a 5xx")
                .isNotNull()
                .startsWith("Multiple seasons exist for year " + FRESH_YEAR);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private UUID persistFreshSeason(String name, int year, int number, Season template) {
        var s = new Season();
        s.setName(name);
        s.setYear(year);
        s.setNumber(number);
        s.setActive(false);
        s.setFormat(SeasonFormat.LEAGUE);
        s.setLegs(1);
        s.setRaceScoring(template.getRaceScoring());
        s.setMatchScoring(template.getMatchScoring());
        return seasonRepository.save(s).getId();
    }
}
