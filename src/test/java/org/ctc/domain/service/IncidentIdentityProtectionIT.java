package org.ctc.domain.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.ctc.dataimport.CsvImportService;
import org.ctc.dataimport.exception.ImportRejectedException;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.TimeProgressionMode;
import org.ctc.domain.repository.DriverRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
class IncidentIdentityProtectionIT extends IncidentIntegrationFixture {
    @Autowired RaceService raceService;
    @Autowired MatchService matchService;
    @Autowired MatchdayService matchdayService;
    @Autowired CsvImportService imports;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean DriverRepository drivers;
    final String firstPsn = "Test_INC_A_" + UUID.randomUUID().toString().substring(0, 12);
    final String secondPsn = "Test_INC_B_" + UUID.randomUUID().toString().substring(0, 12);

    @Override
    @AfterEach
    protected void cleanUp() {
        super.cleanUp();
        tx.executeWithoutResult(status -> {
            drivers.findByPsnId(firstPsn).ifPresent(drivers::delete);
            drivers.findByPsnId(secondPsn).ifPresent(drivers::delete);
        });
    }

    @Test
    void reportedRaceDeletionReturnsDomainRejectionAndPreservesHistory() {
        var receipt = submit();
        assertThatThrownBy(() -> raceService.deleteRace(fixture.race().getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("incident");
        assertHistory(receipt.reportId(), 1);
    }

    @Test
    void boundPairingDeletionIsRejectedEvenBeforeFirstReport() {
        assertThatThrownBy(() -> matchService.deleteMatch(fixture.match().getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("incident");
        assertThat(contextRows.existsById(contextId)).isTrue();
        assertThat(matches.existsById(fixture.match().getId())).isTrue();
    }

    @Test
    void boundMatchdayDeletionIsRejectedWithoutLosingItsContext() {
        assertThatThrownBy(() -> matchdayService.deleteMatchday(fixture.matchday().getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("incident");
        assertThat(contextRows.existsById(contextId)).isTrue();
        assertThat(races.existsById(fixture.race().getId())).isTrue();
    }

    @Test
    void swappingBoundParticipantsFailsBeforeChangingThePairing() {
        var result = save(fixture.matchday().getId(), fixture.awayTeam().getId(), fixture.homeTeam().getId());
        assertThat(result.success()).isFalse(); assertThat(result.message()).contains("incident");
        tx.executeWithoutResult(status -> assertThat(matches.findById(fixture.match().getId()).orElseThrow().getHomeTeam().getId())
                .isEqualTo(fixture.homeTeam().getId()));
    }

    @Test
    void movingReportedRaceToAnotherMatchdayFailsBeforeMutation() {
        var receipt = submit();
        var dayId = tx.execute(status -> helper.createMatchday(seasons.findById(fixture.season().getId()).orElseThrow(), "Test incident other day", 2).getId());
        var result = save(dayId, fixture.homeTeam().getId(), fixture.awayTeam().getId());
        assertThat(result.success()).isFalse(); assertThat(result.message()).contains("incident");
        assertHistory(receipt.reportId(), 1);
    }

    @Test
    void resultsOnlyImportReusesTheExactIncidentRaceAndRetainsWithdrawnHistory() throws Exception {
        var receipt = submit(); reports.withdraw(receipt.reportId(), 1, 1, UUID.randomUUID(), actor);
        var preview = preview(false);
        assertThatCode(() -> assertThat(imports.executeImport(preview, Map.of(), Set.of(firstPsn, secondPsn), true).hasErrors()).isFalse())
                .doesNotThrowAnyException();
        assertHistory(receipt.reportId(), 2);
        tx.executeWithoutResult(status -> {
            assertThat(races.findByMatchId(fixture.match().getId())).extracting(Race::getId).containsExactly(fixture.race().getId());
            assertThat(races.findById(fixture.race().getId()).orElseThrow().getResults()).hasSize(2);
            assertThat(reportRows.findById(receipt.reportId()).orElseThrow().getState().name()).isEqualTo("WITHDRAWN");
        });
    }

    @Test
    void ambiguousImportRejectsBeforeCreatingDriversOrReplacingAnyRace() throws Exception {
        var receipt = submit();
        UUID another = tx.execute(status -> {
            var original = races.findById(fixture.race().getId()).orElseThrow();
            return helper.createRace(original.getMatchday(), original.getMatch()).getId();
        });
        var preview = preview(false);
        org.mockito.Mockito.clearInvocations(drivers);
        assertThatThrownBy(() -> imports.executeMultiRaceImport(List.of(preview, preview), Map.of(), Set.of(firstPsn, secondPsn), true))
                .isInstanceOf(ImportRejectedException.class).hasMessageContaining("ambiguous");
        org.mockito.Mockito.verify(drivers, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(drivers.findByPsnId(firstPsn)).isEmpty(); assertThat(drivers.findByPsnId(secondPsn)).isEmpty();
        tx.executeWithoutResult(status -> assertThat(races.findByMatchId(fixture.match().getId())).extracting(Race::getId)
                .containsExactlyInAnyOrder(fixture.race().getId(), another));
        assertHistory(receipt.reportId(), 1);
    }

    @Test
    void ordinaryScheduleAndSetupChangesKeepTheCaseAndConfirmedDeadline() {
        var receipt = submit();
        var result = save(fixture.matchday().getId(), fixture.homeTeam().getId(), fixture.awayTeam().getId());
        assertThat(result.success()).isTrue(); assertHistory(receipt.reportId(), 1);
        tx.executeWithoutResult(status -> assertThat(windowRows.findById(windowId).orElseThrow().getDeadline()).isEqualTo(NOW.plusSeconds(3600)));
    }

    private org.ctc.domain.incidents.IncidentReportReceipt submit() {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        return reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload);
    }

    private RaceService.SaveResult save(UUID day, UUID home, UUID away) {
        return raceService.saveRace(fixture.race().getId(), day, home, away, null, null, LocalDateTime.of(2036, 1, 1, 20, 0),
                12, null, null, null, null, null, TimeProgressionMode.MULTIPLIER, 0, null, null, null, null);
    }

    private CsvImportService.ImportPreview preview(boolean reversed) throws Exception {
        var home = reversed ? fixture.awayTeam() : fixture.homeTeam(); var away = reversed ? fixture.homeTeam() : fixture.awayTeam();
        var csv = "Team,PSN ID,Position,Quali,FL\n%s,%s,1,1,true\n%s,%s,2,2,false\n"
                .formatted(home.getShortName(), firstPsn, away.getShortName(), secondPsn);
        return tx.execute(status -> {
            try {
                return imports.parseAndPreview(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                        new CsvImportService.ImportMetadata(fixture.season().getId(), null, null, null, null, fixture.matchday().getId()));
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        });
    }

    private void assertHistory(UUID id, long version) {
        tx.executeWithoutResult(status -> {
            var report = reportRows.findById(id).orElseThrow();
            assertThat(report.getVersion()).isEqualTo(version); assertThat(report.getRace().getId()).isEqualTo(fixture.race().getId());
            assertThat(report.getOriginalMatchday().getId()).isEqualTo(fixture.matchday().getId());
            assertThat(report.getOriginalHomeTeam().getId()).isEqualTo(fixture.homeTeam().getId());
            assertThat(report.getParty()).isEqualTo(actor.party());
            assertThat(revisionRows.findByReportIdOrderByRevisionNumberAsc(id)).singleElement().satisfies(row -> assertThat(row.getPayload()).isEqualTo(payload));
        });
    }
}
