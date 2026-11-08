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
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PickInteractionHandlerTest {
    @Test
    void givenEightSlotDraft_whenReducingCountThenReusingAnOldMenu_thenKeepSixPositionsAndRejectTheRetiredNonce() {
        // given
        complete("equal"); click("Change weather"); selectCode("CUSTOM"); selectCode("8");
        String retired = response.last().selection().id();
        for (int slot = 0; slot < 8; slot++) selectCode(slot % 2 == 0 ? "S01" : "?");
        assertThat(response.last().selection().options()).hasSize(8);
        // when
        click("Change weather"); selectCode("CUSTOM"); selectCode("6");
        for (int slot = 0; slot < 6; slot++) click("Keep selected slot");
        assertThat(response.last().selection().options()).hasSize(6);
        String confirm = button("Confirm submission");
        dispatch(PickGatewayInteraction.Kind.SELECT, retired, fields("selection", "S02"));
        // then
        assertThat(response.allText()).contains("unavailable"); verifyNoInteractions(submissions);
        dispatch(PickGatewayInteraction.Kind.BUTTON, confirm, Map.of());
        var captured = ArgumentCaptor.forClass(PickPayload.class);
        verify(submissions).submit(eq(race), eq(0L), any(), eq(actor), captured.capture());
        assertThat(captured.getValue().weather()).isEqualTo("Custom S01; ?; S01; ?; S01; ?");
        assertThat(captured.getValue().laps()).isEqualTo(12);
    }

    @Test
    void givenCustomDraft_whenSwitchingToPreset_thenKeepOnlyOneCodeAndAllOtherPicks() {
        // given
        complete("0"); click("Change weather"); selectCode("CUSTOM"); selectCode("6");
        for (int slot = 0; slot < 6; slot++) selectCode("S03");
        // when
        click("Change weather"); selectCode("PRESET"); click("Keep selected slot"); click("Confirm submission");
        // then
        var captured = ArgumentCaptor.forClass(PickPayload.class);
        verify(submissions).submit(eq(race), eq(0L), any(), eq(actor), captured.capture());
        assertThat(captured.getValue().weather()).isEqualTo("Preset S03");
        assertThat(captured.getValue().timeProgressionMultiplier()).isZero();
    }

    @Test
    void givenSeveralCarsPerSeasonEntry_whenChoosingACar_thenSelectTheEntryBeforeSeeingOnlyItsCars() {
        // given
        UUID category = UUID.randomUUID(), oneMake = UUID.randomUUID(), secondCar = UUID.randomUUID(), otherCar = UUID.randomUUID();
        view = new PickTeamView(target, null, null, null, null, view.deadline(), false, false, null,
                List.of(new PickTeamView.Choice(car, category, "Test model one", false, "Test category"),
                        new PickTeamView.Choice(secondCar, category, "Test model two", false, "Test category"),
                        new PickTeamView.Choice(otherCar, oneMake, "Test One-Make", true, "Test One-Make")), view.tracks(), LobbyRulesProfile.CTC_2027);
        // when
        command(); choose(); click("Enter picks"); select(track);
        // then
        assertThat(response.last().selection().options()).extracting(PickGatewayModal.Option::value)
                .containsExactlyInAnyOrder(category.toString(), oneMake.toString());
        select(category);
        assertThat(response.last().selection().options()).extracting(PickGatewayModal.Option::value)
                .containsExactlyInAnyOrder(car.toString(), secondCar.toString());
        verifyNoInteractions(submissions);
    }

    @Test
    void givenSavedCategoryCarAndCompleteRevision_whenKeepingEveryDefault_thenSubmitTheSameValuesOnlyAfterConfirming() {
        // given
        UUID entry = UUID.randomUUID();
        var saved = new PickPayload(track, car, entry, 34, 5, 4, 8, PickInitialFuel.FUEL_60, 2,
                TimeProgressionMode.MULTIPLIER, 3, "Custom S02; S02; ?; C01; C02; S01", "Evening",
                Set.of(PickTyre.RM, PickTyre.RH, PickTyre.I, PickTyre.W), Set.of(PickTyre.RH),
                LobbyRulesProfile.CTC_2027, RaceStartType.ROLLING, RaceSlipstream.WEAK);
        target = new HomeRaceTarget(race, target.label(), PickState.SUBMITTED, 7, true, "Before the deadline");
        var cars = java.util.stream.IntStream.range(0, 62).mapToObj(index -> new PickTeamView.Choice(
                index == 47 ? car : UUID.randomUUID(), entry, "Test model %02d".formatted(index), false, "Test category")).toList();
        view = new PickTeamView(target, saved, new SubmissionReceipt(race, UUID.randomUUID(), 2, 7, clock.instant(), "300"),
                "Test model 47", "Test track", view.deadline(), false, false, null, cars, view.tracks(), LobbyRulesProfile.CTC_2027);
        // when
        command(); choose(); click("Replace picks"); click("Keep selected track");
        assertThat(response.last().selection().options().stream().filter(PickGatewayModal.Option::defaultSelected))
                .extracting(PickGatewayModal.Option::value).containsExactly(entry.toString());
        click("Keep selected category or One-Make");
        assertThat(response.last().content()).contains("Page 2/3", "Test model 47");
        click("Keep selected car");
        for (String stage : List.of("rules", "race strategy", "mandatory", "time")) {
            open("Open " + stage + " form");
            var defaults = new HashMap<String, List<String>>();
            for (var field : response.modal.fields()) {
                if (field instanceof PickGatewayModal.Text text) defaults.put(text.id(), List.of(text.value()));
                else if (field instanceof PickGatewayModal.Selection selection) {
                    defaults.put(selection.id(), selection.options().stream().filter(PickGatewayModal.Option::defaultSelected)
                            .map(PickGatewayModal.Option::value).toList());
                }
            }
            submitModal(defaults);
            verifyNoInteractions(submissions);
        }
        click("Keep selected weather"); click("Confirm submission");
        // then
        verify(submissions).submit(eq(race), eq(7L), any(), eq(actor), eq(saved));
    }

    @Test
    void givenSingletonCategory_whenChoosingIt_thenKeepTheCarStepAndDoNotGrantSportsPermission() {
        // given
        UUID entry = UUID.randomUUID();
        view = new PickTeamView(target, null, null, null, null, view.deadline(), false, false, null,
                List.of(new PickTeamView.Choice(car, entry, "Test single model", false, "Test singleton category")),
                view.tracks(), LobbyRulesProfile.CTC_2027);
        // when
        command(); choose(); click("Enter picks"); select(track); select(entry);
        // then
        assertThat(response.last().selection().options()).extracting(PickGatewayModal.Option::value).containsExactly(car.toString());
        select(car); open("Open rules form");
        assertThat(response.modal.fields()).extracting(PickGatewayModal.Field::id).doesNotContain("family");
        verifyNoInteractions(submissions);
    }

    @Test
    void givenCategoryAndAnotherEntry_whenForgingAnotherEntryCar_thenRejectWithoutSaving() {
        // given
        UUID category = UUID.randomUUID(), other = UUID.randomUUID(), otherCar = UUID.randomUUID();
        view = new PickTeamView(target, null, null, null, null, view.deadline(), false, false, null,
                List.of(new PickTeamView.Choice(car, category, "Test selected model", false, "Test category"),
                        new PickTeamView.Choice(otherCar, other, "Test other model", false, "Test other category")), view.tracks());
        command(); choose(); click("Enter picks"); select(track); select(category);
        String selection = response.last().selection().id();
        // when
        dispatch(PickGatewayInteraction.Kind.SELECT, selection, fields("selection", otherCar.toString()));
        // then
        assertThat(response.allText()).contains("unavailable"); verifyNoInteractions(submissions);
    }

    @Test
    void givenSelectedCategoryCarWithdrawnAfterRendering_whenSelecting_thenRecheckCurrentEligibility() {
        // given
        UUID entry = UUID.randomUUID();
        var oldCars = List.of(new PickTeamView.Choice(car, entry, "Test selected model", false, "Test category"));
        view = new PickTeamView(target, null, null, null, null, view.deadline(), false, false, null, oldCars, view.tracks());
        command(); choose(); click("Enter picks"); select(track); select(entry);
        String selection = response.last().selection().id();
        view = new PickTeamView(target, null, null, null, null, view.deadline(), false, false, null, List.of(), view.tracks());
        // when
        dispatch(PickGatewayInteraction.Kind.SELECT, selection, fields("selection", car.toString()));
        // then
        assertThat(response.allText()).contains("unavailable"); verifyNoInteractions(submissions);
    }

    @Test
    void givenExplicitOneMakeWithConflictingMembers_whenSelectingIt_thenRejectInsteadOfChoosingAnArbitraryCar() {
        // given
        UUID entry = UUID.randomUUID();
        view = new PickTeamView(target, null, null, null, null, view.deadline(), false, false, null,
                List.of(new PickTeamView.Choice(car, entry, "Test first model", true, "Test One-Make"),
                        new PickTeamView.Choice(UUID.randomUUID(), entry, "Test second model", true, "Test One-Make")), view.tracks());
        command(); choose(); click("Enter picks"); select(track);
        // when
        select(entry);
        // then
        assertThat(response.allText()).contains("unavailable"); verifyNoInteractions(submissions);
    }

    @Test
    void givenSportsRevision_whenChangingTheFamily_thenClearIncompatibleAvailableAndMandatoryDefaults() {
        // given
        UUID entry = UUID.randomUUID();
        var saved = new PickPayload(track, car, entry, 12, 2, 3, 4, PickInitialFuel.FUEL_60, 1,
                TimeProgressionMode.MULTIPLIER, 2, "S01", "Afternoon", Set.of(PickTyre.SS, PickTyre.SM), Set.of(PickTyre.SM),
                LobbyRulesProfile.CTC_2027, RaceStartType.ROLLING, RaceSlipstream.WEAK);
        target = new HomeRaceTarget(race, target.label(), PickState.SUBMITTED, 1, true, "Before the deadline");
        view = new PickTeamView(target, saved, new SubmissionReceipt(race, UUID.randomUUID(), 1, 1, clock.instant(), "300"), "Test model", "Test track", view.deadline(), false, false, null,
                List.of(new PickTeamView.Choice(car, entry, "Test model", true, "Test One-Make")), view.tracks(), LobbyRulesProfile.CTC_2027);
        command(); choose(); click("Replace picks"); click("Keep selected track"); click("Keep selected category or One-Make");
        open("Open rules form");
        var changedRules = new HashMap<>(rules); changedRules.put("family", List.of("RACING"));
        // when
        submitModal(changedRules); open("Open race strategy form");
        // then
        var available = (PickGatewayModal.Selection) response.modal.fields().stream().filter(field -> field.id().equals("available")).findFirst().orElseThrow();
        assertThat(available.options()).extracting(PickGatewayModal.Option::value).containsExactly("RS", "RM", "RH");
        assertThat(available.options()).noneMatch(PickGatewayModal.Option::defaultSelected);
        submitModal(fuel); open("Open mandatory form");
        var mandatory = (PickGatewayModal.Selection) response.modal.fields().getFirst();
        assertThat(mandatory.options().stream().filter(PickGatewayModal.Option::defaultSelected))
                .extracting(PickGatewayModal.Option::value).containsExactly("None");
        verifyNoInteractions(submissions);
    }

    @Test
    void givenSavedTrackOutsideFirstPage_whenReplacing_thenOpenItsPageWithTheSavedChoiceSelected() {
        // given
        target = new HomeRaceTarget(race, target.label(), PickState.SUBMITTED, 1, true, "Before the deadline");
        var saved = view(payload(), new SubmissionReceipt(race, UUID.randomUUID(), 1, 1, clock.instant(), "300"), false, false, null);
        var tracks = java.util.stream.IntStream.range(0, 130).mapToObj(i -> new PickTeamView.Choice(
                i == 110 ? track : UUID.randomUUID(), null, "Test circuit %03d".formatted(i))).toList();
        view = new PickTeamView(saved.target(), saved.payload(), saved.receipt(), saved.carLabel(), saved.trackLabel(), saved.deadline(),
                false, false, null, saved.cars(), tracks);
        // when
        command(); choose(); click("Replace picks");
        // then
        assertThat(response.last().content()).contains("Page 5/6");
        assertThat(response.last().selection().options().stream().filter(PickGatewayModal.Option::defaultSelected))
                .extracting(PickGatewayModal.Option::value).containsExactly(track.toString());
        verifyNoInteractions(submissions);
    }

    @Test
    void givenPreviouslySubmittedValues_whenReplacing_thenPrefillExistingRulesWithoutSaving() {
        // given
        target = new HomeRaceTarget(race, target.label(), PickState.SUBMITTED, 1, true, "Before the deadline");
        view = view(payload(), new SubmissionReceipt(race, UUID.randomUUID(), 1, 1, clock.instant(), "300"), false, false, null);
        command(); choose(); click("Replace picks"); select(track); select(car);
        // when
        open("Open rules form");
        // then
        var values = response.modal.fields().stream().filter(PickGatewayModal.Text.class::isInstance)
                .map(PickGatewayModal.Text.class::cast)
                .collect(java.util.stream.Collectors.toMap(PickGatewayModal.Text::id, field -> String.valueOf(field.value())));
        assertThat(values).containsExactlyEntriesOf(Map.of("laps", "12"));
        for (var expected : Map.of("wear", "2", "consumption", "3", "refuel", "4").entrySet()) {
            var selection = (PickGatewayModal.Selection) response.modal.fields().stream()
                    .filter(field -> field.id().equals(expected.getKey())).findFirst().orElseThrow();
            assertThat(selection.options().stream().filter(PickGatewayModal.Option::defaultSelected))
                    .extracting(PickGatewayModal.Option::value).containsExactly(expected.getValue());
        }
        verifyNoInteractions(submissions);
    }

    @ParameterizedTest
    @CsvSource({"'SS,SM,SH',SM", "'RS,RM,RH',RM"})
    void givenSaved2027OneMake_whenCompletingTheWizard_thenSubmitEveryCompoundOnlyAfterConfirmation(String available, String mandatory) {
        // given
        UUID entry = UUID.randomUUID();
        view = new PickTeamView(view.target(), null, null, view.carLabel(), view.trackLabel(), view.deadline(), false, false, null,
                List.of(new PickTeamView.Choice(car, entry, "Test One-Make", true)), view.tracks(), LobbyRulesProfile.CTC_2027);
        enterCatalogs(); open("Open rules form");
        var chosenRules = new HashMap<>(rules); chosenRules.put("family", List.of(available.startsWith("S") ? "SPORTS" : "RACING"));
        submitModal(chosenRules); open("Open race strategy form");
        var choices = (PickGatewayModal.Selection) response.modal.fields().stream().filter(f -> f.id().equals("available")).findFirst().orElseThrow();
        assertThat(choices.options()).extracting(PickGatewayModal.Option::value)
                .containsExactly(available.startsWith("S") ? new String[]{"SS", "SM", "SH"} : new String[]{"RS", "RM", "RH"});
        var selected = new HashMap<>(fuel); selected.put("available", List.of(available.split(",")));
        // when
        submitModal(selected); open("Open mandatory form"); submitModal(fields("mandatory", mandatory)); open("Open time form");
        submitModal(fields("progression", "0", "daytime", "Night", "start", "ROLLING", "slipstream", "WEAK"));
        verifyNoInteractions(submissions);
        preset(); click("Confirm submission");
        // then
        var captured = ArgumentCaptor.forClass(PickPayload.class);
        verify(submissions).submit(eq(race), eq(0L), any(), eq(actor), captured.capture());
        assertThat(captured.getValue().carPoolEntryId()).isEqualTo(entry);
        assertThat(captured.getValue().availableTyres()).extracting(Enum::name).containsExactlyInAnyOrder((available.startsWith("S") ? available : available + ",I,W").split(","));
        assertThat(captured.getValue().mandatoryTyres()).extracting(Enum::name).containsExactly(mandatory);
        assertThat(response.last().content()).contains("Saved revision 1");
    }

    @ParameterizedTest
    @CsvSource({"SS,None,false", "'RS,SS',None,true", "'RS,I',I,true", "'RS,W',W,true"})
    void givenInvalidOrCategorySportsSelection_whenSubmittingTheFuelModal_thenRemainInTheDraft(String available, String mandatory, boolean oneMake) {
        // given
        view = new PickTeamView(view.target(), null, null, view.carLabel(), view.trackLabel(), view.deadline(), false, false, null,
                List.of(new PickTeamView.Choice(car, UUID.randomUUID(), "Test saved definition", oneMake)), view.tracks(), LobbyRulesProfile.CTC_2027);
        enterCatalogs(); open("Open rules form");
        var chosenRules = new HashMap<>(rules); if (oneMake) chosenRules.put("family", List.of("RACING"));
        submitModal(chosenRules); open("Open race strategy form");
        var selected = new HashMap<>(fuel); selected.put("available", List.of(available.split(",")));
        // when
        submitModal(selected);
        // then
        assertThat(response.last().content()).contains("Check all required values");
        assertThat(response.last().buttons()).extracting(PickGatewayMessage.Button::label).containsExactly("Open race strategy form");
        verifyNoInteractions(submissions);
    }

    @Test
    void given2027DraftAndLaterProfileChange_whenConfirming_thenKeepCapturedChoicesAndDoNotClaimSuccessOnRejection() {
        // given
        view = new PickTeamView(view.target(), view.payload(), view.receipt(), view.carLabel(), view.trackLabel(), view.deadline(),
                view.matchdayOpen(), view.manualClosed(), view.returnReason(), view.cars(), view.tracks(), LobbyRulesProfile.CTC_2027);
        enterCatalogs(); open("Open rules form"); submitModal(rules); open("Open race strategy form"); submitModal(fuel); open("Open mandatory form"); submitModal(fields("mandatory", "None")); open("Open time form");
        assertThat(response.modal.fields()).hasSize(4);
        view = new PickTeamView(view.target(), view.payload(), view.receipt(), view.carLabel(), view.trackLabel(), view.deadline(),
                view.matchdayOpen(), view.manualClosed(), view.returnReason(), view.cars(), view.tracks(), LobbyRulesProfile.LEGACY);
        submitModal(fields("progression", "0", "daytime", "Night", "start", "ROLLING", "slipstream", "WEAK"));
        preset();
        assertThat(response.last().buttons()).extracting(PickGatewayMessage.Button::label).contains("Confirm submission");
        assertThat(response.last().content()).contains("Rolling Start", "Weak");
        when(submissions.submit(eq(race), anyLong(), any(), eq(actor), any())).thenThrow(new BusinessRuleException("Rules changed; reload"));
        // when
        click("Confirm submission");
        // then
        var captured = ArgumentCaptor.forClass(PickPayload.class);
        verify(submissions).submit(eq(race), eq(0L), any(), eq(actor), captured.capture());
        assertThat(captured.getValue().lobbyRulesProfile()).isEqualTo(LobbyRulesProfile.CTC_2027);
        assertThat(captured.getValue().startType()).isEqualTo(RaceStartType.ROLLING);
        assertThat(captured.getValue().slipstream()).isEqualTo(RaceSlipstream.WEAK);
        assertThat(response.allText()).contains("reload").doesNotContain("Saved revision");
    }

    @Test
    void givenKnownLayout_whenForgingTimeOutsideChoices_thenRetainTimeStepAndDoNotSave() {
        view = new PickTeamView(target, null, null, null, null, view.deadline(), false, false, null, view.cars(),
                List.of(new PickTeamView.Choice(track, null, "Test track", false, null, List.of("Night", "Sunrise"))));
        enterCatalogs(); open("Open rules form"); submitModal(rules);
        open("Open race strategy form"); submitModal(fuel);
        open("Open mandatory form"); submitModal(fields("mandatory", "None")); open("Open time form");
        submitModal(fields("progression", "0", "daytime", "Afternoon"));
        assertThat(response.last().content()).contains("Check all required values");
        assertThat(response.last().buttons()).extracting(PickGatewayMessage.Button::label).containsExactly("Open time form");
        verifyNoInteractions(submissions);
        open("Open time form");
        submitModal(fields("progression", "0", "daytime", "Night"));
        preset(); click("Confirm submission");
        var payload = ArgumentCaptor.forClass(PickPayload.class);
        verify(submissions).submit(eq(race), eq(0L), any(), eq(actor), payload.capture());
        assertThat(payload.getValue().timeOfDay()).isEqualTo("Night");
        assertThat(payload.getValue().weather()).isEqualTo("Preset S01");
    }

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
    final Map<String, List<String>> fuel = new HashMap<>(fields("initial", "DEFAULT", "stops", "1"));

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
        assertThat(response.text).isEmpty();
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
                "Required pit stops: 1", "Weather: Preset S01", "Time of day: Sunset", "Available tyres: RS, RM, I, W", "Mandatory tyres: None");
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
        assertThat(payload.mandatoryTyres()).isEmpty(); assertThat(payload.availableTyres()).containsExactlyInAnyOrder(PickTyre.RS, PickTyre.RM, PickTyre.I, PickTyre.W);
        assertThat(payload.timeProgressionMultiplier()).isEqualTo(progression.equals("equal") ? null : Integer.valueOf(progression));
        assertThat(payload.weather()).isEqualTo("Preset S01");
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
        assertThat(response.last().content()).contains("RETURNED", "Check weather order", "Matchday picks: Open", "revision 2", "by <@301>", "Weather: S01; R03");
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
    void givenInvalidTyres_whenSubmittingMandatory_thenRejectWithoutAdvancing(String error) {
        // given
        enterCatalogs(); open("Open rules form"); submitModal(rules); open("Open race strategy form");
        submitModal(fuel); open("Open mandatory form");
        var invalid = new HashMap<String, List<String>>();
        invalid.put("mandatory", switch (error) {
            case "mixed-none" -> List.of("None", "RS"); case "subset" -> List.of("RH");
            case "duplicates" -> List.of("RS", "RS"); default -> List.of("unknown"); });
        // when
        submitModal(invalid);
        // then
        assertThat(response.last().content()).contains("Check all required values");
        assertThat(button("Open mandatory form")).isNotBlank();
        verifyNoInteractions(submissions);
    }

    @Test
    void givenUnmatchedTrackQuery_whenSearching_thenExplainScopeAndAllowClearingQuery() {
        // given
        command(); choose(); click("Enter picks");
        open("Search track catalog");
        // when
        submitModal(fields("query", "Spa"));
        // then
        assertThat(response.last().content()).contains("No available tracks match your search", "season track pool", "Clear the search");
        assertThat(response.last().selection()).isNull();
        open("Search track catalog"); submitModal(fields("query", ""));
        assertThat(response.last().selection().options()).extracting(PickGatewayModal.Option::value).containsExactly(track.toString());
        verifyNoInteractions(submissions);
    }

    @Test
    void givenNoEligibleTracks_whenOpeningCatalog_thenExplainPoolAndPriorUseInsteadOfUnmatchedQuery() {
        // given
        view = new PickTeamView(target, null, null, null, null, view.deadline(), false, false, null, view.cars(), List.of());
        // when
        command(); choose(); click("Enter picks");
        // then
        assertThat(response.last().content()).contains("No tracks are available", "season track pool", "used or reserved")
                .doesNotContain("No available tracks match your search");
        assertThat(response.last().selection()).isNull();
        verifyNoInteractions(submissions);
    }

    @Test
    void givenDeadlineInstant_whenViewing_thenRenderNativeFullAndRelativeTimestampsWithoutEscaping() {
        // given
        var instant = java.time.Instant.parse("2026-11-08T19:30:00Z");
        view = new PickTeamView(target, null, null, null, null, view.deadline(), true, false, null,
                view.cars(), view.tracks(), LobbyRulesProfile.CTC_2027, instant);
        // when
        command(); choose();
        // then
        assertThat(response.last().content()).contains("Deadline: <t:" + instant.getEpochSecond() + ":F> · <t:"
                + instant.getEpochSecond() + ":R>").doesNotContain("Europe/Berlin", "\\<t:");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Not configured", "2027-10-31T02:30 Europe/Berlin requires management review", "<@123>"})
    void givenNoDeadlineInstant_whenViewing_thenKeepEscapedExplanationWithoutInventingTimestamp(String label) {
        // given
        view = new PickTeamView(target, null, null, null, null, label, true, false, null, view.cars(), view.tracks());
        // when
        command(); choose();
        // then
        assertThat(response.last().content()).contains("Deadline: " + PickInteractionViews.escape(label)).doesNotContain("<t:", "<@123>");
    }

    @Test
    void givenConfirmedReceipt_whenRendering_thenLocalizeSavedTimeAndKeepAuthorMention() {
        // given
        var receipt = new SubmissionReceipt(race, UUID.randomUUID(), 2, 3,
                java.time.Instant.parse("2026-11-06T13:46:21.104055Z"), "300");
        // when
        var text = PickInteractionViews.receipt(receipt);
        // then
        assertThat(text).contains("saved <t:1793972781:F> by <@300>").doesNotContain("2026-11-06T13:46");
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
        enterCatalogs(); open("Open rules form"); submitModal(rules); open("Open race strategy form");
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
        assertThat(response.modal).isNull(); assertThat(response.last().buttons()).extracting(PickGatewayMessage.Button::label).contains("Open race strategy form");
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
        enterCatalogs(); open("Open rules form"); submitModal(rules); open("Open race strategy form"); submitModal(fuel);
        open("Open mandatory form"); submitModal(fields("mandatory", "None"));
        open("Open time form"); submitModal(fields("progression", progression, "daytime", "Sunset"));
        preset();
    }
    void preset() { selectCode("PRESET"); selectCode("S01"); }
    void selectCode(String value) { dispatch(PickGatewayInteraction.Kind.SELECT, response.last().selection().id(), fields("selection", value)); }
    void enterCatalogs() {
        command(); choose(); click("Enter picks"); select(track);
        var chosen = view.cars().stream().filter(choice -> choice.id().equals(car)).findFirst().orElseThrow();
        if (chosen.entryId() != null) select(chosen.entryId());
        if (!chosen.oneMake()) select(car);
    }
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
