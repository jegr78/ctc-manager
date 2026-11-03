package org.ctc.admin.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.picks.PreparedPickPublication;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pickactivation;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.locations=classpath:db/migration",
        "logging.config=classpath:logback-test.xml", "ctc.outbound.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Tag("integration")
class PickActivationControllerIT {
    @Autowired MockMvc mvc;
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired RaceRepository races;
    @Autowired TrackRepository tracks;
    @Autowired MatchdayPickRepository picks;
    @Autowired PickActivationService activation;
    @Autowired PickPublicationService publications;
    @Autowired PickDisclosureService disclosure;
    @Autowired PickSessionEpoch epoch;
    TestHelper.SeasonFixture fixture;
    UUID trackId;

    @BeforeEach void setUp() {
        // given
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("TAC" + UUID.randomUUID().toString().substring(0, 7));
            fixture.race().setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0));
            var track = tracks.save(new Track("Test activation private " + UUID.randomUUID())); trackId = track.getId();
            fixture.race().setTrack(track);
        });
    }
    @AfterEach void cleanUp() {
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow()); seasons.flush();
            tracks.deleteById(trackId); teams.deleteAllById(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
        });
    }

    @Test @WithMockUser("test-manager")
    void givenLegacyConfiguredSeason_whenOpeningMigrationReview_thenShowAnUnselectedExplicitClassification() throws Exception {
        // when / then
        mvc.perform(get(path())).andExpect(status().isOk()).andExpect(view().name("admin/pick-collection"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Review existing settings")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Test activation private")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Choose a classification")));
        assertUnenrolled();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true}) @WithMockUser("test-manager")
    void givenExplicitWithheldReview_whenEnablingThroughTheNativeForm_thenEnrollWithoutCreatingAnApprovalOrPublication(boolean scheduled) throws Exception {
        // given
        if (!scheduled) tx.executeWithoutResult(status -> races.findById(fixture.race().getId()).orElseThrow().setDateTime(null));
        var review = activation.review(fixture.season().getId());
        // when
        mvc.perform(post(path() + "/enable").with(csrf()).param("expectedFingerprint", review.fingerprint())
                .param("expectedGeneration", review.generation()).param(classification(), "WITHHELD"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path()));
        // then
        assertThat(activation.review(fixture.season().getId()).enabled()).isTrue();
        assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).isNull();
        tx.executeWithoutResult(status -> assertThat(picks.findByRaceId(fixture.race().getId())).isEmpty());
    }

    @Test @WithMockUser("test-manager")
    void givenMissingClassification_whenPosting_thenKeepTheReviewContextAndRejectWithoutMutation() throws Exception {
        // given
        var review = activation.review(fixture.season().getId());
        // when / then
        mvc.perform(post(path() + "/enable").with(csrf()).param("expectedFingerprint", review.fingerprint()).param("expectedGeneration", review.generation()))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Classify every configured target")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Test activation private")));
        assertUnenrolled();
    }

    @Test @WithMockUser("test-manager")
    void givenChangedSettingsAfterReview_whenPosting_thenRejectTheStaleFingerprintBeforeEnrollment() throws Exception {
        // given
        var review = activation.review(fixture.season().getId());
        tx.executeWithoutResult(status -> tracks.findById(trackId).orElseThrow().setName("Test changed private activation track " + UUID.randomUUID()));
        // when / then
        mvc.perform(post(path() + "/enable").with(csrf()).param("expectedFingerprint", review.fingerprint())
                .param("expectedGeneration", review.generation()).param(classification(), "WITHHELD"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("review changed")));
        assertUnenrolled();
    }

    @Test @WithMockUser("test-manager")
    void givenMatchingConfirmedLobbyEvidence_whenEnablingAndPausing_thenRetainExactlyItsPublishedFields() throws Exception {
        // given
        publications.publish(List.of(fixture.race().getId()), PickPublicationOutput.LOBBY_SETTINGS,
                new ManagementPickActor("test-manager"), rows -> new PreparedPickPublication<>("123456789012345678", "234567890123456789", null,
                        "a".repeat(64), "{}", Map.of(fixture.race().getId(), Set.of(PickDisclosureField.TRACK)),
                        () -> "test-actual-legacy-post", message -> message));
        var review = activation.review(fixture.season().getId());
        // when
        mvc.perform(post(path() + "/enable").with(csrf()).param("expectedFingerprint", review.fingerprint())
                .param("expectedGeneration", review.generation()).param(classification(), "CONFIRMED_PUBLIC"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post(path() + "/pause").with(csrf()).param("expectedFingerprint", activation.review(fixture.season().getId()).fingerprint())
                .param("expectedGeneration", epoch.managementGeneration())).andExpect(status().is3xxRedirection());
        // then
        assertThat(activation.review(fixture.season().getId()).enabled()).isFalse();
        assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).contains("Test activation private");
        assertThat(disclosure.forPublic(fixture.race().getId()).carName()).isNull();
        tx.executeWithoutResult(status -> {
            assertThat(races.findById(fixture.race().getId()).orElseThrow().isPicksManaged()).isTrue();
            assertThat(picks.findByRaceId(fixture.race().getId())).isEmpty();
        });
    }

    @Test
    void givenAnonymousUser_whenOpeningTheReview_thenRequireExistingProductionAuthentication() throws Exception {
        // when / then
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
    }
    @ParameterizedTest @ValueSource(strings = {"enable", "pause"}) @WithMockUser("test-manager")
    void givenAuthenticatedMutationWithoutCsrf_whenPosting_thenDenyBeforeAnyDomainMutation(String route) throws Exception {
        // when / then
        mvc.perform(post(path() + "/" + route)).andExpect(status().isForbidden()); assertUnenrolled();
    }
    @ParameterizedTest @ValueSource(strings = {"enable", "pause"})
    void givenAnonymousMutationWithCsrf_whenPosting_thenRequireAuthentication(String route) throws Exception {
        // when / then
        mvc.perform(post(path() + "/" + route).with(csrf())).andExpect(status().isUnauthorized()); assertUnenrolled();
    }
    @Test
    void givenLegacyOutputStillInFlight_whenEnabling_thenWaitForItsActualConfirmationBeforeClassifyingIt() throws Exception {
        // given
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var sent = executor.submit(() -> publications.publish(List.of(fixture.race().getId()), PickPublicationOutput.SETTINGS,
                    new ManagementPickActor("test-manager"), rows -> new PreparedPickPublication<>("123456789012345678", "234567890123456789", null,
                            "a".repeat(64), "{}", Map.of(fixture.race().getId(), Set.of(PickDisclosureField.TRACK)), () -> {
                                started.countDown();
                                try {
                                    if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("Test network release missing");
                                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted); }
                                return "test-confirmed-after-flight";
                            }, message -> message)));
            try {
                assertThat(started.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var review = activation.review(fixture.season().getId());
                assertThat(review.pendingPublicationCount()).isEqualTo(1);
                assertThatThrownBy(() -> activation.enable(fixture.season().getId(), review.fingerprint(),
                        Map.of(fixture.race().getId(), LegacyPickClassification.WITHHELD), new ManagementPickActor("test-manager", epoch.managementGeneration())))
                        .isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class).hasMessageContaining("Reconcile");
                assertUnenrolled();
            } finally { release.countDown(); }
            assertThat(sent.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("test-confirmed-after-flight");
        }
        // then
        var confirmed = activation.review(fixture.season().getId());
        assertThat(confirmed.pendingPublicationCount()).isZero(); assertThat(confirmed.configured().getFirst().confirmedPublic()).isTrue();
        activation.enable(fixture.season().getId(), confirmed.fingerprint(), Map.of(fixture.race().getId(), LegacyPickClassification.CONFIRMED_PUBLIC),
                new ManagementPickActor("test-manager", epoch.managementGeneration()));
        assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).contains("Test activation private");
    }

    private void assertUnenrolled() {
        assertThat(activation.review(fixture.season().getId()).enabled()).isFalse();
        tx.executeWithoutResult(status -> assertThat(races.findById(fixture.race().getId()).orElseThrow().isPicksManaged()).isFalse());
    }
    private String path() { return "/admin/seasons/" + fixture.season().getId() + "/pick-collection"; }
    private String classification() { return "classifications[" + fixture.race().getId() + "]"; }
}
