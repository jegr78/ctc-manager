package org.ctc.discord.picks;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.*;
import org.ctc.domain.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PickInteractionHandlerTest {
    final PickAccessService access = mock(PickAccessService.class);
    final PickTargetService targets = mock(PickTargetService.class);
    final PickTeamReadService reads = mock(PickTeamReadService.class);
    final PickSubmissionService submissions = mock(PickSubmissionService.class);
    final PickDraftSessionStoreTest.MutableClock clock = new PickDraftSessionStoreTest.MutableClock();
    final PickSessionEpoch epoch = new PickSessionEpoch();
    final PickInteractionHandler handler = new PickInteractionHandler(access, targets, reads, submissions, new PickDraftSessionStore(clock, epoch, 512));
    final UUID race = UUID.randomUUID(), season = UUID.randomUUID(), team = UUID.randomUUID(), car = UUID.randomUUID(), track = UUID.randomUUID();
    final ChannelPickActor channel = new ChannelPickActor("100", "200", "300", Set.of(new ChannelPickActor.Binding(season, team)));
    final AuthorizedPickActor actor = new AuthorizedPickActor(race, season, team, "300", new AuthorizedPickActor.ChannelContext("100", "200", 0));
    HomeRaceTarget target = new HomeRaceTarget(race, "Test season 2026 | League | Matchday 2 | vs T-AWAY | Leg 2", PickState.MISSING, 0, true, "Before the deadline");
    PickTeamView view;
    Response response;
    String eventUser = "300", eventChannel = "200";
    final Map<String, List<String>> rules = fields("laps", "12", "wear", "2", "consumption", "3", "refuel", "4");
    final Map<String, List<String>> fuel = new HashMap<>(fields("initial", "DEFAULT", "stops", "1", "mandatory", "None"));

    @BeforeEach
    void setUp() {
        fuel.put("available", List.of("RS", "RM"));
        view = view(null, null, false, false, null);
        when(access.authorizeChannel(anyString(), anyString(), anyString())).thenReturn(channel);
        when(access.authorizeTarget(channel, race)).thenReturn(actor);
        when(targets.listHomeRaces(channel)).thenAnswer(call -> List.of(target));
        when(reads.load(channel, race)).thenAnswer(call -> view);
        when(submissions.submit(eq(race), anyLong(), any(), eq(actor), any())).thenAnswer(call ->
                new SubmissionReceipt(race, call.getArgument(2), 1, 1, clock.instant(), "300"));
    }

    @Test
    void givenAuthorizedChannel_whenStartingPicks_thenOfferPrivateHomeRaceChoice() {
        // when
        command();
        // then
        assertThat(response.text).contains("Choose your home race to view or edit its picks.");
        assertThat(response.last().content()).contains("Test season 2026", "Matchday 2", "T-AWAY", "Leg 2");
        assertThat(response.last().selection().options()).hasSize(1);
        verifyNoInteractions(submissions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"equal", "0", "6"})
    void givenCompletePrivateDraft_whenConfirmingAndReplaying_thenSaveAllFieldsOnlyAfterExplicitConfirmation(String progression) {
        // given
        complete(progression);
        var summary = response.last();
        assertThat(summary.content()).contains("Test season 2026", "Matchday 2", "Leg 2", "Track: Test track", "Car: Test car",
                "Laps: 12", "Tyre wear: 2", "Fuel consumption: 3", "Refuelling speed: 4", "Initial fuel: Default",
                "Required pit stops: 1", "Weather: S01; R03", "Time of day: Sunset", "Available tyres: RS, RM", "Mandatory tyres: None");
        assertThat(summary.content()).contains(progression.equals("equal") ? "Time progression: Equal Conditions" : "Time progression: " + progression + "x");
        verifyNoInteractions(submissions);
        String confirm = button("Confirm submission");
        // when
        dispatch(PickGatewayInteraction.Kind.BUTTON, confirm, Map.of());
        assertThat(response.last().content()).contains("Saved revision 1", "Leg 2", "Matchday 2");
        dispatch(PickGatewayInteraction.Kind.BUTTON, confirm, Map.of());
        // then
        var ids = ArgumentCaptor.forClass(UUID.class); var payloads = ArgumentCaptor.forClass(PickPayload.class);
        verify(submissions, times(2)).submit(eq(race), eq(0L), ids.capture(), eq(actor), payloads.capture());
        assertThat(ids.getAllValues().getFirst()).isEqualTo(ids.getAllValues().getLast());
        var payload = payloads.getValue();
        assertThat(payload.trackId()).isEqualTo(track); assertThat(payload.carId()).isEqualTo(car);
        assertThat(payload.mandatoryTyres()).isEmpty(); assertThat(payload.availableTyres()).containsExactlyInAnyOrder(PickTyre.RS, PickTyre.RM);
        assertThat(payload.timeProgressionMultiplier()).isEqualTo(progression.equals("equal") ? null : Integer.valueOf(progression));
        assertThat(payload.weather()).isEqualTo("S01; R03");
    }

    @Test
    void givenReturnedCurrentRevision_whenAnotherMemberViews_thenDisplaySavedPayloadAndReasonWithoutExposingDraft() {
        // given
        var payload = payload();
        target = new HomeRaceTarget(race, target.label(), PickState.RETURNED, 4, true, "Collection was opened by management");
        view = view(payload, new SubmissionReceipt(race, UUID.randomUUID(), 2, 3, clock.instant(), "301"), true, false, "Check weather order");
        eventUser = "302";
        // when
        command(); choose();
        // then
        assertThat(response.last().content()).contains("RETURNED", "Check weather order", "no new deadline", "revision 2", "by Discord user 301", "Weather: S01; R03");
        assertThat(response.last().buttons()).extracting(PickGatewayMessage.Button::label).contains("Replace picks");
        verify(access, atLeastOnce()).authorizeChannel("100", "200", "302");
        verifyNoInteractions(submissions);
    }

    @Test
    void givenApprovedOrClosedTarget_whenViewing_thenKeepStatusIndependentAndOfferNoEdit() {
        // given
        target = new HomeRaceTarget(race, target.label(), PickState.APPROVED, 5, false, "Approved picks are locked");
        view = view(payload(), new SubmissionReceipt(race, UUID.randomUUID(), 2, 4, clock.instant(), "301"), false, false, null);
        // when
        command(); choose();
        // then
        assertThat(response.last().content()).contains("APPROVED", "Approved picks are locked", "Deadline:", "Europe/Berlin +01:00");
        assertThat(response.last().buttons()).extracting(PickGatewayMessage.Button::label).containsExactly("Refresh current submission");
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "channel", "expired", "restore", "nonce", "kind", "permission", "target"})
    void givenInterruptedAuthorityOrSession_whenConfirming_thenSaveNothing(String failure) {
        // given
        complete("equal"); String confirm = button("Confirm submission");
        var kind = PickGatewayInteraction.Kind.BUTTON;
        switch (failure) {
            case "user" -> eventUser = "999";
            case "channel" -> eventChannel = "999";
            case "expired" -> clock.now = clock.now.plusSeconds(1800);
            case "restore" -> epoch.afterRestore(null);
            case "nonce" -> confirm += "x";
            case "kind" -> kind = PickGatewayInteraction.Kind.MODAL;
            case "permission" -> when(access.authorizeChannel(anyString(), anyString(), anyString())).thenThrow(new BusinessRuleException("Private role metadata"));
            case "target" -> when(reads.load(channel, race)).thenThrow(new BusinessRuleException("Target reoriented"));
            default -> throw new AssertionError();
        }
        // when
        dispatch(kind, confirm, Map.of());
        // then
        verifyNoInteractions(submissions);
        assertThat(response.allText()).doesNotContain("S01", "Test car", "Private role", "Target reoriented");
    }

    @Test
    void givenVersionChangedOrCommitFails_whenConfirming_thenNeverClaimSavedAndKeepOriginalVersion() {
        // given
        complete("equal"); String confirm = button("Confirm submission");
        when(submissions.submit(eq(race), anyLong(), any(), eq(actor), any())).thenThrow(new BusinessRuleException("Pick changed; reload its current version"));
        // when
        dispatch(PickGatewayInteraction.Kind.BUTTON, confirm, Map.of());
        // then
        assertThat(response.allText()).contains("reload the current submission").doesNotContain("Saved revision");
        verify(submissions).submit(eq(race), eq(0L), any(), eq(actor), any());
    }

    @Test
    void givenRestoreDuringFinalRead_whenNewAuthorityIsIssued_thenNeverAdoptNewGenerationForOldDraft() {
        // given
        complete("equal"); String confirm = button("Confirm submission");
        when(reads.load(channel, race)).thenAnswer(call -> { epoch.afterRestore(null); return view; });
        when(access.authorizeTarget(channel, race)).thenAnswer(call -> new AuthorizedPickActor(race, season, team, "300",
                new AuthorizedPickActor.ChannelContext("100", "200", epoch.getAsLong())));
        // when
        dispatch(PickGatewayInteraction.Kind.BUTTON, confirm, Map.of());
        // then
        verifyNoInteractions(submissions);
        assertThat(response.allText()).doesNotContain("Saved revision");
    }

    @ParameterizedTest
    @ValueSource(strings = {"laps", "wear", "consumption", "refuel"})
    void givenInvalidNumericInput_whenSubmittingRules_thenRetainEarlierChoicesAndOfferRetry(String field) {
        // given
        enterCatalogs(); open("Open rules form"); var invalid = new HashMap<>(rules); invalid.put(field, List.of("0"));
        // when
        submitModal(invalid);
        // then
        assertThat(response.last().content()).contains("Check all required values");
        assertThat(button("Open rules form")).startsWith("ctc-picks:");
        verifyNoInteractions(submissions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"mixed-none", "subset", "duplicates", "unknown"})
    void givenInvalidTyres_whenSubmittingFuel_thenRejectWithoutAdvancing(String error) {
        // given
        enterCatalogs(); open("Open rules form"); submitModal(rules); open("Open fuel form");
        var invalid = new HashMap<>(fuel);
        invalid.put("mandatory", switch (error) {
            case "mixed-none" -> List.of("None", "RS"); case "subset" -> List.of("RH");
            case "duplicates" -> List.of("RS", "RS"); default -> List.of("unknown"); });
        // when
        submitModal(invalid);
        // then
        assertThat(response.last().content()).contains("Check all required values");
        assertThat(button("Open fuel form")).isNotBlank();
        verifyNoInteractions(submissions);
    }

    @Test
    void givenLargeCatalog_whenSearchingAndPaging_thenValidateReturnedPageAndEscapeUntrustedNames() {
        // given
        var choices = java.util.stream.IntStream.range(0, 56).mapToObj(i -> new PickTeamView.Choice(UUID.randomUUID(), null, "Test track %02d".formatted(i))).toList();
        view = new PickTeamView(target, null, null, null, null, view.deadline(), false, false, null, view.cars(), choices);
        command(); choose(); click("Enter picks");
        assertThat(response.last().selection().options()).hasSize(25);
        click("Next"); assertThat(response.last().content()).contains("Page 2/3");
        click("Next"); assertThat(response.last().selection().options()).hasSize(6);
        open("Search track catalog"); submitModal(fields("query", "TRACK 55"));
        assertThat(response.last().selection().options()).hasSize(1);
        String action = response.last().selection().id();
        // when
        dispatch(PickGatewayInteraction.Kind.SELECT, action, fields("selection", choices.getFirst().id().toString()));
        // then
        assertThat(response.allText()).contains("unavailable"); verifyNoInteractions(submissions);
        assertThat(PickInteractionViews.escape("@everyone **test** [x](https://example.org) <@123>")).contains("@\u200beveryone", "\\*", "\\[");
    }

    @Test
    void givenForgedExtraModalFields_whenSubmittingFuel_thenRejectCrossStageChanges() {
        // given
        enterCatalogs(); open("Open rules form"); submitModal(rules); open("Open fuel form");
        var forged = new HashMap<>(fuel); forged.put("laps", List.of("99"));
        // when
        submitModal(forged);
        // then
        assertThat(response.last().content()).contains("Check all required values");
        verifyNoInteractions(submissions);
    }

    @Test
    void givenModalSubmission_whenAdvancing_thenReplyWithButtonInsteadOfChainingModal() {
        // given
        enterCatalogs(); open("Open rules form");
        // when
        submitModal(rules);
        // then
        assertThat(response.modal).isNull(); assertThat(response.last().buttons()).extracting(PickGatewayMessage.Button::label).contains("Open fuel form");
    }

    @Test
    void givenWithdrawnCatalogChoice_whenSelectingOldMenu_thenRejectBeforeAdvancing() {
        // given
        command(); choose(); click("Enter picks"); String action = response.last().selection().id();
        view = new PickTeamView(target, null, null, null, null, view.deadline(), false, false, null, view.cars(), List.of());
        // when
        dispatch(PickGatewayInteraction.Kind.SELECT, action, fields("selection", track.toString()));
        // then
        assertThat(response.allText()).contains("unavailable"); verifyNoInteractions(submissions);
    }

    @Test
    void givenForgedTargetSelection_whenSelecting_thenDoNotReadAnyPrivateTarget() {
        // given
        command(); String action = response.last().selection().id();
        // when
        dispatch(PickGatewayInteraction.Kind.SELECT, action, fields("selection", UUID.randomUUID().toString()));
        // then
        verifyNoInteractions(reads, submissions);
        assertThat(response.allText()).contains("unavailable");
    }

    void complete(String progression) {
        enterCatalogs(); open("Open rules form"); submitModal(rules); open("Open fuel form"); submitModal(fuel);
        open("Open time form"); submitModal(fields("progression", progression, "weather", "S01; R03", "daytime", "Sunset"));
    }
    void enterCatalogs() { command(); choose(); click("Enter picks"); select(track); select(car); }
    void command() { dispatch(PickGatewayInteraction.Kind.COMMAND, "picks", Map.of()); }
    void choose() { select(race); }
    void select(UUID id) { dispatch(PickGatewayInteraction.Kind.SELECT, response.last().selection().id(), fields("selection", id.toString())); }
    void click(String label) { dispatch(PickGatewayInteraction.Kind.BUTTON, button(label), Map.of()); }
    String button(String label) { return response.last().buttons().stream().filter(b -> b.label().equals(label)).findFirst().orElseThrow().id(); }
    void open(String label) { click(label); assertThat(response.modal).isNotNull(); }
    void submitModal(Map<String, List<String>> values) { dispatch(PickGatewayInteraction.Kind.MODAL, response.modal.id(), values); }
    void dispatch(PickGatewayInteraction.Kind kind, String action, Map<String, List<String>> values) {
        response = new Response();
        var event = new PickGatewayInteraction("1000", "100", eventChannel, eventUser, kind, action, values, clock.instant(), response);
        handler.onInteraction(event, new PickGatewayReply(response, clock, clock.instant(), epoch, Runnable::run));
    }
    PickTeamView view(PickPayload payload, SubmissionReceipt receipt, boolean manualOpen, boolean manualClosed, String reason) {
        return new PickTeamView(target, payload, receipt, "Test car", "Test track", "2035-01-01T12:00 Europe/Berlin +01:00", manualOpen, manualClosed, reason,
                List.of(new PickTeamView.Choice(car, null, "Test car")), List.of(new PickTeamView.Choice(track, null, "Test track")));
    }
    PickPayload payload() { return new PickPayload(track, car, null, 12, 2, 3, 4, PickInitialFuel.DEFAULT, 1, TimeProgressionMode.EQUAL_CONDITIONS, null,
            "S01; R03", "Sunset", Set.of(PickTyre.RS, PickTyre.RM), Set.of()); }
    static Map<String, List<String>> fields(String... pairs) {
        var values = new HashMap<String, List<String>>();
        for (int i = 0; i < pairs.length; i += 2) values.put(pairs[i], List.of(pairs[i + 1]));
        return values;
    }
    static final class Response implements PickGatewayResponse, PickGatewayResponse.Continuation {
        final List<String> text = new ArrayList<>(); final List<PickGatewayMessage> messages = new ArrayList<>(); PickGatewayModal modal;
        @Override public java.util.concurrent.CompletionStage<Continuation> deferPrivate() { return CompletableFuture.completedFuture(this); }
        @Override public void replyPrivate(String message) { text.add(message); }
        @Override public void replyPrivate(PickGatewayMessage message) { messages.add(message); }
        @Override public void openModal(PickGatewayModal value) { modal = value; }
        PickGatewayMessage last() { return messages.getLast(); }
        String allText() { return String.join("\n", text) + messages.stream().map(PickGatewayMessage::content).collect(java.util.stream.Collectors.joining("\n")); }
    }
}
