package org.ctc.domain.service;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.picks.*;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.discord.service.DiscordPostService;
import org.ctc.admin.service.TeamCardService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.picks.PickChannelPermissionSource;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.TeamPickChannelBindingRepository;
import org.ctc.sitegen.SiteGeneratorService;
import org.ctc.sitegen.YouTubeScraperService;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration")
class DiscordPicksWorkflowIT extends PickPersistenceIT {
    static final String GUILD = "100000000000000001", CHANNEL = "200000000000000002";
    static final Path ROOT = SitegenTestDir.create("discord-picks-acceptance");
    static final String WEBHOOK = "/api/v10/webhooks/300000000000000003/test-picks-rollout";
    static final String PUBLIC_CHANNEL = "400000000000000004";
    @RegisterExtension static WireMockExtension wm = WireMockExtension.newInstance()
            .options(com.github.tomakehurst.wiremock.core.WireMockConfiguration.options().dynamicPort()).build();
    @DynamicPropertySource static void offlineConfiguration(DynamicPropertyRegistry properties) {
        properties.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10");
        properties.add("app.discord.bot-token", () -> "test-bot-token");
        properties.add("app.discord.allowed-hosts", () -> "discord.com,localhost,127.0.0.1");
        properties.add("app.discord.rate-limit.jitter-ms", () -> "0");
        properties.add("app.upload-dir", () -> ROOT.resolve("uploads").toString());
        properties.add("ctc.site.output-dir", () -> ROOT.resolve("site").toString());
    }
    @Autowired PickInteractionHandler handler;
    @Autowired PickTeamReadService reader;
    @Autowired PickAccessService access;
    @Autowired PickSessionEpoch epoch;
    @Autowired Clock clock;
    @Autowired SeasonCarPoolService pools;
    @Autowired TeamPickChannelBindingRepository bindings;
    @Autowired DiscordGlobalConfigRepository configs;
    @Autowired MatchRepository matches;
    @Autowired RaceRepository races;
    @Autowired PhaseTeamRepository phaseTeams;
    @Autowired DiscordPostRepository postRecords;
    @Autowired DiscordPostService posts;
    @Autowired PickDisclosureService disclosure;
    @Autowired SiteGeneratorService generator;
    @Autowired TeamCardService cards;
    @MockitoBean YouTubeScraperService youtube;
    @MockitoBean PickChannelPermissionSource permissions;
    Long configId;
    String originalGuild;
    boolean createdConfig;
    UUID extraTrack;

    @BeforeEach
    void bindPrivateChannel() {
        // given
        wm.resetAll();
        payload = new PickPayload(payload.trackId(), payload.carId(), payload.carPoolEntryId(), payload.laps(), payload.tyreWear(),
                payload.fuelConsumption(), payload.refuelingSpeed(), payload.initialFuel(), payload.requiredPitStops(),
                payload.timeProgressionMode(), payload.timeProgressionMultiplier(), payload.weather(), payload.timeOfDay(),
                Set.of(PickTyre.RS, PickTyre.RM, PickTyre.I, PickTyre.W), payload.mandatoryTyres());
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
            postRecords.deleteAll(postRecords.findAll().stream().filter(row -> fixture.match().getId().equals(row.getMatchId())).toList());
            postRecords.flush();
            phaseTeams.deleteAll(phaseTeams.findByPhaseId(fixture.matchday().getPhase().getId()));
            phaseTeams.flush();
            if (extraTrack != null) {
                seasons.findById(fixture.season().getId()).orElseThrow().getTracks().removeIf(track -> track.getId().equals(extraTrack));
                entityManager.flush();
                tracks.deleteById(extraTrack);
            }
            if (createdConfig) configs.deleteById(configId);
            else configs.findById(configId).orElseThrow().setGuildId(originalGuild);
        });
    }

    @Test
    void givenMatchDeadlineOverride_whenLoadingPrivateView_thenUseStoredEffectiveCutoff() {
        // given
        tx.executeWithoutResult(status -> matches.findById(fixture.match().getId()).orElseThrow()
                .setPickDeadlineOverride(LocalDateTime.of(2035, 7, 1, 20, 30)));
        // when
        var view = current();
        // then
        assertThat(view.deadlineAt()).isEqualTo(java.time.Instant.parse("2035-07-01T18:30:00Z"));
        assertThat(view.deadline()).contains("2035-07-01T20:30", "+02:00");
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
    void givenReturnedPickAfterDeadline_whenAllowingAMatchException_thenShowReasonAndUseItsExtendedCutoff() {
        // given
        var original = new Driver("12345"); original.complete(); original.click("Confirm submission");
        tx.executeWithoutResult(status -> {
            var race = entityManager.find(Race.class, fixture.race().getId());
            race.getMatchday().setPickDeadline(LocalDateTime.of(2020, 1, 1, 12, 0));
        });
        reviews.returnForCorrection(fixture.race().getId(), 1, new ManagementPickActor("test-admin"), "Check weather slot order");
        var corrected = new Driver("12346"); corrected.command(); corrected.select(fixture.race().getId());
        assertThat(corrected.response.allText()).contains("RETURNED", "Check weather slot order", "deadline has passed", "Deadline: <t:1577876400:F> · <t:1577876400:R>");
        assertThat(corrected.response.last().buttons()).extracting(PickGatewayMessage.Button::label).doesNotContain("Replace picks");
        tx.executeWithoutResult(status -> entityManager.find(Race.class, fixture.race().getId()).getMatch().setPickDeadlineOverride(LocalDateTime.of(2035, 1, 1, 12, 0)));
        // when
        corrected.complete(); corrected.click("Confirm submission");
        // then
        assertThat(current().receipt().revisionNumber()).isEqualTo(2);
        assertThat(current().receipt().aggregateVersion()).isEqualTo(3);
        assertThat(current().matchdayOpen()).isTrue();
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
    void givenClosedMatchday_whenOpeningSelector_thenOfferNoHomeRacesEvenWithoutACutoff() {
        // given
        tx.executeWithoutResult(status -> {
            var day = entityManager.find(Race.class, fixture.race().getId()).getMatchday(); day.setPickDeadline(null); day.setPicksOpen(false);
        });
        var member = new Driver("12345");
        member.command();
        assertThat(current().target().editable()).isFalse();
        assertThat(member.response.allText()).contains("No eligible home races");
        assertThat(member.response.messages).isEmpty();
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
        var saved = current().payload();
        UUID other = tx.execute(status -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            var track = tracks.save(new Track("Test category second track " + UUID.randomUUID()));
            extraTrack = track.getId(); season.getTracks().add(track);
            var race = helper.createRace(fixture.matchday(), matches.findById(fixture.match().getId()).orElseThrow());
            race.setDateTime(LocalDateTime.of(2035, 1, 1, 21, 0)); race.setPicksManaged(true);
            return race.getId();
        });
        var duplicate = new PickPayload(extraTrack, saved.carId(), entry, saved.laps(), saved.tyreWear(), saved.fuelConsumption(),
                saved.refuelingSpeed(), saved.initialFuel(), saved.requiredPitStops(), saved.timeProgressionMode(), saved.timeProgressionMultiplier(),
                saved.weather(), saved.timeOfDay(), saved.availableTyres(), saved.mandatoryTyres());
        var authorized = access.authorizeTarget(access.authorizeChannel(GUILD, CHANNEL, "12345"), other);
        assertThatThrownBy(() -> submissions.submit(other, 0, UUID.randomUUID(), authorized, duplicate))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already used Test category entry");
        reviews.approve(fixture.race().getId(), 1, 1, new ManagementPickActor("test-admin"), "Test category setup approved");
        tx.executeWithoutResult(status -> {
            var race = races.findById(fixture.race().getId()).orElseThrow();
            assertThat(race.getCarPoolEntry().getId()).isEqualTo(entry);
            assertThat(race.getCar().getId()).isEqualTo(saved.carId());
        });
        assertThatThrownBy(() -> submissions.submit(other, 0, UUID.randomUUID(), authorized, duplicate))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already used Test category entry");
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

    @Test
    void givenPrivateWizardAndApproval_whenPublishingExplicitly_thenReleaseOnlyTheCapturedOutputWithCollectionStopped() throws Exception {
        payload = withTime(TimeProgressionMode.EQUAL_CONDITIONS, null, "Test private initial " + UUID.randomUUID());
        tx.executeWithoutResult(status -> {
            phaseTeams.saveAll(List.of(new PhaseTeam(fixture.matchday().getPhase(), fixture.homeTeam()),
                    new PhaseTeam(fixture.matchday().getPhase(), fixture.awayTeam())));
            var match = matches.findById(fixture.match().getId()).orElseThrow();
            match.setDiscordChannelId(PUBLIC_CHANNEL); match.setDiscordChannelWebhookUrl(wm.baseUrl() + WEBHOOK);
        });
        String carName = tx.execute(status -> cars.findById(payload.carId()).orElseThrow().getDisplayName());
        String trackName = tx.execute(status -> tracks.findById(payload.trackId()).orElseThrow().getName());
        tx.executeWithoutResult(status -> assertThatCode(() -> cards.generateAllCards(seasons.findById(fixture.season().getId()).orElseThrow())).doesNotThrowAnyException());
        var member = new Driver("12345"); member.complete(); member.click("Confirm submission");
        assertThat(generateSiteText()).doesNotContain(carName, trackName, payload.weather());
        reviews.approve(fixture.race().getId(), 1, 1, new ManagementPickActor("test-admin"), "Test full workflow approval");
        var teammate = new Driver("12346"); teammate.command(); teammate.select(fixture.race().getId());
        assertThat(teammate.response.allText()).contains("APPROVED", payload.weather());
        assertThat(teammate.response.last().buttons()).extracting(PickGatewayMessage.Button::label).doesNotContain("Enter picks", "Replace picks");
        when(collection.isAvailable()).thenReturn(false);
        tx.executeWithoutResult(status -> seasons.findById(fixture.season().getId()).orElseThrow().setPicksCollectionEnabled(false));
        assertThat(generateSiteText()).doesNotContain(carName, trackName, payload.weather());
        assertThat(wm.getAllServeEvents()).isEmpty();

        stubPublication("500000000000000005");
        posts.postLobbySettings(publicationMatch());
        assertThat(generateSiteText()).contains(trackName).doesNotContain(carName);
        assertThat(disclosure.forPublic(fixture.race().getId()).settings().weather()).isEqualTo(payload.weather());
        assertThat(disclosure.forPublic(fixture.race().getId()).settings().timeProgressionMode()).isEqualTo(TimeProgressionMode.EQUAL_CONDITIONS);
        var correction = withTime(TimeProgressionMode.MULTIPLIER, 0, "Test private zero correction " + UUID.randomUUID());
        reviews.correct(fixture.race().getId(), 2, new ManagementPickActor("test-admin"), "Test unpublished correction", correction);
        assertThat(generateSiteText()).doesNotContain(carName, correction.weather());
        assertThat(disclosure.forPublic(fixture.race().getId()).settings().weather()).isEqualTo(payload.weather());
        assertThat(disclosure.forPublic(fixture.race().getId()).settings().timeProgressionMode()).isEqualTo(TimeProgressionMode.EQUAL_CONDITIONS);
        stubPublication("500000000000000006");
        posts.postSettings(publicationMatch());

        assertThat(generateSiteText()).contains(carName, trackName);
        var visible = disclosure.forPublic(fixture.race().getId());
        assertThat(visible.settings().timeProgressionMode()).isEqualTo(TimeProgressionMode.MULTIPLIER);
        assertThat(visible.settings().timeProgressionMultiplier()).isZero();
        assertThat(visible.settings().weather()).isEqualTo(correction.weather());
        wm.verify(2, WireMock.postRequestedFor(WireMock.urlPathEqualTo(WEBHOOK)));
        assertThat(wm.getAllServeEvents()).allSatisfy(event -> assertThat(event.getRequest().getBody()).hasSizeGreaterThan(1000));
    }

    @Test
    void givenMissingReturnLegAfterDeadline_whenOpeningAndSubmitting_thenUseItsOwnPrivateTeamChannelWithoutANewDeadline() {
        String awayChannel = "200000000000000003";
        UUID returnLeg = tx.execute(status -> {
            var match = matches.findById(fixture.match().getId()).orElseThrow();
            var race = helper.createRace(match.getMatchday(), match);
            race.setHomeTeamOverride(match.getAwayTeam()); race.setAwayTeamOverride(match.getHomeTeam());
            race.setDateTime(LocalDateTime.of(2035, 1, 1, 21, 0)); race.setPicksManaged(true);
            match.getMatchday().setPickDeadline(LocalDateTime.of(2020, 1, 1, 12, 0));
            bindings.save(new TeamPickChannelBinding(match.getMatchday().getSeason(), match.getAwayTeam(), GUILD, awayChannel));
            return race.getId();
        });
        when(permissions.hasCurrentPrivateAccess(GUILD, awayChannel, "12346")).thenReturn(true);
        var home = new Driver("12345"); home.command(); home.select(returnLeg);
        assertThat(home.response.allText()).contains("unavailable").doesNotContain("Saved revision");
        var away = new Driver("12346", awayChannel, returnLeg); away.command(); away.select(returnLeg);
        assertThat(away.response.allText()).contains("MISSING", "Leg 2", "deadline has passed");
        tx.executeWithoutResult(status -> entityManager.find(Race.class, returnLeg).getMatch().setPickDeadlineOverride(LocalDateTime.of(2035, 1, 1, 12, 0)));
        away.complete(); away.click("Confirm submission");
        var saved = reader.load(access.authorizeChannel(GUILD, awayChannel, "12346"), returnLeg);
        assertThat(saved.receipt().revisionNumber()).isEqualTo(1);
        assertThat(saved.matchdayOpen()).isTrue();
        assertThat(saved.target().label()).contains("Leg 2");
        tx.executeWithoutResult(status -> assertThat(races.findById(returnLeg).orElseThrow().getMatchday().getPickDeadline()).isEqualTo(LocalDateTime.of(2020, 1, 1, 12, 0)));
        away.command(); away.select(returnLeg);
        tx.executeWithoutResult(status -> entityManager.find(Race.class, returnLeg).getMatchday().setPicksOpen(false));
        away.click("Refresh current submission");
        assertThat(away.response.allText()).contains("Matchday picks are closed");
        assertThat(away.response.last().buttons()).extracting(PickGatewayMessage.Button::label).doesNotContain("Enter picks", "Replace picks");
        reviews.approve(returnLeg, 1, 1, new ManagementPickActor("test-admin"), "Test return leg approval");
        assertThat(reader.load(access.authorizeChannel(GUILD, awayChannel, "12346"), returnLeg).target().state()).isEqualTo(PickState.APPROVED);
        assertThat(wm.getAllServeEvents()).isEmpty();
    }

    private PickPayload withTime(TimeProgressionMode mode, Integer multiplier, String weather) {
        return new PickPayload(payload.trackId(), payload.carId(), payload.carPoolEntryId(), payload.laps(), payload.tyreWear(), payload.fuelConsumption(),
                payload.refuelingSpeed(), payload.initialFuel(), payload.requiredPitStops(), mode, multiplier, weather,
                payload.timeOfDay(), payload.availableTyres(), payload.mandatoryTyres());
    }

    private Match publicationMatch() {
        return tx.execute(status -> {
            var match = matches.findById(fixture.match().getId()).orElseThrow();
            match.getRaces().forEach(race -> { race.getSettings().isComplete(); race.getCar().getDisplayName(); race.getTrack().getName(); });
            match.getHomeTeam().getShortName(); match.getAwayTeam().getShortName(); match.getMatchday().getSeason().getName();
            return match;
        });
    }

    private void stubPublication(String message) {
        wm.stubFor(WireMock.post(WireMock.urlPathEqualTo(WEBHOOK))
                .willReturn(WireMock.okJson("{\"id\":\"" + message + "\",\"channel_id\":\"" + PUBLIC_CHANNEL + "\",\"flags\":0}")));
    }

    private String generateSiteText() throws Exception {
        var result = generator.generate();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getPagesGenerated()).isPositive();
        var text = new StringBuilder();
        try (var files = Files.walk(ROOT.resolve("site"))) {
            for (var file : files.filter(Files::isRegularFile).toList()) text.append(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        }
        return text.toString();
    }

    org.ctc.domain.picks.PickTeamView current() {
        return reader.load(access.authorizeChannel(GUILD, CHANNEL, "12345"), fixture.race().getId());
    }
    final class Driver {
        final String user;
        final String channel;
        final UUID target;
        Reply response;
        Driver(String user) { this(user, CHANNEL, fixture.race().getId()); }
        Driver(String user, String channel, UUID target) { this.user = user; this.channel = channel; this.target = target; }
        void complete() {
            command(); select(target);
            click(response.last().buttons().stream().anyMatch(b -> b.label().equals("Replace picks")) ? "Replace picks" : "Enter picks");
            select(payload.trackId());
            var chosen = reader.load(access.authorizeChannel(GUILD, channel, user), target).cars().stream()
                    .filter(choice -> choice.id().equals(payload.carId())).findFirst().orElseThrow();
            if (chosen.entryId() != null) select(chosen.entryId());
            if (!chosen.oneMake()) select(payload.carId());
            click("Open rules form"); modal(fields("laps", "12", "wear", "2", "consumption", "3", "refuel", "4"));
            click("Open race strategy form"); var values = new HashMap<>(fields("initial", "DEFAULT", "stops", "1"));
            values.put("available", List.of("RS", "RM")); modal(values);
            click("Open mandatory form"); modal(fields("mandatory", "RM"));
            click("Open time form"); modal(fields("progression", "equal", "weather", payload.weather(), "daytime", payload.timeOfDay()));
        }
        void command() { dispatch(PickGatewayInteraction.Kind.COMMAND, "picks", Map.of()); }
        void select(UUID value) { dispatch(PickGatewayInteraction.Kind.SELECT, response.last().selection().id(), fields("selection", value.toString())); }
        String button(String label) { return response.last().buttons().stream().filter(b -> b.label().equals(label)).findFirst().orElseThrow().id(); }
        void click(String label) { dispatch(PickGatewayInteraction.Kind.BUTTON, button(label), Map.of()); }
        void modal(Map<String, List<String>> values) { dispatch(PickGatewayInteraction.Kind.MODAL, response.modal.id(), values); }
        void dispatch(PickGatewayInteraction.Kind kind, String action, Map<String, List<String>> values) {
            response = new Reply();
            var event = new PickGatewayInteraction("1000", GUILD, channel, user, kind, action, values, clock.instant(), response);
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
