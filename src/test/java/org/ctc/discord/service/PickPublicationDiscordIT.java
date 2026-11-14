package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.admin.service.SettingsGraphicService;
import org.ctc.admin.service.LobbySettingsGraphicService;
import org.ctc.admin.service.LineupGraphicService;
import org.ctc.discord.DiscordWebhookClient;
import org.ctc.discord.dto.WebhookPayload;
import org.ctc.discord.exception.DiscordAuthException;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.discord.exception.DiscordTransientException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.AuthorizedPickActor;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.picks.RenderedPickGraphic;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@CtcDevSpringBootContext
@Tag("integration")
class PickPublicationDiscordIT {
    static final String WEBHOOK = "/api/v10/webhooks/123456789012345678/Test_production-token";
    static final String MESSAGE = "234567890123456789";
    static final String CHANNEL = "345678901234567890";
    static final Path ROOT = SitegenTestDir.create("pick-publication-discord");
    @RegisterExtension static WireMockExtension wm = WireMockExtension.newInstance().options(options().dynamicPort()).build();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10");
        properties.add("app.discord.allowed-hosts", () -> "discord.com,localhost,127.0.0.1");
        properties.add("app.discord.attachment-hosts", () -> "cdn.discordapp.com,localhost,127.0.0.1");
        properties.add("app.discord.rate-limit.jitter-ms", () -> "0");
        properties.add("app.discord.rate-limit.fivexx-backoff-ms", () -> "10,10,10");
        properties.add("app.upload-dir", ROOT::toString);
    }
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired CarRepository cars;
    @Autowired TrackRepository tracks;
    @Autowired DriverRepository drivers;
    @Autowired RaceLineupRepository lineups;
    @Autowired MatchRepository matches;
    @Autowired RaceRepository races;
    @Autowired PickSubmissionService submissions;
    @Autowired PickReviewService reviews;
    @Autowired org.ctc.HistoricalPickCorrections corrections;
    @Autowired PickDisclosureService disclosure;
    @Autowired PickPublicationAttemptRepository attempts;
    @Autowired PickPublicationSnapshotRepository snapshots;
    @Autowired DiscordPostService posts;
    @Autowired PickPublicationService publications;
    @Autowired PickSessionEpoch epoch;
    @Autowired DiscordPickReconciliationService reconciliation;
    @Autowired DiscordGlobalConfigService globalConfig;
    @MockitoSpyBean DiscordPostRepository postRecords;
    @MockitoSpyBean DiscordWebhookClient webhooks;
    @MockitoBean SettingsGraphicService settingsGraphics;
    @MockitoBean LobbySettingsGraphicService lobbyGraphics;
    @MockitoBean LineupGraphicService lineupGraphics;
    TestHelper.SeasonFixture fixture;
    PickPayload payload;
    UUID driverId;
    String previousAnnouncement;

    @BeforeEach
    void setUp() throws Exception {
        // given
        wm.resetAll(); Files.createDirectories(ROOT);
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("TPD" + UUID.randomUUID().toString().substring(0, 7));
            
             fixture.race().setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0));
            fixture.matchday().setPickDeadline(LocalDateTime.of(2035, 1, 1, 12, 0));
            fixture.matchday().setPicksOpen(true);
            fixture.match().setDiscordChannelId(CHANNEL); fixture.match().setDiscordChannelWebhookUrl(wm.baseUrl() + WEBHOOK);
            fixture.match().setDiscordTeaser("Test explicit preview");
            var car = cars.save(new Car("Test private publication", UUID.randomUUID().toString()));
            var track = tracks.save(new Track("Test private publication " + UUID.randomUUID()));
            fixture.season().getCars().add(car); fixture.season().getTracks().add(track);
            payload = new PickPayload(track.getId(), car.getId(), null, 12, 2, 3, 4, PickInitialFuel.DEFAULT, 1,
                    TimeProgressionMode.EQUAL_CONDITIONS, null, "S01, R03, S02", "Sunset", Set.of(PickTyre.RS), Set.of());
            fixture.race().setCar(car); fixture.race().setTrack(track);
            var settings = new RaceSettings(fixture.race()); RaceSettingsValues.from(payload).applyTo(settings); fixture.race().setSettings(settings);
            var driver = helper.createDriver("Test-Pub-" + UUID.randomUUID(), "Test publication driver"); driverId = driver.getId();
            var lineup = new RaceLineup(); lineup.setRace(fixture.race()); lineup.setDriver(driver); lineup.setTeam(fixture.homeTeam()); lineups.save(lineup);
            previousAnnouncement = globalConfig.getOrInitialize().getAnnouncementWebhookUrl();
        });
        Files.writeString(ROOT.resolve("test-settings.png"), "Test initially private graphic");
        Files.writeString(ROOT.resolve("test-lineup.png"), "Test safe lineup graphic");
        when(settingsGraphics.generateSettings(any(Race.class))).thenReturn("/uploads/test-settings.png");
        when(lobbyGraphics.generateLobbySettings(any(Race.class))).thenReturn("/uploads/test-settings.png");
        when(lineupGraphics.generateLineup(any(Race.class))).thenReturn("/uploads/test-lineup.png");
        when(settingsGraphics.renderPublication(any(Race.class))).thenAnswer(call -> graphic(call.getArgument(0), false));
        when(lobbyGraphics.renderPublication(any(Race.class))).thenAnswer(call -> graphic(call.getArgument(0), true));
        wm.stubFor(post(urlPathEqualTo(WEBHOOK)).withQueryParam("wait", equalTo("true"))
                .willReturn(okJson("{\"id\":\"" + MESSAGE + "\",\"channel_id\":\"" + CHANNEL + "\"}")));
        wm.stubFor(patch(urlPathEqualTo(WEBHOOK + "/messages/" + MESSAGE))
                .willReturn(okJson("{\"id\":\"" + MESSAGE + "\",\"channel_id\":\"" + CHANNEL + "\"}")));
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            postRecords.deleteAll(postRecords.findAll().stream().filter(row -> fixture.match().getId().equals(row.getMatchId())).toList());
            postRecords.flush();
            globalConfig.getOrInitialize().setAnnouncementWebhookUrl(previousAnnouncement);
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow()); seasons.flush();
            drivers.deleteById(driverId); cars.deleteById(payload.carId()); tracks.deleteById(payload.trackId());
            teams.deleteAllById(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
        });
    }

    @Test
    void givenManagedSettingsWithoutApproval_whenPosting_thenRejectBeforeWebhookDelivery() {
        // when / then
        assertThatThrownBy(() -> posts.postSettings(match())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("approved");
        wm.verify(0, postRequestedFor(urlPathEqualTo(WEBHOOK)));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {64, 128})
    void givenEphemeralOrUnfinishedWebhookResponse_whenPublishing_thenRetainUncertaintyWithoutPublicRelease(int flags) {
        // given
        approve();
        wm.stubFor(post(urlPathEqualTo(WEBHOOK)).withQueryParam("wait", equalTo("true"))
                .willReturn(okJson("{\"id\":\"" + MESSAGE + "\",\"channel_id\":\"" + CHANNEL + "\",\"flags\":" + flags + "}")));
        // when / then
        assertThatThrownBy(() -> posts.postSettings(match())).isInstanceOf(DiscordTransientException.class).hasMessageContaining("reconcile");
        assertThat(disclosure.forPublic(fixture.race().getId()).carName()).isNull();
        tx.executeWithoutResult(status -> assertThat(attempts.findByActiveRaceId(fixture.race().getId())).isPresent());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"SETTINGS,false", "SETTINGS,true", "LOBBY_SETTINGS,false", "LOBBY_SETTINGS,true"})
    void givenMissingOrDifferentResponseChannel_whenPublishing_thenKeepTheOutputUncertainWithoutConfirmingItsDestination(PickPublicationOutput output, boolean missing) {
        // given
        approve();
        var response = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("id", MESSAGE).put("flags", 0);
        if (!missing) response.put("channel_id", "456789012345678901");
        wm.stubFor(post(urlPathEqualTo(WEBHOOK)).withQueryParam("wait", equalTo("true")).willReturn(okJson(response.toString())));
        // when / then
        assertThatThrownBy(() -> {
            if (output == PickPublicationOutput.SETTINGS) posts.postSettings(match()); else posts.postLobbySettings(match());
        }).isInstanceOf(DiscordTransientException.class).hasMessageContaining("reconcile");
        assertThat(disclosure.forPublic(fixture.race().getId()).settings()).isNull();
        assertThat(disclosure.forPublic(fixture.race().getId()).carName()).isNull();
        tx.executeWithoutResult(status -> {
            assertThat(attempts.findByActiveRaceId(fixture.race().getId())).isPresent().get()
                    .extracting(PickPublicationAttempt::getState).isEqualTo(PickPublicationState.UNCERTAIN);
            assertThat(snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(fixture.race().getId())).isEmpty();
            assertThat(postRecords.findAll().stream().filter(row -> fixture.match().getId().equals(row.getMatchId()))).isEmpty();
        });
        wm.verify(1, postRequestedFor(urlPathEqualTo(WEBHOOK)).withQueryParam("wait", equalTo("true")));
    }

    @Test
    void givenApprovedSettingsWithCollectionDisabled_whenPosting_thenSendFrozenBytesOutsideSqlAndConfirmTheirPublicVersion() {
        // given
        approve(); outsideNetwork();
        // when
        var result = assertDoesNotThrow(() -> posts.postSettings(match()));
        // then
        assertThat(result.getMessageId()).isEqualTo(MESSAGE);
        assertThat(disclosure.forPublic(fixture.race().getId()).settings()).isNotNull();
        assertThat(disclosure.forPublic(fixture.race().getId()).settings().weather()).isEqualTo(payload.weather());
        assertThat(disclosure.forPublic(fixture.race().getId()).carName()).contains("Test private publication");
        assertThat(wm.getAllServeEvents().getFirst().getRequest().getBodyAsString()).contains(payload.weather(), "settings-race-1.png");
        tx.executeWithoutResult(status -> assertThat(snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(fixture.race().getId())).hasSize(1));
    }

    @Test
    void givenApprovedLobbyOutput_whenPosting_thenKeepTheCarWithheldOnPublicReads() {
        // given
        approve();
        // when
        assertDoesNotThrow(() -> posts.postLobbySettings(match()));
        // then
        var projection = disclosure.forPublic(fixture.race().getId());
        assertThat(projection.trackName()).isNotNull(); assertThat(projection.carName()).isNull(); assertThat(projection.poolEntryLabel()).isNull();
        assertThat(projection.settings().weather()).isEqualTo(payload.weather());
    }

    @Test
    void givenApprovedCustomOutputWithoutProvenCoverage_whenPublishing_thenKeepWebsiteFieldsWithheld() throws Exception {
        // given
        approve(); when(settingsGraphics.renderPublication(any(Race.class))).thenAnswer(call -> {
            var captured = graphic(call.getArgument(0), false);
            return new RenderedPickGraphic(captured.png(), captured.templateHash(), Set.of());
        });
        // when
        assertDoesNotThrow(() -> posts.postSettings(match()));
        // then
        assertThat(disclosure.forPublic(fixture.race().getId()).carName()).isNull();
        tx.executeWithoutResult(status -> {
            assertThat(attempts.findByActiveRaceId(fixture.race().getId())).isEmpty();
            assertThat(attempts.findFirstByRaceIdOrderByContentVersionDescCapturedAtDesc(fixture.race().getId()).orElseThrow().getState()).isEqualTo(PickPublicationState.SUCCEEDED);
            assertThat(snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(fixture.race().getId())).isEmpty();
        });
        wm.verify(1, postRequestedFor(urlPathEqualTo(WEBHOOK)).withQueryParam("wait", equalTo("true")));
    }

    @Test
    void givenApprovedExplicitPreview_whenPosting_thenConfirmOnlyItsCapturedSettings() {
        // given
        approve(); tx.executeWithoutResult(status -> globalConfig.getOrInitialize().setAnnouncementWebhookUrl(wm.baseUrl() + WEBHOOK));
        // when
        assertDoesNotThrow(() -> posts.postMatchPreview(match()));
        // then
        assertThat(disclosure.forPublic(fixture.race().getId()).carName()).contains("Test private publication");
        assertThat(wm.getAllServeEvents().getFirst().getRequest().getBodyAsString()).contains("settings-md1.png", "lineups-md1.png");
    }

    @Test
    void givenSeveralPairingsPublishedInSequence_whenALaterPhysicalBundleFails_thenKeepOnlyTheEarlierBundlePublic() {
        // given
        approve();
        UUID secondRaceId = tx.execute(status -> {
            var day = races.findById(fixture.race().getId()).orElseThrow().getMatchday();
            var secondMatch = helper.createMatch(day, teams.findById(fixture.awayTeam().getId()).orElseThrow(), teams.findById(fixture.homeTeam().getId()).orElseThrow());
            secondMatch.setDiscordChannelId(CHANNEL); secondMatch.setDiscordChannelWebhookUrl(wm.baseUrl() + WEBHOOK);
            var second = helper.createRace(day, secondMatch); second.setDateTime(LocalDateTime.of(2035, 1, 1, 21, 0)); 
            return second.getId();
        });
        submissions.submit(secondRaceId, 0, UUID.randomUUID(), new AuthorizedPickActor(secondRaceId, fixture.season().getId(), fixture.awayTeam().getId(), "12345"), payload);
        reviews.approve(secondRaceId, 1, 1, new ManagementPickActor("test-admin"), "Test second pairing approved");
        var secondMatch = tx.execute(status -> {
            var second = races.findById(secondRaceId).orElseThrow(); var match = second.getMatch();
            match.getRaces().forEach(race -> { race.getSettings().isComplete(); race.getCar().getDisplayName(); race.getTrack().getName(); });
            match.getHomeTeam().getShortName(); match.getAwayTeam().getShortName(); match.getMatchday().getSeason().getName(); return match;
        });
        // when
        assertDoesNotThrow(() -> posts.postSettings(match()));
        wm.stubFor(post(urlPathEqualTo(WEBHOOK)).withQueryParam("wait", equalTo("true")).willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json").withBody("{\"message\":\"Unauthorized\",\"code\":0}")));
        assertThatThrownBy(() -> posts.postSettings(secondMatch)).isInstanceOf(DiscordAuthException.class);
        // then
        assertThat(disclosure.forPublic(fixture.race().getId()).carName()).isNotNull();
        assertThat(disclosure.forPublic(secondRaceId).carName()).isNull();
        tx.executeWithoutResult(status -> {
            assertThat(attempts.findFirstByRaceIdOrderByContentVersionDescCapturedAtDesc(fixture.race().getId()).orElseThrow().getState()).isEqualTo(PickPublicationState.SUCCEEDED);
            assertThat(attempts.findFirstByRaceIdOrderByContentVersionDescCapturedAtDesc(secondRaceId).orElseThrow().getState()).isEqualTo(PickPublicationState.FAILED);
        });
    }

    @Test
    void givenConcurrentManagementCorrection_whenPosting_thenSendAndConfirmOnlyThePreNetworkCapture() throws Exception {
        // given
        approve();
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            corrections.record(fixture.race().getId(), 2, new ManagementPickActor("test-admin"), "Test correction while sending", corrected());
            return call.callRealMethod();
        }).when(webhooks).executeMultipart(anyString(), any(WebhookPayload.class), anyList(), isNull());
        // when
        assertDoesNotThrow(() -> posts.postSettings(match()));
        // then
        assertThat(disclosure.forPublic(fixture.race().getId()).settings()).isNotNull();
        assertThat(disclosure.forPublic(fixture.race().getId()).settings().weather()).isEqualTo(payload.weather());
        assertThat(wm.getAllServeEvents().getFirst().getRequest().getBodyAsString()).contains(payload.weather()).doesNotContain(corrected().weather());
        tx.executeWithoutResult(status -> assertThat(races.findById(fixture.race().getId()).orElseThrow().getSettings().getWeather()).isEqualTo(corrected().weather()));
    }

    @Test
    void givenRemoteSuccessAndFailedPostMapping_whenPosting_thenRetainUncertaintyWithoutPublicRelease() {
        // given
        approve(); doThrow(new DataIntegrityViolationException("Test local confirmation failure")).when(postRecords).saveAndFlush(any());
        // when / then
        assertThatThrownBy(() -> posts.postSettings(match())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("reconciliation");
        tx.executeWithoutResult(status -> assertThat(attempts.findByActiveRaceId(fixture.race().getId())).isPresent()
                .get().extracting(PickPublicationAttempt::getState).isEqualTo(PickPublicationState.UNCERTAIN));
        assertThat(disclosure.forPublic(fixture.race().getId()).carName()).isNull();
        wm.verify(1, postRequestedFor(urlPathEqualTo(WEBHOOK)).withQueryParam("wait", equalTo("true")));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void givenUncertainDeliveryAndLaterInternalCorrection_whenCheckingTheActualMessage_thenReleaseOnlyExactVerifiedBytes(boolean tampered) throws Exception {
        // given
        approve(); byte[] capturedImage = graphic(match().getRaces().getFirst(), false).png();
        doThrow(new DataIntegrityViolationException("Test local confirmation failed")).when(postRecords).saveAndFlush(any());
        assertThatThrownBy(() -> posts.postSettings(match())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("reconciliation");
        reset(postRecords);
        UUID batch = tx.execute(status -> attempts.findByActiveRaceId(fixture.race().getId()).orElseThrow().getBatchId());
        corrections.record(fixture.race().getId(), 2, new ManagementPickActor("test-admin"), "Test newer setup remains private during reconciliation", corrected());
        String path = "/attachments/" + CHANNEL + "/456789012345678901/settings-race-1.png";
        String imageUrl = wm.baseUrl() + path + "?ex=abc&hm=Test_verification-signature";
        wm.stubFor(get(urlPathEqualTo(WEBHOOK + "/messages/" + MESSAGE)).willReturn(okJson("""
                {"id":"%s","webhook_id":"123456789012345678","channel_id":"%s","content":"","flags":0,
                 "attachments":[{"id":"456789012345678901","filename":"settings-race-1.png","size":%d,"url":"%s"}]}
                """.formatted(MESSAGE, CHANNEL, capturedImage.length, imageUrl))));
        byte[] remoteImage = capturedImage.clone(); if (tampered) remoteImage[0] ^= 1;
        wm.stubFor(get(urlPathEqualTo(path)).withQueryParam("hm", equalTo("Test_verification-signature")).willReturn(aResponse().withBody(remoteImage)));
        // when / then
        org.assertj.core.api.ThrowableAssert.ThrowingCallable verify = () -> publications.reconcile(batch, publications.reconciliationFingerprint(batch),
                "Test checked exact stored output", new ManagementPickActor("test-reviewer", epoch.managementGeneration()), rows -> reconciliation.prepare(rows, MESSAGE));
        if (tampered) {
            assertThatThrownBy(verify).isInstanceOf(BusinessRuleException.class).hasMessageContaining("does not prove");
            assertThat(disclosure.forPublic(fixture.race().getId()).settings()).isNull();
            tx.executeWithoutResult(status -> assertThat(attempts.findByActiveRaceId(fixture.race().getId())).isPresent());
        } else {
            assertThatCode(verify).doesNotThrowAnyException();
            assertThat(disclosure.forPublic(fixture.race().getId()).settings().weather()).isEqualTo(payload.weather());
            tx.executeWithoutResult(status -> {
                assertThat(attempts.findByActiveRaceId(fixture.race().getId())).isEmpty();
                assertThat(races.findById(fixture.race().getId()).orElseThrow().getSettings().getWeather()).isEqualTo(corrected().weather());
            });
        }
        wm.verify(1, postRequestedFor(urlPathEqualTo(WEBHOOK)).withQueryParam("wait", equalTo("true")));
        wm.verify(0, patchRequestedFor(anyUrl()));
    }

    @Test
    void givenUncertainEditOfAnOlderPublicMessage_whenRetiringAndPublishingAgain_thenClearItsEditMappingAndSendAFreshMessage() throws Exception {
        // given
        approve(); posts.postSettings(match()); var previous = disclosure.forPublic(fixture.race().getId());
        corrections.record(fixture.race().getId(), 2, new ManagementPickActor("test-admin"), "Test new internal setup", corrected());
        wm.stubFor(patch(urlPathEqualTo(WEBHOOK + "/messages/" + MESSAGE)).willReturn(aResponse().withStatus(503)));
        assertThatThrownBy(() -> posts.postSettings(match())).isInstanceOf(DiscordTransientException.class).hasMessageContaining("reconcile");
        UUID batch = tx.execute(status -> attempts.findByActiveRaceId(fixture.race().getId()).orElseThrow().getBatchId());
        assertThat(disclosure.forPublic(fixture.race().getId())).isEqualTo(previous);
        // when
        publications.abandon(batch, publications.reconciliationFingerprint(batch), "Test operator retired ambiguous remote output", true,
                new ManagementPickActor("test-reviewer", epoch.managementGeneration()), reconciliation::quarantine);
        tx.executeWithoutResult(status -> assertThat(postRecords.findByChannelIdAndPostTypeAndMatchId(CHANNEL, org.ctc.discord.model.DiscordPostType.SETTINGS, fixture.match().getId())).isEmpty());
        wm.stubFor(post(urlPathEqualTo(WEBHOOK)).withQueryParam("wait", equalTo("true"))
                .willReturn(okJson("{\"id\":\"234567890123456790\",\"channel_id\":\"" + CHANNEL + "\",\"flags\":0}")));
        var result = posts.postSettings(match());
        // then
        assertThat(result.getMessageId()).isEqualTo("234567890123456790");
        assertThat(disclosure.forPublic(fixture.race().getId()).settings().weather()).isEqualTo(corrected().weather());
        wm.verify(2, postRequestedFor(urlPathEqualTo(WEBHOOK)).withQueryParam("wait", equalTo("true")));
    }

    @Test
    void givenPublishedContentAndRejectedCorrection_whenRetrying_thenPreserveTheEarlierPublicVersion() {
        // given
        approve(); assertDoesNotThrow(() -> posts.postSettings(match()));
        var original = disclosure.forPublic(fixture.race().getId()); assertThat(original.settings()).isNotNull();
        corrections.record(fixture.race().getId(), 2, new ManagementPickActor("test-admin"), "Test unpublished correction", corrected());
        wm.stubFor(patch(urlPathEqualTo(WEBHOOK + "/messages/" + MESSAGE)).willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json").withBody("{\"message\":\"Unauthorized\",\"code\":0}")));
        // when / then
        assertThatThrownBy(() -> posts.postSettings(match())).isInstanceOf(DiscordAuthException.class);
        assertThat(disclosure.forPublic(fixture.race().getId())).isEqualTo(original);
    }

    @Test
    void givenAmbiguousWriteThenRejectedRetry_whenPublishing_thenKeepUncertaintyAndBlockAnotherSend() {
        // given
        approve(); String scenario = "Test publication may have succeeded before rejection";
        wm.stubFor(post(urlPathEqualTo(WEBHOOK)).withQueryParam("wait", equalTo("true"))
                .inScenario(scenario).whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willSetStateTo("retry").willReturn(aResponse().withStatus(503)));
        wm.stubFor(post(urlPathEqualTo(WEBHOOK)).withQueryParam("wait", equalTo("true"))
                .inScenario(scenario).whenScenarioStateIs("retry").willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json").withBody("{\"message\":\"Unauthorized\",\"code\":0}")));
        // when / then
        assertThatThrownBy(() -> posts.postSettings(match())).isInstanceOf(DiscordTransientException.class).hasMessageContaining("reconcile");
        tx.executeWithoutResult(status -> assertThat(attempts.findByActiveRaceId(fixture.race().getId())).isPresent()
                .get().extracting(PickPublicationAttempt::getState).isEqualTo(PickPublicationState.UNCERTAIN));
        assertThat(disclosure.forPublic(fixture.race().getId()).settings()).isNull();
        assertThatThrownBy(() -> posts.postSettings(match())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("reconcile");
        wm.verify(2, postRequestedFor(urlPathEqualTo(WEBHOOK)).withQueryParam("wait", equalTo("true")));
    }

    private void approve() {
        submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), new AuthorizedPickActor(fixture.race().getId(), fixture.season().getId(),
                fixture.homeTeam().getId(), "12345"), payload);
        reviews.approve(fixture.race().getId(), 1, 1, new ManagementPickActor("test-admin"), "Test exact setup checked");
    }
    private Match match() {
        return tx.execute(status -> {
            var match = matches.findById(fixture.match().getId()).orElseThrow();
            match.getRaces().forEach(race -> { race.getSettings().isComplete(); race.getCar().getDisplayName(); race.getTrack().getName(); });
            match.getHomeTeam().getShortName(); match.getAwayTeam().getShortName(); match.getMatchday().getSeason().getName();
            return match;
        });
    }
    private PickPayload corrected() {
        return new PickPayload(payload.trackId(), payload.carId(), null, payload.laps(), payload.tyreWear(), payload.fuelConsumption(),
                payload.refuelingSpeed(), payload.initialFuel(), payload.requiredPitStops(), payload.timeProgressionMode(), payload.timeProgressionMultiplier(),
                "S02, R04, S03", payload.timeOfDay(), payload.availableTyres(), payload.mandatoryTyres());
    }
    private RenderedPickGraphic graphic(Race race, boolean lobby) {
        String text = race.getTrack().getName() + ";" + race.getSettings().getWeather();
        if (!lobby) text += ";" + race.getCar().getDisplayName();
        return new RenderedPickGraphic(text.getBytes(StandardCharsets.UTF_8), "a".repeat(64), lobby ? PickDisclosureField.lobby() : PickDisclosureField.all());
    }
    private void outsideNetwork() {
        try {
            doAnswer(call -> { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return call.callRealMethod(); })
                    .when(webhooks).executeMultipart(anyString(), any(WebhookPayload.class), anyList(), isNull());
        } catch (java.io.IOException impossibleStubbing) { throw new AssertionError(impossibleStubbing); }
    }
}
