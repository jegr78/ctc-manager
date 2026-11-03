package org.ctc.discord.incidents;

import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.discord.picks.*;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.ctc.domain.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IncidentInteractionHandlerTest {
    static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    static final String GUILD = "100000000000000001", CHANNEL = "200000000000000002", USER = "400000000000000004";
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final PickSessionEpoch epoch = new PickSessionEpoch();
    final ImportLockService imports = new ImportLockService(Duration.ofMillis(100));
    final IncidentAccessService access = mock(IncidentAccessService.class);
    final IncidentPrivateReadService reads = mock(IncidentPrivateReadService.class);
    final IncidentReportService reports = mock(IncidentReportService.class);
    final IncidentDraftSessionStore sessions = new IncidentDraftSessionStore(clock, epoch, 16);
    final IncidentInteractionHandler handler = new IncidentInteractionHandler(access, reads, reports, sessions, imports, epoch);
    final UUID context = UUID.randomUUID(), window = UUID.randomUUID(), race = UUID.randomUUID(), report = UUID.randomUUID();
    final AuthorizedIncidentActor actor = new AuthorizedIncidentActor(context, GUILD, CHANNEL, USER,
            new IncidentParty(IncidentPartyType.TEAM, UUID.randomUUID(), "Test reporting team", "500000000000000005"));
    final IncidentPayload payload = new IncidentPayload("Test involved guest", "00:00", "Test private incident marker", "https://example.invalid/video");
    final IncidentReporterView.RaceChoice choice = new IncidentReporterView.RaceChoice(race, "Test season | Test matchday 2 | T-HOME vs T-AWAY | Leg 1");
    IncidentReporterView view;
    Response response;
    String eventUser = USER, eventChannel = CHANNEL;

    @BeforeEach
    void setUp() {
        view = new IncidentReporterView(context, window, 7, true, "Test pairing", List.of(choice), List.of());
        when(access.authorizeChannel(GUILD, CHANNEL, USER)).thenReturn(actor);
        when(reads.home(actor)).thenAnswer(call -> view);
        when(reports.submit(eq(window), eq(race), anyLong(), any(), eq(actor), any())).thenAnswer(call -> receipt(IncidentEventKind.SUBMITTED, call.getArgument(3)));
        when(reports.edit(eq(report), anyLong(), anyLong(), any(), eq(actor), any())).thenAnswer(call -> receipt(IncidentEventKind.EDITED, call.getArgument(3)));
        when(reports.withdraw(eq(report), anyLong(), anyLong(), any(), eq(actor))).thenAnswer(call -> receipt(IncidentEventKind.WITHDRAWN, call.getArgument(3)));
    }

    @Test
    void givenMatchChannelEntry_whenStarting_thenAcknowledgePrivatelyAndOfferNewAndOwnReports() {
        command(); assertThat(response.deferred).isTrue(); assertThat(response.initial).isNull();
        assertThat(last().content()).contains("Test pairing"); assertThat(last().buttons()).extracting(PickGatewayMessage.Button::label).contains("New report", "My reports");
        verifyNoInteractions(reports);
    }

    @ParameterizedTest
    @ValueSource(strings = {"00:00", "lap 3 near the finish", "1:23.456"})
    void givenCompleteFourFieldDraft_whenConfirmingTwice_thenSaveOnlyAfterConfirmationWithOneDurableRequest(String replay) {
        complete(fields(payload.getInvolvedDrivers(), replay, payload.getDescription(), payload.getEvidenceLinks()));
        assertThat(last().content()).contains("Test matchday 2", "Leg 1", "Test reporting team", replay, "Test private incident marker");
        verifyNoInteractions(reports); String confirm = button("Confirm report");
        dispatch(PickGatewayInteraction.Kind.BUTTON, confirm, Map.of());
        assertThat(last().content()).contains("Stored", "INC-test", "pending");
        dispatch(PickGatewayInteraction.Kind.BUTTON, confirm, Map.of());
        var requests = ArgumentCaptor.forClass(UUID.class); var values = ArgumentCaptor.forClass(IncidentPayload.class);
        verify(reports, times(2)).submit(eq(window), eq(race), eq(7L), requests.capture(), eq(actor), values.capture());
        assertThat(requests.getAllValues().getFirst()).isEqualTo(requests.getAllValues().getLast());
        assertThat(values.getValue().getReplayTimestamp()).isEqualTo(replay); assertThat(values.getValue().getDescription()).isEqualTo(payload.getDescription());
    }

    @Test
    void givenSeveralRaces_whenStarting_thenSelectOnlyAServerOfferedRaceBeforeTheForm() {
        var other = new IncidentReporterView.RaceChoice(UUID.randomUUID(), "Test matchday 2 | Leg 2");
        view = new IncidentReporterView(context, window, 7, true, view.label(), List.of(choice, other), List.of());
        command(); click("New report"); assertThat(last().selection().options()).hasSize(2);
        String selection = last().selection().id(); dispatch(PickGatewayInteraction.Kind.SELECT, selection, Map.of("selection", List.of(UUID.randomUUID().toString())));
        assertThat(response.text).contains("unavailable"); verifyNoInteractions(reports);
    }

    @Test
    void givenAnotherUserOrChannel_whenConfirmingAnotherDraft_thenDenyWithoutSaving() {
        complete(fields("Test guest", "00:00", "Test private marker", "")); String confirm = button("Confirm report");
        eventUser = "499999999999999999"; dispatch(PickGatewayInteraction.Kind.BUTTON, confirm, Map.of());
        assertThat(response.text).contains("unavailable"); eventUser = USER; eventChannel = "299999999999999999";
        dispatch(PickGatewayInteraction.Kind.BUTTON, confirm, Map.of()); assertThat(response.text).contains("unavailable"); verifyNoInteractions(reports);
    }

    @Test
    void givenRestoreResettingDraftGeneration_whenConfirming_thenDenyWithoutSaving() {
        complete(fields("Test guest", "00:00", "Test private marker", "")); String confirm = button("Confirm report");
        epoch.afterRestore(null); dispatch(PickGatewayInteraction.Kind.BUTTON, confirm, Map.of());
        assertThat(response.text).contains("unavailable"); verifyNoInteractions(reports);
    }

    @Test
    void givenRevokedRolesAfterReview_whenConfirming_thenRecheckAccessWithoutLeakingExceptionDetails() {
        complete(fields("Test guest", "00:00", "Test private marker", "")); String confirm = button("Confirm report");
        when(access.authorizeChannel(GUILD, CHANNEL, USER)).thenThrow(new BusinessRuleException("private callback token"));
        dispatch(PickGatewayInteraction.Kind.BUTTON, confirm, Map.of()); assertThat(response.text).contains("unavailable").doesNotContain("private callback token"); verifyNoInteractions(reports);
        assertAdmissionReleased();
    }

    @Test
    void givenChangedReportingPartyAfterReview_whenConfirming_thenRequireAReviewedNewDraft() {
        complete(fields("Test guest", "00:00", "Test private marker", "")); String confirm = button("Confirm report");
        var changed = new AuthorizedIncidentActor(context, GUILD, CHANNEL, USER, new IncidentParty(IncidentPartyType.TEAM, UUID.randomUUID(), "Test other team", "600000000000000006"));
        when(access.authorizeChannel(GUILD, CHANNEL, USER)).thenReturn(changed); when(reads.home(changed)).thenReturn(view);
        dispatch(PickGatewayInteraction.Kind.BUTTON, confirm, Map.of()); assertThat(response.text).contains("unavailable"); verifyNoInteractions(reports);
    }

    @Test
    void givenOwnStoredCase_whenEditingAndWithdrawing_thenUseOriginalVersionsAndNoNewSubmit() {
        ownCase(); command(); click("My reports"); select(report); assertThat(last().content()).contains("Test private incident marker");
        click("Edit report"); click("Enter report"); assertThat(response.modal.fields()).allMatch(field -> field instanceof PickGatewayModal.Text text && text.value() == null);
        dispatch(PickGatewayInteraction.Kind.MODAL, response.modal.id(), fields("Test changed guest", "00:00", "Test corrected description", ""));
        click("Confirm changes"); verify(reports).edit(eq(report), eq(7L), eq(3L), any(), eq(actor), any());
        command(); click("My reports"); select(report); click("Withdraw report"); click("Confirm withdrawal");
        verify(reports).withdraw(eq(report), eq(7L), eq(3L), any(), eq(actor)); verify(reports, never()).submit(any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    void givenStaleEdit_whenConfirming_thenKeepItsBaseVersionAndWarnWithoutReplacingTheCase() {
        ownCase(); command(); click("My reports"); select(report); click("Edit report"); click("Enter report");
        dispatch(PickGatewayInteraction.Kind.MODAL, response.modal.id(), fields("Test guest", "00:00", "Test correction", ""));
        when(reports.edit(eq(report), eq(7L), eq(3L), any(), eq(actor), any())).thenThrow(new BusinessRuleException("version changed"));
        click("Confirm changes"); assertThat(response.text).contains("changed"); assertAdmissionReleased();
    }

    @Test
    void givenRestoreHoldingAdmission_whenStarting_thenDenyBeforeRoleOrPrivateCaseQueries() {
        assertThat(imports.tryLock()).isTrue();
        try { command(); assertThat(response.text).contains("restore"); verifyNoInteractions(access, reads, reports); }
        finally { imports.unlock(); }
    }

    @Test
    void givenExpiredReportingWindow_whenStarting_thenOfferOwnHistoryWithoutNewReport() {
        view = new IncidentReporterView(context, window, 7, false, view.label(), view.races(), List.of());
        command(); assertThat(last().buttons()).extracting(PickGatewayMessage.Button::label).doesNotContain("New report").contains("My reports");
        verifyNoInteractions(reports);
    }

    @Test
    void givenLongPrivateDraft_whenReviewing_thenPageEveryCharacterWithinDiscordLimitsBeforeConfirmation() {
        String description = "Test long private description ".repeat(120), evidence = "https://example.invalid/" + "a".repeat(3500);
        complete(fields("Test guest", "00:00", description, evidence));
        var text = new StringBuilder(); int pages = 0;
        while (true) {
            var message = last(); assertThat(message.content()).hasSizeLessThanOrEqualTo(2000); text.append(message.content().substring(message.content().indexOf('\n') + 1)); pages++;
            if (message.buttons().stream().noneMatch(button -> button.label().equals("Next"))) break;
            assertThat(message.buttons()).extracting(PickGatewayMessage.Button::label).doesNotContain("Confirm report"); click("Next");
        }
        assertThat(pages).isGreaterThan(1); assertThat(text.toString()).contains(description, evidence); button("Confirm report");
        verifyNoInteractions(reports);
    }

    @Test
    void givenPrivatePayload_whenCreatingComponents_thenKeepTextAndReporterIdentityOutOfCustomIds() {
        complete(fields("Test guest", "00:00", "Test private marker", ""));
        for (var message : response.messages) {
            for (var control : message.buttons()) assertThat(control.id()).doesNotContain("Test private", USER).hasSizeLessThanOrEqualTo(100);
            if (message.selection() != null) assertThat(message.selection().id()).doesNotContain(USER, "Test guest");
        }
        assertThat(response.modal.id()).doesNotContain(USER, "Test guest");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"message", "modal"})
    void givenPublishedNextAction_whenAnotherThreadResolvesItImmediately_thenReleaseThePreviousSessionLockFirst(String kind) {
        dispatch(PickGatewayInteraction.Kind.COMMAND, "incidents", Map.of());
        var checked = new java.util.concurrent.atomic.AtomicBoolean();
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        response.onPublish = value -> {
            String id; PickGatewayInteraction.Kind nextKind;
            if (kind.equals("message") && value instanceof PickGatewayMessage message) {
                var next = message.buttons().stream().filter(item -> item.label().equals("Enter report")).findFirst();
                if (next.isEmpty()) return;
                id = next.orElseThrow().id(); nextKind = PickGatewayInteraction.Kind.BUTTON;
            } else if (kind.equals("modal") && value instanceof PickGatewayModal modal) {
                id = modal.id(); nextKind = PickGatewayInteraction.Kind.MODAL;
            } else return;
            checked.set(true);
            var next = new PickGatewayInteraction(UUID.randomUUID().toString(), GUILD, eventChannel, eventUser, nextKind, id, Map.of(), NOW, response);
            try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                workers.submit(() -> sessions.withAction(next, (session, action) -> true)).get(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception denied) { failure.set(denied); }
        };
        click("New report"); if (kind.equals("modal")) click("Enter report");
        assertThat(checked).isTrue(); assertThat(failure.get()).as("published controls must already be usable by the next interaction").isNull();
    }

    void ownCase() {
        var own = new IncidentReporterView.CaseChoice(report, race, "INC-test", "ACTIVE", "Test original party", 3, 2, true);
        view = new IncidentReporterView(context, window, 7, true, view.label(), view.races(), List.of(own));
        when(reads.load(actor, report)).thenReturn(new IncidentReporterView.CaseDetail(own, payload));
    }
    void command() { dispatch(PickGatewayInteraction.Kind.COMMAND, "incidents", Map.of()); }
    void complete(Map<String,List<String>> fields) { command(); click("New report"); click("Enter report"); assertThat(response.modal).isNotNull(); dispatch(PickGatewayInteraction.Kind.MODAL, response.modal.id(), fields); }
    void select(UUID id) { var control = last().selection(); assertThat(control).isNotNull(); dispatch(PickGatewayInteraction.Kind.SELECT, control.id(), Map.of("selection", List.of(id.toString()))); }
    void click(String label) { dispatch(PickGatewayInteraction.Kind.BUTTON, button(label), Map.of()); }
    String button(String label) { var found = last().buttons().stream().filter(button -> button.label().equals(label)).findFirst().orElse(null); assertThat(found).as(label).isNotNull(); return found.id(); }
    PickGatewayMessage last() { assertThat(response.messages).isNotEmpty(); return response.messages.getLast(); }
    void dispatch(PickGatewayInteraction.Kind kind, String action, Map<String,List<String>> fields) {
        var previous = response; response = new Response(); if (previous != null) { response.modal = previous.modal; response.messages.addAll(previous.messages); response.onPublish = previous.onPublish; }
        var event = new PickGatewayInteraction(UUID.randomUUID().toString(), GUILD, eventChannel, eventUser, kind, action, fields, NOW, response);
        handler.onInteraction(event, new PickGatewayReply(response, clock, NOW, epoch, Runnable::run));
    }
    static Map<String,List<String>> fields(String drivers, String replay, String description, String links) { return Map.of("drivers", List.of(drivers), "replay", List.of(replay), "description", List.of(description), "links", List.of(links)); }
    IncidentReportReceipt receipt(IncidentEventKind action, UUID request) { return new IncidentReportReceipt(report, "INC-test", 1, 1, action, NOW, request); }
    void assertAdmissionReleased() { assertThat(imports.tryLock()).isTrue(); try { org.junit.jupiter.api.Assertions.assertDoesNotThrow(imports::awaitWritersDrained); } finally { imports.unlock(); } }
    static class Response implements PickGatewayResponse, PickGatewayResponse.Continuation {
        boolean deferred; String initial, text; PickGatewayModal modal; final List<PickGatewayMessage> messages = new ArrayList<>();
        java.util.function.Consumer<Object> onPublish = value -> { };
        @Override public java.util.concurrent.CompletionStage<Continuation> deferPrivate() { deferred = true; return CompletableFuture.completedFuture(new Continuation() {
            @Override public void replyPrivate(String value) { text = value; }
            @Override public void replyPrivate(PickGatewayMessage value) { messages.add(value); onPublish.accept(value); }
        }); }
        @Override public void replyPrivate(String value) { initial = value; text = value; }
        @Override public void replyPrivate(PickGatewayMessage value) { messages.add(value); onPublish.accept(value); }
        @Override public void openModal(PickGatewayModal value) { modal = value; onPublish.accept(value); }
    }
}
