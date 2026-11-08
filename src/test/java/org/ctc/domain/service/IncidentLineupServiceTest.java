package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.util.stream.IntStream;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceLineupRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentLineupServiceTest {
    final IncidentUnitFixture fixture = new IncidentUnitFixture();
    final RaceLineupRepository rows = mock(RaceLineupRepository.class);
    final IncidentContextService contexts = mock(IncidentContextService.class);
    final IncidentReportAccess access = mock(IncidentReportAccess.class);
    final ScoreWriteLockService locks = mock(ScoreWriteLockService.class);
    final EntityManager entities = mock(EntityManager.class);
    final IncidentLineupService service = new IncidentLineupService(rows, contexts, Optional.of(access), locks, entities);

    @Test
    void givenExactRaceGuestsAndForeignTeam_whenLoading_thenOfferOnlyBothParticipantsAfterAuthorization() {
        var home = row("Test home", fixture.home); home.setGuest(true);
        var away = row("Test away", fixture.away);
        var foreign = row("Test foreign", team("Test foreign"));
        var actor = new AuthorizedIncidentActor(fixture.context.getId(), IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL,
                "400000000000000004", fixture.party());
        when(contexts.get(fixture.context.getId())).thenReturn(fixture.context);
        when(contexts.resolve(fixture.reference())).thenReturn(fixture.target());
        when(rows.findByRaceId(fixture.race.getId())).thenReturn(List.of(home, away, foreign, home));
        var result = service.load(actor, fixture.race.getId());
        assertThat(result.drivers()).extracting(IncidentDriverChoices.DriverChoice::label).containsExactly("Test away · TIA", "Test home · TIH");
        verify(access).requireCurrent(actor, fixture.context, null);
    }

    @Test
    void givenOrganizationParticipant_whenItsSubteamHasALineup_thenKeepExactAssignedTeamInSnapshot() {
        var sub = team("Test sub"); sub.setParentTeam(fixture.home);
        var choice = IncidentLineupService.snapshot(fixture.race, List.of(row("Test guest", sub))).drivers().getFirst();
        assertThat(choice.home()).isTrue(); assertThat(choice.teamId()).isEqualTo(sub.getId());
        assertThat(choice.label()).isEqualTo("Test guest · Test sub");
    }

    @Test
    void givenSubteamParticipant_whenSiblingAssigned_thenExcludeSibling() {
        var parent = team("Test parent"); fixture.home.setParentTeam(parent);
        var sibling = team("Test sibling"); sibling.setParentTeam(parent);
        assertThat(IncidentLineupService.snapshot(fixture.race, List.of(row("Test own", fixture.home), row("Test sibling", sibling))).drivers()).hasSize(1);
    }

    @Test
    void givenDriverOnBothTeams_whenPreparingChoices_thenRejectAmbiguousAssignment() {
        var home = row("Test duplicate", fixture.home);
        var away = new RaceLineup(fixture.race, home.getDriver(), fixture.away);
        assertThatThrownBy(() -> IncidentLineupService.snapshot(fixture.race, List.of(home, away))).isInstanceOf(IncidentLineupException.class);
    }

    @Test
    void givenEmptyOrForeignRaceLineup_whenPreparingChoices_thenExplainMissingOrUnavailableLineup() {
        assertThatThrownBy(() -> IncidentLineupService.snapshot(fixture.race, List.of())).hasMessageContaining("No race lineup");
        var other = row("Test other", fixture.home); other.setRace(new IncidentUnitFixture().race);
        assertThatThrownBy(() -> IncidentLineupService.snapshot(fixture.race, List.of(other))).hasMessageContaining("unavailable");
    }

    @Test
    void givenLineupOrderOrIdentityChanges_whenFingerprinting_thenIgnoreOrderButDetectNameTeamAndRaceChanges() {
        var home = row("Test home", fixture.home); var away = row("Test away", fixture.away);
        var first = IncidentLineupService.snapshot(fixture.race, List.of(home, away));
        assertThat(IncidentLineupService.snapshot(fixture.race, List.of(away, home)).fingerprint()).isEqualTo(first.fingerprint());
        home.getDriver().setPsnId("Test renamed");
        assertThat(IncidentLineupService.snapshot(fixture.race, List.of(home, away)).fingerprint()).isNotEqualTo(first.fingerprint());
        home.getDriver().setPsnId("Test home"); home.setTeam(fixture.away);
        assertThat(IncidentLineupService.snapshot(fixture.race, List.of(home, away)).fingerprint()).isNotEqualTo(first.fingerprint());
        var other = new IncidentUnitFixture(); home.setRace(other.race); home.setTeam(other.home);
        assertThat(IncidentLineupService.snapshot(other.race, List.of(home)).fingerprint()).isNotEqualTo(first.fingerprint());
    }

    @Test
    void givenCurrentLockedLineup_whenResolvingSelection_thenSnapshotCanonicalNamesAndPreserveOtherFields() {
        var home = row("Test home", fixture.home); var away = row("Test away", fixture.away);
        when(rows.findByRaceIdForIncidentUpdate(fixture.race.getId())).thenReturn(List.of(home, away));
        var choices = IncidentLineupService.snapshot(fixture.race, List.of(home, away));
        service.lockRace(fixture.race.getId());
        var fields = new IncidentPayload("Untrusted text", "00:13.500", "Test description", "Test evidence");
        var result = service.resolve(fixture.race, fields, new IncidentDriverSelection(fixture.race.getId(), choices.fingerprint(),
                List.of(home.getDriver().getId(), away.getDriver().getId())));
        assertThat(result).isEqualTo(new IncidentPayload("Test away · TIA\nTest home · TIH", "00:13.500", "Test description", "Test evidence"));
        verify(locks).lockRace(fixture.race.getId());
        for (var row : List.of(home, away)) {
            verify(entities).refresh(row, LockModeType.PESSIMISTIC_READ);
            verify(entities).refresh(row.getDriver(), LockModeType.PESSIMISTIC_READ);
            verify(entities).refresh(row.getTeam(), LockModeType.PESSIMISTIC_READ);
        }
    }

    @Test
    void givenStaleOrForgedSelection_whenResolving_thenRejectWithoutUsingSubmittedNames() {
        var row = row("Test driver", fixture.home);
        when(rows.findByRaceIdForIncidentUpdate(fixture.race.getId())).thenReturn(List.of(row));
        var choices = IncidentLineupService.snapshot(fixture.race, List.of(row));
        var fields = new IncidentPayload("Fake", "00:00", "Test", "");
        assertThatThrownBy(() -> service.resolve(fixture.race, fields, new IncidentDriverSelection(fixture.race.getId(), "old", List.of(row.getDriver().getId()))))
                .hasMessageContaining("lineup changed");
        assertThatThrownBy(() -> service.resolve(fixture.race, fields, new IncidentDriverSelection(fixture.race.getId(), choices.fingerprint(), List.of(UUID.randomUUID()))))
                .isInstanceOf(IncidentLineupException.class);
        assertThatThrownBy(() -> service.resolve(fixture.race, fields, new IncidentDriverSelection(UUID.randomUUID(), choices.fingerprint(), List.of(row.getDriver().getId()))))
                .isInstanceOf(IncidentLineupException.class);
    }

    @Test
    void givenSelectionValues_whenValidating_thenRequireDistinctCurrentDriversAndMapOnlyExactSavedNames() {
        var home = row("Test home", fixture.home); var away = row("Test away", fixture.away);
        var choices = IncidentLineupService.snapshot(fixture.race, List.of(home, away));
        UUID id = home.getDriver().getId();
        assertThatThrownBy(() -> choices.selectedText(List.of())).isInstanceOf(IncidentLineupException.class);
        assertThatThrownBy(() -> choices.selectedText(List.of(id, id))).isInstanceOf(IncidentLineupException.class);
        assertThat(choices.savedSelection("Test home · TIH")).containsExactly(id);
        assertThat(choices.savedSelection("Test home · TIH\nUnknown")).isEmpty();
        assertThat(choices.savedSelection("Test home · TIH\nTest home · TIH")).isEmpty();
        assertThat(choices.savedSelection(null)).isEmpty();
        assertThat(choices.toString()).doesNotContain(id.toString(), "Test home");
    }

    @Test
    void givenLargeLineups_whenPreparingChoices_thenAllowTwoBoundedMenusAndRejectMoreThan25OnOneTeam() {
        var lineups = new ArrayList<RaceLineup>();
        IntStream.range(0, 25).forEach(index -> lineups.add(row("Test home " + index, fixture.home)));
        IntStream.range(0, 25).forEach(index -> lineups.add(row("Test away " + index, fixture.away)));
        assertThat(IncidentLineupService.snapshot(fixture.race, lineups).drivers()).hasSize(50);
        lineups.add(row("Test overflow", fixture.home));
        assertThatThrownBy(() -> IncidentLineupService.snapshot(fixture.race, lineups)).hasMessageContaining("more than 25");
    }

    RaceLineup row(String name, Team team) {
        var driver = new Driver(); driver.setId(UUID.randomUUID()); driver.setPsnId(name); driver.setNickname(name);
        return IncidentUnitFixture.id(new RaceLineup(fixture.race, driver, team));
    }
    Team team(String label) { var team = new Team(label, label); team.setId(UUID.randomUUID()); return team; }
}
