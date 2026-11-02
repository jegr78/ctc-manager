package org.ctc.domain.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.picks.*;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.picks.PickChannelPermissionSource;
import org.ctc.domain.repository.TeamPickChannelBindingRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration")
class DiscordPicksWorkflowIT extends PickPersistenceIT {
    static final String GUILD = "100000000000000001", CHANNEL = "200000000000000002";
    @Autowired PickInteractionHandler handler;
    @Autowired PickTeamReadService reader;
    @Autowired PickAccessService access;
    @Autowired PickSessionEpoch epoch;
    @Autowired Clock clock;
    @Autowired SeasonCarPoolService pools;
    @Autowired TeamPickChannelBindingRepository bindings;
    @Autowired DiscordGlobalConfigRepository configs;
    @MockitoBean PickChannelPermissionSource permissions;
    Long configId;
    String originalGuild;
    boolean createdConfig;

    @BeforeEach
    void bindPrivateChannel() {
        // given
        tx.executeWithoutResult(status -> {
            var config = configs.findFirstByOrderByIdAsc(); createdConfig = config == null;
            if (config == null) config = new DiscordGlobalConfig();
            originalGuild = config.getGuildId(); config.setGuildId(GUILD); configId = configs.saveAndFlush(config).getId();
            bindings.saveAndFlush(new TeamPickChannelBinding(seasons.findById(fixture.season().getId()).orElseThrow(),
                    teams.findById(fixture.homeTeam().getId()).orElseThrow(), GUILD, CHANNEL));
        });
        when(permissions.hasCurrentPrivateAccess(GUILD, CHANNEL, "12345")).thenReturn(true);
        when(permissions.hasCurrentPrivateAccess(GUILD, CHANNEL, "12346")).thenReturn(true);
    }

    @AfterEach
    void restoreConfig() {
        tx.executeWithoutResult(status -> {
            if (createdConfig) configs.deleteById(configId);
            else configs.findById(configId).orElseThrow().setGuildId(originalGuild);
        });
    }

    @Test
    void givenTwoTeamDrafts_whenConfirmingReplayingAndViewing_thenRetainOneDurableRevisionAndRejectStaleReplacement() {
        // given
        var first = new Driver("12345"); first.complete(); var confirm = first.button("Confirm submission");
        var stale = new Driver("12346"); stale.complete(); var staleConfirm = stale.button("Confirm submission");
        assertThat(current().payload()).isNull();
        // when
        first.dispatch(PickGatewayInteraction.Kind.BUTTON, confirm, Map.of());
        assertThat(first.response.last().content()).contains("Saved revision 1", fixture.matchday().getLabel(), "Leg 1");
        first.dispatch(PickGatewayInteraction.Kind.BUTTON, confirm, Map.of());
        stale.dispatch(PickGatewayInteraction.Kind.BUTTON, staleConfirm, Map.of());
        // then
        assertThat(stale.response.allText()).contains("reload the current submission").doesNotContain("Saved revision");
        assertThat(current().payload()).isEqualTo(payload);
        assertThat(current().receipt().revisionNumber()).isEqualTo(1);
        tx.executeWithoutResult(status -> assertThat(revisions.findAll().stream().filter(r -> r.getPick().getRace().getId().equals(fixture.race().getId()))).hasSize(1));
        var member = new Driver("12346"); member.command(); member.select(fixture.race().getId());
        assertThat(member.response.allText()).contains("revision 1", "12345", "Slot 1: S01; Slot 2: R03", "Equal Conditions");
    }

    @Test
    void givenReturnedPickAfterDeadline_whenCompletingCorrection_thenShowReasonAndKeepOpeningWithoutNewDeadline() {
        // given
        var original = new Driver("12345"); original.complete(); original.click("Confirm submission");
        tx.executeWithoutResult(status -> {
            var race = entityManager.find(Race.class, fixture.race().getId());
            race.getMatchday().setPickDeadline(LocalDateTime.of(2020, 1, 1, 12, 0));
        });
        reviews.returnForCorrection(fixture.race().getId(), 1, new ManagementPickActor("test-admin"), "Check weather slot order");
        var corrected = new Driver("12346"); corrected.command(); corrected.select(fixture.race().getId());
        assertThat(corrected.response.allText()).contains("RETURNED", "Check weather slot order", "no new deadline", "2020-01-01T12:00");
        // when
        corrected.complete(); corrected.click("Confirm submission");
        // then
        assertThat(current().receipt().revisionNumber()).isEqualTo(2);
        assertThat(current().receipt().aggregateVersion()).isEqualTo(3);
        assertThat(current().manualOpen()).isTrue();
        assertThat(current().target().state()).isEqualTo(PickState.SUBMITTED);
    }

    @Test
    void givenRevokedMemberOrRestoredDatabase_whenConfirmingCompleteDraft_thenSaveNothing() {
        // given
        var revoked = new Driver("12345"); revoked.complete(); var revokedConfirm = revoked.button("Confirm submission");
        when(permissions.hasCurrentPrivateAccess(GUILD, CHANNEL, "12345")).thenReturn(false);
        // when
        revoked.dispatch(PickGatewayInteraction.Kind.BUTTON, revokedConfirm, Map.of());
        // then
        assertThat(revoked.response.allText()).contains("unavailable");
        when(permissions.hasCurrentPrivateAccess(GUILD, CHANNEL, "12345")).thenReturn(true);
        var restored = new Driver("12346"); restored.complete(); var restoredConfirm = restored.button("Confirm submission");
        epoch.afterRestore(null);
        restored.dispatch(PickGatewayInteraction.Kind.BUTTON, restoredConfirm, Map.of());
        assertThat(restored.response.allText()).contains("expired");
        assertThat(current().payload()).isNull();
    }

    @Test
    void givenClosedOrMissingDeadline_whenViewing_thenShowMissingStateWithoutOfferingTeamWrites() {
        // given
        tx.executeWithoutResult(status -> entityManager.find(Race.class, fixture.race().getId()).getMatchday().setPickDeadline(null));
        var member = new Driver("12345");
        // when
        member.command(); member.select(fixture.race().getId());
        // then
        assertThat(member.response.allText()).contains("MISSING", "deadline or explicit management opening", "No confirmed submission");
        assertThat(member.response.last().buttons()).extracting(PickGatewayMessage.Button::label).containsExactly("Refresh current submission");
    }

    @Test
    void givenFrozenCategoryPool_whenCompletingWizard_thenKeepExactEntryAndConcreteCarInDurablePayload() {
        // given
        var entry = tx.execute(status -> {
            var car = cars.findById(payload.carId()).orElseThrow();
            car.setCategory(CarCategory.GR_3); car.setDrivetrain(Drivetrain.MR); car.setModelYear(9898);
            car.setModelYearOverride(true); car.setModelYearSource("Test fixture"); car.setModelYearEvidence("Test accepted local year");
            pools.changeMode(fixture.season().getId(), CarPoolMode.CATEGORY_POOL);
            var id = pools.saveEntry(fixture.season().getId(), null, "Test category entry",
                    List.of(new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, Drivetrain.MR, 9898)));
            pools.freeze(fixture.season().getId()); return id;
        });
        var driver = new Driver("12345");
        // when
        driver.complete(); driver.click("Confirm submission");
        // then
        assertThat(current().payload().carPoolEntryId()).isEqualTo(entry);
        assertThat(current().payload().carId()).isEqualTo(payload.carId());
        assertThat(current().receipt().revisionNumber()).isEqualTo(1);
    }

    @Test
    void givenConfirmedPicksAfterRaceDecision_whenViewingAndReplaying_thenRecoverOriginalReceiptWithoutOfferingEdits() {
        // given
        var driver = new Driver("12345"); driver.complete(); var confirm = driver.button("Confirm submission"); driver.click("Confirm submission");
        var saved = current().receipt();
        tx.executeWithoutResult(status -> entityManager.find(Race.class, fixture.race().getId()).getMatch().setHomeScore(0));
        // when
        var member = new Driver("12346"); member.command();
        // then
        assertThat(member.response.messages).hasSize(1);
        member.select(fixture.race().getId());
        assertThat(member.response.allText()).contains("revision 1", "Slot 1: S01; Slot 2: R03");
        assertThat(member.response.last().buttons()).extracting(PickGatewayMessage.Button::label).containsExactly("Refresh current submission");
        driver.dispatch(PickGatewayInteraction.Kind.BUTTON, confirm, Map.of());
        assertThat(driver.response.allText()).contains("Saved revision 1");
        assertThat(current().receipt()).isEqualTo(saved);
    }

    org.ctc.domain.picks.PickTeamView current() {
        return reader.load(access.authorizeChannel(GUILD, CHANNEL, "12345"), fixture.race().getId());
    }
    final class Driver {
        final String user;
        Reply response;
        Driver(String user) { this.user = user; }
        void complete() {
            command(); select(fixture.race().getId());
            click(response.last().buttons().stream().anyMatch(b -> b.label().equals("Replace picks")) ? "Replace picks" : "Enter picks");
            select(payload.trackId()); select(payload.carId());
            click("Open rules form"); modal(fields("laps", "12", "wear", "2", "consumption", "3", "refuel", "4"));
            click("Open fuel form"); var values = new HashMap<>(fields("initial", "DEFAULT", "stops", "1", "mandatory", "RM"));
            values.put("available", List.of("RS", "RM")); modal(values);
            click("Open time form"); modal(fields("progression", "equal", "weather", payload.weather(), "daytime", payload.timeOfDay()));
        }
        void command() { dispatch(PickGatewayInteraction.Kind.COMMAND, "picks", Map.of()); }
        void select(UUID value) { dispatch(PickGatewayInteraction.Kind.SELECT, response.last().selection().id(), fields("selection", value.toString())); }
        String button(String label) { return response.last().buttons().stream().filter(b -> b.label().equals(label)).findFirst().orElseThrow().id(); }
        void click(String label) { dispatch(PickGatewayInteraction.Kind.BUTTON, button(label), Map.of()); }
        void modal(Map<String, List<String>> values) { dispatch(PickGatewayInteraction.Kind.MODAL, response.modal.id(), values); }
        void dispatch(PickGatewayInteraction.Kind kind, String action, Map<String, List<String>> values) {
            response = new Reply();
            var event = new PickGatewayInteraction("1000", GUILD, CHANNEL, user, kind, action, values, clock.instant(), response);
            handler.onInteraction(event, new PickGatewayReply(response, clock, clock.instant(), epoch, Runnable::run));
        }
    }
    static Map<String, List<String>> fields(String... pairs) {
        var values = new HashMap<String, List<String>>();
        for (int i = 0; i < pairs.length; i += 2) values.put(pairs[i], List.of(pairs[i + 1]));
        return values;
    }
    static final class Reply implements PickGatewayResponse, PickGatewayResponse.Continuation {
        final List<String> text = new ArrayList<>(); final List<PickGatewayMessage> messages = new ArrayList<>(); PickGatewayModal modal;
        @Override public java.util.concurrent.CompletionStage<Continuation> deferPrivate() { return CompletableFuture.completedFuture(this); }
        @Override public void replyPrivate(String message) { text.add(message); }
        @Override public void replyPrivate(PickGatewayMessage message) { messages.add(message); }
        @Override public void openModal(PickGatewayModal value) { modal = value; }
        PickGatewayMessage last() { return messages.getLast(); }
        String allText() { return String.join("\n", text) + messages.stream().map(PickGatewayMessage::content).collect(java.util.stream.Collectors.joining("\n")); }
    }
}
