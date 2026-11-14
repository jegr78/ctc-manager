package org.ctc.admin.controller;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.discord.service.PickPublicationEvidenceService;
import org.ctc.domain.model.*;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pickpublicationui;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.locations=classpath:db/migration", "logging.config=classpath:logback-test.xml", "ctc.outbound.enabled=false"})
@AutoConfigureMockMvc @ActiveProfiles("prod") @Tag("integration")
class PickPublicationControllerIT {
    @Autowired MockMvc mvc;
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired TrackRepository tracks;
    @Autowired PickPublicationAttemptRepository attempts;
    @Autowired DiscordPostRepository posts;
    @Autowired PickPublicationService publications;
    @Autowired PickSessionEpoch epoch;
    @Autowired PickDisclosureService disclosure;
    @Autowired Clock clock;
    @MockitoBean PickPublicationEvidenceService evidence;
    TestHelper.SeasonFixture fixture; UUID trackId, batch;
    static final String MESSAGE = "345678901234567890";

    @BeforeEach void setUp() {
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("TPU" + UUID.randomUUID().toString().substring(0, 7));
            
            var track = tracks.save(new Track("Test unpublished verification " + UUID.randomUUID())); trackId = track.getId(); fixture.race().setTrack(track);
            fixture.match().setDiscordChannelId("123456789012345678");
            fixture.match().setDiscordChannelWebhookUrl("https://discord.com/api/v10/webhooks/234567890123456789/Test_private-token");
            batch = UUID.randomUUID();
            var attempt = attempts.saveAndFlush(new PickPublicationAttempt(new PickPublicationAttempt.Capture(batch, fixture.race(), 1, 0,
                    PickPublicationOrigin.LEGACY, PickPublicationOutput.LOBBY_SETTINGS, PickPublicationContent.from(fixture.race()), Set.of(PickDisclosureField.TRACK),
                    "test-original-manager", "123456789012345678", "234567890123456789", null, "a".repeat(64), "{}", epoch.managementGeneration(), clock.instant().minusSeconds(2))));
            attempt.uncertain("TEST_LOST_RESPONSE", clock.instant().minusSeconds(1));
        });
    }
    @AfterEach void cleanUp() {
        tx.executeWithoutResult(status -> {
            posts.deleteAll(posts.findAll().stream().filter(post -> fixture.match().getId().equals(post.getMatchId())).toList()); posts.flush();
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow()); seasons.flush();
            tracks.deleteById(trackId); teams.deleteAllById(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
        });
    }

    @Test @WithMockUser("test-manager")
    void givenUnconfirmedPublication_whenOpeningTheNativeReview_thenShowItsPendingStateWithoutExposingCredentials() throws Exception {
        mvc.perform(get("/admin/pick-publications")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Unconfirmed publications")));
        mvc.perform(get(path())).andExpect(status().isOk()).andExpect(view().name("admin/pick-publication"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("UNCERTAIN")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Test_private-token"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Test unpublished verification"))));
        verifyNoInteractions(evidence); assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).isNull();
    }

    @Test @WithMockUser("test-manager")
    void givenMatchingRemoteEvidence_whenConfirmingWithCsrf_thenPersistExactFieldsAndTheManagementActor() throws Exception {
        mvc.perform(post(path() + "/confirm").with(csrf()).param("expectedFingerprint", publications.reconciliationFingerprint(batch))
                .param("expectedGeneration", epoch.managementGeneration()).param("messageId", MESSAGE).param("reason", "Test inspected remote output"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path()));
        verify(evidence).verify(anyString(), eq(MESSAGE), eq("234567890123456789"), eq("123456789012345678"), eq("{}"), anyString());
        assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).contains("Test unpublished verification");
        assertThat(disclosure.forPublic(fixture.race().getId()).carName()).isNull();
        tx.executeWithoutResult(status -> {
            assertThat(attempts.findByBatchId(batch).getFirst().getReconciledBy()).isEqualTo("test-manager");
            assertThat(posts.findByChannelIdAndPostTypeAndMatchId("123456789012345678", org.ctc.discord.model.DiscordPostType.LOBBY_SETTINGS, fixture.match().getId()))
                    .isPresent().get().extracting(org.ctc.discord.model.DiscordPost::getMessageId).isEqualTo(MESSAGE);
        });
    }

    @Test @WithMockUser("test-manager")
    void givenPublishedSetupAndLaterPrivateEdit_whenReviewingTheRace_thenDistinguishUnpublishedChangesFromTheConfirmedFields() throws Exception {
        // given / when / then
        mvc.perform(get("/admin/matchday-picks/" + fixture.race().getId())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Unpublished")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(path())));
        mvc.perform(post(path() + "/confirm").with(csrf()).param("expectedFingerprint", publications.reconciliationFingerprint(batch))
                .param("expectedGeneration", epoch.managementGeneration()).param("messageId", MESSAGE).param("reason", "Test confirmed original setup"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/matchday-picks/" + fixture.race().getId())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Captured fields published")));
        tx.executeWithoutResult(status -> tracks.findById(trackId).orElseThrow().setName("Test later private label"));
        mvc.perform(get("/admin/matchday-picks/" + fixture.race().getId())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Internal changes unpublished")));
        assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).contains("Test unpublished verification");
    }

    @Test @WithMockUser("test-manager")
    void givenUnverifiableMessage_whenConfirming_thenKeepTheFormAndPendingState() throws Exception {
        doThrow(new org.ctc.domain.exception.BusinessRuleException("Test message mismatch")).when(evidence).verify(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        mvc.perform(post(path() + "/confirm").with(csrf()).param("expectedFingerprint", publications.reconciliationFingerprint(batch))
                .param("expectedGeneration", epoch.managementGeneration()).param("messageId", MESSAGE).param("reason", "Test inspected remote output"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Test message mismatch")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(MESSAGE)));
        assertPending();
    }

    @Test @WithMockUser("test-manager")
    void givenExplicitRemoteRetirement_whenAbandoning_thenRecordItWithoutReleasingChoices() throws Exception {
        mvc.perform(post(path() + "/abandon").with(csrf()).param("expectedFingerprint", publications.reconciliationFingerprint(batch))
                .param("expectedGeneration", epoch.managementGeneration()).param("remoteHandled", "true").param("reason", "Test removed ambiguous output"))
                .andExpect(status().is3xxRedirection());
        tx.executeWithoutResult(status -> assertThat(attempts.findByBatchId(batch).getFirst().getState()).isEqualTo(PickPublicationState.ABANDONED));
        assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).isNull(); verifyNoInteractions(evidence);
    }

    @ParameterizedTest @ValueSource(strings = {"confirm", "abandon"}) @WithMockUser("test-manager")
    void givenAuthenticatedRequestWithoutCsrf_whenResolving_thenDenyBeforeAnyVerification(String action) throws Exception {
        mvc.perform(post(path() + "/" + action)).andExpect(status().isForbidden()); assertPending(); verifyNoInteractions(evidence);
    }
    @Test void givenAnonymousRequest_whenViewing_thenRequireProductionAuthentication() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized()); verifyNoInteractions(evidence);
    }
    private String path() { return "/admin/pick-publications/" + batch; }
    private void assertPending() {
        tx.executeWithoutResult(status -> assertThat(attempts.findByBatchId(batch).getFirst().getState()).isEqualTo(PickPublicationState.UNCERTAIN));
        assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).isNull();
    }
}
