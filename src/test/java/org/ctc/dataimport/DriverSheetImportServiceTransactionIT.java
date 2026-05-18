package org.ctc.dataimport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.ctc.dataimport.DriverSheetImportService.DriverSheetImportPreview;
import org.ctc.dataimport.DriverSheetImportService.TabPreview;
import org.ctc.domain.model.MatchScoring;
import org.ctc.domain.model.RaceScoring;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.MatchScoringRepository;
import org.ctc.domain.repository.RaceScoringRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
@Tag("integration")
class DriverSheetImportServiceTransactionIT {

    private static final String SHEET_URL = "https://docs.google.com/spreadsheets/d/tx-it-sheet-id";
    private static final String SPREADSHEET_ID = "tx-it-sheet-id";
    private static final int FRESH_YEAR = 2099;

    @MockitoBean
    private GoogleSheetsService googleSheetsService;

    @Autowired private DriverSheetImportService driverSheetImportService;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private RaceScoringRepository raceScoringRepository;
    @Autowired private MatchScoringRepository matchScoringRepository;

    private final List<UUID> createdSeasonIds = new ArrayList<>();
    private UUID createdRaceScoringId;
    private UUID createdMatchScoringId;

    @BeforeEach
    void seedTwoSeasonsForFreshYear() {
        // Pitfall guard (WR-01): H2 in-memory persists across test contexts (DB_CLOSE_DELAY=-1).
        // A previous failed cleanup must not leave 2099 rows behind, otherwise findUnique would
        // see > 2 hits and the regression assertion would silently change shape.
        var leftover = seasonRepository.findByYear(FRESH_YEAR);
        if (!leftover.isEmpty()) {
            leftover.forEach(s -> seasonRepository.deleteById(s.getId()));
        }

        // WR-03: create OWN scoring config (NOT NULL FKs on Season) instead of borrowing
        // from the dev seed via seasonRepository.findAll().findFirst(). This decouples the
        // regression IT from DevDataSeeder evolution (seed-data drift, ordering changes,
        // future scoring-bean refactors) without forcing a new test-profile infrastructure.
        var raceScoring = raceScoringRepository.save(
                new RaceScoring("Phase59-TxIT", "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2));
        var matchScoring = matchScoringRepository.save(
                new MatchScoring("Phase59-TxIT", 3, 1, 0));
        createdRaceScoringId = raceScoring.getId();
        createdMatchScoringId = matchScoring.getId();

        createdSeasonIds.add(persistFreshSeason("Phase59-TxIT-Extra-A", FRESH_YEAR, 1, raceScoring, matchScoring));
        createdSeasonIds.add(persistFreshSeason("Phase59-TxIT-Extra-B", FRESH_YEAR, 2, raceScoring, matchScoring));
    }

    @AfterEach
    void cleanupExtraSeasons() {
        for (UUID id : createdSeasonIds) {
            try {
                seasonRepository.deleteById(id);
            } catch (Exception ex) {
                // Best-effort cleanup; a failed test must not block subsequent tests.
                // WR-02: promote diagnostic visibility — silent ignore once masked the original bug too.
                System.err.println("Phase59-TxIT cleanup failed for season " + id + ": "
                        + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
        createdSeasonIds.clear();

        // WR-03: also clean up the test-owned scoring rows so repeated runs do not pile up.
        if (createdMatchScoringId != null) {
            try {
                matchScoringRepository.deleteById(createdMatchScoringId);
            } catch (Exception ex) {
                System.err.println("Phase59-TxIT cleanup failed for matchScoring " + createdMatchScoringId
                        + ": " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            createdMatchScoringId = null;
        }
        if (createdRaceScoringId != null) {
            try {
                raceScoringRepository.deleteById(createdRaceScoringId);
            } catch (Exception ex) {
                System.err.println("Phase59-TxIT cleanup failed for raceScoring " + createdRaceScoringId
                        + ": " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            createdRaceScoringId = null;
        }
    }

    @Test
    void givenTestClass_whenCheckingClassAnnotations_thenIsNotTransactional() {
        // WR-04: self-protection invariant. The entire CI-protection value of this IT
        // depends on the absence of @Transactional at the class level — any rollback-only
        // poisoning at the AOP boundary must surface, not be auto-rolled-back. A future
        // contributor encountering "flaky cleanup" might reach for @Transactional as the
        // standard fix; this test fails loudly if that ever happens. See class-level Javadoc.
        assertThat(DriverSheetImportServiceTransactionIT.class
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .as("This IT must NOT carry @Transactional — see class-level Javadoc")
                .isFalse();
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

    // Helpers

    private UUID persistFreshSeason(String name, int year, int number,
                                    RaceScoring raceScoring, MatchScoring matchScoring) {
        var s = new Season();
        s.setName(name);
        s.setYear(year);
        s.setNumber(number);
        s.setActive(false);
        return seasonRepository.save(s).getId();
    }
}
