package org.ctc.discord.picks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.lineups.LineupChoice;
import org.ctc.domain.lineups.LineupReceipt;
import org.ctc.domain.lineups.LineupTarget;
import org.ctc.domain.lineups.LineupView;
import org.ctc.domain.model.LineupPayload;
import org.ctc.domain.model.LineupStatus;
import org.ctc.domain.picks.AuthorizedPickActor;
import org.ctc.domain.picks.ChannelPickActor;
import org.ctc.domain.service.LineupSubmissionService;
import org.ctc.domain.service.LineupTargetService;
import org.ctc.domain.service.PickAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LineupInteractionHandlerTest {
    final Clock clock = Clock.fixed(Instant.parse("2035-01-01T12:00:00Z"), ZoneOffset.UTC);
    final AtomicLong epoch = new AtomicLong();
    final PickAccessService access = mock(PickAccessService.class);
    final LineupTargetService targets = mock(LineupTargetService.class);
    final LineupSubmissionService submissions = mock(LineupSubmissionService.class);
    final LineupInteractionHandler handler = new LineupInteractionHandler(access, targets, submissions, new LineupSessionStore(clock, epoch::get, 8));
    final UUID race = UUID.randomUUID(), team = UUID.randomUUID(), nextRace = UUID.randomUUID();
    final ChannelPickActor channel = new ChannelPickActor("100", "200", "300", Set.of(new ChannelPickActor.Binding(UUID.randomUUID(), team)));
    final AuthorizedPickActor actor = new AuthorizedPickActor(race, UUID.randomUUID(), team, "300",
            new AuthorizedPickActor.ChannelContext("100", "200", 0, team));
    final List<LineupChoice> roster = IntStream.range(0, 30).mapToObj(i -> new LineupChoice(UUID.randomUUID(), "Test_D" + i, "Test_D" + i + " · ORG1")).toList();
    final LineupTarget target = new LineupTarget(race, team, "MD1 | ORG1 vs OPP (home) | Leg 1/2 | 2035-01-10 20:00 Berlin", LineupStatus.MISSING, 0,
            true, "Open until the deadline", Instant.parse("2035-01-10T18:00:00Z"), Instant.parse("2035-01-10T19:00:00Z"));
    PickInteractionHandlerTest.Response response;

    @BeforeEach
    void setUp() {
        // given
        when(access.authorizeChannel("100", "200", "300")).thenReturn(channel);
        when(access.authorizeLineupTarget(eq(channel), any(), eq(team))).thenReturn(actor);
        when(targets.list(channel)).thenReturn(List.of(target));
        when(targets.load(channel, race, team)).thenReturn(new LineupView(target, 6, null, false, roster, List.of()));
        when(submissions.submit(eq(race), eq(team), anyLong(), any(), eq(actor), any(), anyBoolean())).thenAnswer(call ->
                new LineupReceipt(race, team, call.getArgument(3), 1, 1, clock.instant(), "300", List.of("Test_D9 is registered for OTHER this season")));
        when(targets.nextLeg(channel, race, team)).thenReturn(Optional.empty());
    }

    @Test
    void givenThirtyDrivers_whenSelectingAcrossPagesAndAddingNewDriver_thenSubmitOrderedLineupOnlyAfterConfirming() {
        // given
        command(); chooseTarget(); click("Enter lineup");
        assertThat(response.last().selection().options()).as("the first page holds 25 drivers").hasSize(25);
        assertThat(response.last().selection().maxValues()).isEqualTo(25);
        assertThat(response.last().selection().minValues()).as("a page can be cleared").isZero();
        // when
        selectDrivers(roster.subList(0, 4));
        click("Next drivers");
        selectDrivers(roster.subList(26, 27));
        click("New driver");
        submitModal(Map.of("psn", List.of("Test_New1"), "name", List.of("Newcomer")));
        assertThat(response.last().content()).contains("Selected 6/6", "Newcomer (Test\\_New1) (new driver)");
        verifyNoInteractions(submissions);
        click("Review lineup");
        click("Confirm lineup");
        // then
        var payload = ArgumentCaptor.forClass(LineupPayload.class);
        verify(submissions).submit(eq(race), eq(team), eq(0L), any(), eq(actor), payload.capture(), eq(false));
        assertThat(payload.getValue().driverIds()).containsExactly(roster.get(0).driverId(), roster.get(1).driverId(),
                roster.get(2).driverId(), roster.get(3).driverId(), roster.get(26).driverId());
        assertThat(payload.getValue().proposed()).extracting(LineupPayload.Entry::psnId).containsExactly("Test_New1");
        assertThat(response.allText()).contains("Saved revision 1", "Note: Test\\_D9 is registered for OTHER this season");
    }

    @Test
    void givenTooManyDrivers_whenSelecting_thenKeepPreviousSelectionAndExplain() {
        // given
        command(); chooseTarget(); click("Enter lineup");
        selectDrivers(roster.subList(0, 6));
        click("Next drivers");
        // when
        selectDrivers(roster.subList(25, 26));
        // then
        assertThat(response.last().content()).contains("A lineup has at most 6 drivers", "Selected 6/6");
    }

    @Test
    void givenShortLineup_whenReviewing_thenRequireExplicitShortfallConfirmation() {
        // given
        command(); chooseTarget(); click("Enter lineup");
        selectDrivers(roster.subList(0, 4));
        // when
        click("Review lineup");
        // then
        assertThat(response.last().buttons()).extracting(PickGatewayMessage.Button::label).doesNotContain("Confirm lineup")
                .contains("Confirm with 4 of 6 drivers");
        click("Confirm with 4 of 6 drivers");
        verify(submissions).submit(eq(race), eq(team), eq(0L), any(), eq(actor), any(), eq(true));
    }

    @Test
    void givenRejectedOverlap_whenConfirming_thenShowTheReasonAndKeepTheDraft() {
        // given
        when(submissions.submit(any(), any(), anyLong(), any(), any(), any(), anyBoolean()))
                .thenThrow(new BusinessRuleException("A driver may field for only one team of an organisation on a matchday: Test_D1 is already in the lineup of ORG2"));
        command(); chooseTarget(); click("Enter lineup");
        selectDrivers(roster.subList(0, 6));
        click("Review lineup");
        // when
        click("Confirm lineup");
        // then
        assertThat(response.last().content()).contains("Not saved: A driver may field for only one team", "ORG2");
        assertThat(response.last().buttons()).extracting(PickGatewayMessage.Button::label).contains("Confirm lineup");
    }

    @Test
    void givenNextLegOpen_whenUsingLineupForNextLeg_thenPrefillReviewForThatLeg() {
        // given
        var next = new LineupTarget(nextRace, team, "MD1 | ORG1 vs OPP (away) | Leg 2/2 | 2035-01-10 21:00 Berlin", LineupStatus.MISSING, 0,
                true, "Open until the deadline", null, null);
        when(targets.nextLeg(channel, race, team)).thenReturn(Optional.of(next));
        when(targets.load(channel, nextRace, team)).thenReturn(new LineupView(next, 6, null, false, roster, List.of()));
        command(); chooseTarget(); click("Enter lineup");
        selectDrivers(roster.subList(0, 6));
        click("Review lineup"); click("Confirm lineup");
        // when
        click("Use this lineup for the next leg");
        // then
        assertThat(response.last().content()).contains("Leg 2/2", "Prefilled with the lineup of the previous leg", "Test\\_D5");
        assertThat(response.last().buttons()).extracting(PickGatewayMessage.Button::label).contains("Confirm lineup");
    }

    @Test
    void givenRestoredGeneration_whenUsingOldDialog_thenExpire() {
        // given
        command();
        String select = response.last().selection().id();
        epoch.incrementAndGet();
        // when
        dispatch(PickGatewayInteraction.Kind.SELECT, select, Map.of("selection", List.of(race + ":" + team)));
        // then
        assertThat(response.allText()).as("a restore invalidates the dialog").contains("Use /lineup to reload");
        verify(targets, never()).load(any(), any(), any());
    }

    void command() { dispatch(PickGatewayInteraction.Kind.COMMAND, "lineup", Map.of()); }
    void chooseTarget() { dispatch(PickGatewayInteraction.Kind.SELECT, response.last().selection().id(), Map.of("selection", List.of(race + ":" + team))); }
    void click(String label) {
        dispatch(PickGatewayInteraction.Kind.BUTTON, response.last().buttons().stream().filter(b -> b.label().equals(label)).findFirst().orElseThrow().id(), Map.of());
    }
    void selectDrivers(List<LineupChoice> chosen) {
        dispatch(PickGatewayInteraction.Kind.SELECT, response.last().selection().id(),
                Map.of("selection", new ArrayList<>(chosen.stream().map(choice -> choice.driverId().toString()).toList())));
    }
    void submitModal(Map<String, List<String>> values) { dispatch(PickGatewayInteraction.Kind.MODAL, response.modal.id(), values); }
    void dispatch(PickGatewayInteraction.Kind kind, String action, Map<String, List<String>> values) {
        var previous = response;
        response = new PickInteractionHandlerTest.Response();
        if (previous != null && kind == PickGatewayInteraction.Kind.MODAL) response.modal = previous.modal;
        var event = new PickGatewayInteraction("1000", "100", "200", "300", kind, action, values, clock.instant(), response);
        handler.onInteraction(event, new PickGatewayReply(response, clock, clock.instant(), epoch::get, Runnable::run));
    }
}
