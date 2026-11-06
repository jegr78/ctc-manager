package org.ctc.domain.service;

import java.time.LocalDateTime;
import java.util.UUID;
import org.ctc.admin.dto.PickCorrectionForm;
import org.ctc.admin.dto.PickReviewForm;
import org.ctc.admin.service.MatchdayPickAdminService;
import org.ctc.discord.service.DiscordPostService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@AutoConfigureMockMvc
@WithMockUser("test-manager")
class MatchdayPickReviewIT extends PickPersistenceIT {
    @Autowired MatchdayPickAdminService admin;
    @Autowired MockMvc mvc;
    @Autowired DriverRepository drivers;
    @Autowired RaceLineupRepository lineups;
    @Autowired PickSessionEpoch epoch;
    @MockitoSpyBean DiscordPostService discord;
    @MockitoSpyBean PickTargetService reviewTargets;
    UUID extraDriver;

    @BeforeEach
    void clearPublishingInvocations() { clearInvocations(discord); }

    @AfterEach
    @Override
    void cleanUp() {
        try { verifyNoInteractions(discord); }
        finally {
            super.cleanUp();
            if (extraDriver != null) drivers.deleteById(extraDriver);
        }
    }

    @Test
    void givenMissingPickAfterDeadline_whenOpeningAndClosingWithNativeForms_thenRetainDeadlineAndServerActor() throws Exception {
        // given
        var cutoff = LocalDateTime.of(2020, 1, 1, 12, 0);
        tx.executeWithoutResult(status -> entityManager.find(Race.class, fixture.race().getId()).getMatchday().setPickDeadline(cutoff));
        mvc.perform(get(path())).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("MISSING")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Deadline passed")));
        // when
        mvc.perform(decision(PickReviewForm.Action.OPEN_MISSING, "Late first submission allowed").param("actorId", "forged-actor"))
                .andExpect(status().is3xxRedirection());
        // then
        var opened = admin.detail(fixture.race().getId());
        assertThat(opened.row().state()).isEqualTo(PickState.MISSING); assertThat(opened.row().manualOpen()).isTrue();
        assertThat(opened.row().revisionNumber()).isNull(); assertThat(opened.decisions().getFirst().actor()).isEqualTo("test-manager");
        mvc.perform(decision(PickReviewForm.Action.CLOSE, "Team entry closed")).andExpect(status().is3xxRedirection());
        assertThatThrownBy(() -> submissions.submit(fixture.race().getId(), 2, UUID.randomUUID(), actor, payload))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("closed");
        tx.executeWithoutResult(status -> assertThat(entityManager.find(Race.class, fixture.race().getId()).getMatchday().getPickDeadline()).isEqualTo(cutoff));
    }

    @Test
    void givenSubmittedPickPastDeadline_whenReturningThenReplacing_thenShowStateHistoryAndRejectStaleApproval() throws Exception {
        // given
        submit(); var stale = decision(PickReviewForm.Action.APPROVE, "Test stale approval reason");
        tx.executeWithoutResult(status -> entityManager.find(Race.class, fixture.race().getId()).getMatchday().setPickDeadline(LocalDateTime.of(2020, 1, 1, 12, 0)));
        // when
        mvc.perform(decision(PickReviewForm.Action.RETURN, "Test <weather> correction")).andExpect(status().is3xxRedirection());
        var returned = admin.detail(fixture.race().getId());
        assertThat(returned.row().state()).isEqualTo(PickState.RETURNED); assertThat(returned.row().manualOpen()).isTrue();
        assertThat(returned.returnReason()).isEqualTo("Test <weather> correction");
        submissions.submit(fixture.race().getId(), 2, UUID.randomUUID(), actor, payload);
        // then
        mvc.perform(stale).andExpect(status().isOk()).andExpect(model().attributeHasErrors("form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Test stale approval reason")));
        var current = admin.detail(fixture.race().getId());
        assertThat(current.row().state()).isEqualTo(PickState.SUBMITTED); assertThat(current.row().version()).isEqualTo(3);
        assertThat(current.revisions()).extracting(org.ctc.admin.dto.MatchdayPickView.Revision::number).containsExactly(2, 1);
        mvc.perform(get(path())).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Test &lt;weather&gt; correction")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<weather>"))));
    }

    @Test
    void givenValidCurrentRevisionAndLineup_whenApproving_thenApplyOnlySettingsAndPreserveSchedulePairingAndLineup() throws Exception {
        // given
        submit();
        UUID lineupId = tx.execute(status -> {
            var driver = helper.createDriver("T-Review-" + UUID.randomUUID(), "Test review driver"); extraDriver = driver.getId();
            return lineups.saveAndFlush(new RaceLineup(entityManager.find(Race.class, fixture.race().getId()), driver,
                    teams.findById(fixture.homeTeam().getId()).orElseThrow())).getId();
        });
        // when
        mvc.perform(decision(PickReviewForm.Action.APPROVE, "Complete setup checked")).andExpect(status().is3xxRedirection());
        // then
        var approved = admin.detail(fixture.race().getId());
        assertThat(approved.row().state()).isEqualTo(PickState.APPROVED); assertThat(approved.row().approvedRevisionNumber()).isEqualTo(1);
        assertThat(approved.row().actions()).isEmpty(); assertThat(approved.row().correctable()).isTrue();
        tx.executeWithoutResult(status -> {
            var saved = entityManager.find(Race.class, fixture.race().getId());
            assertThat(saved.getCar().getId()).isEqualTo(payload.carId()); assertThat(saved.getTrack().getId()).isEqualTo(payload.trackId());
            assertThat(saved.getSettings().getNumberOfLaps()).isEqualTo(payload.laps());
            assertThat(saved.getMatch().getId()).isEqualTo(fixture.match().getId()); assertThat(saved.getDateTime()).isEqualTo(fixture.race().getDateTime());
            assertThat(saved.getMatchday().getId()).isEqualTo(fixture.matchday().getId()); assertThat(saved.getResults()).isEmpty();
            assertThat(lineups.findById(lineupId)).isPresent();
        });
        mvc.perform(decision(PickReviewForm.Action.RETURN, "Forbidden approved return")).andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("form"));
        assertThat(admin.detail(fixture.race().getId()).row().version()).isEqualTo(2);
    }

    @Test
    void givenDifferingPreconfiguredSettings_whenApproving_thenRequireExplicitReplacementAndCurrentFingerprint() throws Exception {
        // given
        submit();
        tx.executeWithoutResult(status -> {
            var race = entityManager.find(Race.class, fixture.race().getId()); var setup = new RaceSettings(race);
            setup.setNumberOfLaps(99); race.setSettings(setup); entityManager.persist(setup);
        });
        assertThat(admin.detail(fixture.race().getId()).requiresReplacement()).isTrue();
        // when / then
        mvc.perform(decision(PickReviewForm.Action.APPROVE, "Replacement not yet approved")).andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("form")).andExpect(content().string(org.hamcrest.Matchers.containsString("replace consent")));
        var changedAfterForm = decision(PickReviewForm.Action.APPROVE, "Test explicit replacement").param("replaceExisting", "true");
        tx.executeWithoutResult(status -> entityManager.find(Race.class, fixture.race().getId()).getSettings().setWeather("Test later management work"));
        mvc.perform(changedAfterForm).andExpect(status().isOk()).andExpect(model().attributeHasErrors("form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Internal settings changed")));
        assertThat(admin.detail(fixture.race().getId()).row().state()).isEqualTo(PickState.SUBMITTED);
        mvc.perform(decision(PickReviewForm.Action.APPROVE, "Explicit replacement checked").param("replaceExisting", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(admin.detail(fixture.race().getId()).row().state()).isEqualTo(PickState.APPROVED);
    }

    @Test
    void givenApprovedRevision_whenSavingCorrection_thenRetainOriginalAndRecordActorReasonAndCompletePayload() throws Exception {
        // given
        submit(); mvc.perform(decision(PickReviewForm.Action.APPROVE, "Original approved")).andExpect(status().is3xxRedirection());
        mvc.perform(get(path() + "/correction")).andExpect(status().isOk()).andExpect(view().name("admin/matchday-pick-correction"));
        var form = admin.correctionForm(fixture.race().getId());
        // when
        mvc.perform(correction(form).param("laps", "22").param("weather", "Test corrected weather")
                        .param("timeProgressionMode", "MULTIPLIER").param("timeProgressionMultiplier", "0"))
                .andExpect(status().is3xxRedirection());
        // then
        var corrected = admin.detail(fixture.race().getId());
        assertThat(corrected.row().state()).isEqualTo(PickState.APPROVED); assertThat(corrected.row().approvedRevisionNumber()).isEqualTo(1);
        assertThat(corrected.row().version()).isEqualTo(3); assertThat(corrected.revisions()).hasSize(1);
        assertThat(corrected.revisions().getFirst().values()).anySatisfy(value -> {
            assertThat(value.label()).isEqualTo("Laps"); assertThat(value.value()).isEqualTo("12");
        });
        assertThat(corrected.decisions().getFirst().kind()).isEqualTo(PickDecisionKind.CORRECTION);
        assertThat(corrected.decisions().getFirst().actor()).isEqualTo("test-manager");
        assertThat(corrected.decisions().getFirst().reason()).isEqualTo("Test internal correction reason");
        assertThat(corrected.decisions().getFirst().correction()).anySatisfy(value -> {
            assertThat(value.label()).isEqualTo("Time progression"); assertThat(value.value()).isEqualTo("0x");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"results", "decided", "reoriented", "eligibility"})
    void givenInvalidatedTargetOrSelection_whenApproving_thenPreserveSubmissionAndInternalSetup(String invalid) throws Exception {
        // given
        if (invalid.equals("reoriented")) {
            tx.executeWithoutResult(status -> {
                var returning = entityManager.find(Race.class, fixture.race().getId());
                var outbound = helper.createRace(returning.getMatchday(), returning.getMatch()); outbound.setDateTime(returning.getDateTime());
                returning.setDateTime(returning.getDateTime().plusDays(1));
                returning.setHomeTeamOverride(fixture.awayTeam()); returning.setAwayTeamOverride(fixture.homeTeam());
            });
            actor = new org.ctc.domain.picks.AuthorizedPickActor(fixture.race().getId(), fixture.season().getId(), fixture.awayTeam().getId(), "12345");
        }
        submit(); var request = decision(PickReviewForm.Action.APPROVE, "Test invalidated approval");
        tx.executeWithoutResult(status -> {
            var race = entityManager.find(Race.class, fixture.race().getId());
            switch (invalid) {
                case "results" -> {
                    var driver = helper.createDriver("T-Review-result-" + UUID.randomUUID(), "Test result driver"); extraDriver = driver.getId();
                    var result = new RaceResult(race, driver, 1, 1, false); entityManager.persist(result); race.getResults().add(result);
                }
                case "decided" -> race.getMatch().setHomeScore(0);
                case "reoriented" -> { race.setHomeTeamOverride(fixture.homeTeam()); race.setAwayTeamOverride(fixture.awayTeam()); }
                case "eligibility" -> race.getMatchday().getSeason().getCars().removeIf(car -> car.getId().equals(payload.carId()));
                default -> throw new AssertionError();
            }
        });
        // when / then
        mvc.perform(request).andExpect(status().isOk()).andExpect(model().attributeHasErrors("form"));
        assertThat(admin.detail(fixture.race().getId()).row().state()).isEqualTo(PickState.SUBMITTED);
        tx.executeWithoutResult(status -> assertThat(entityManager.find(Race.class, fixture.race().getId()).getSettings()).isNull());
    }

    @Test
    void givenDatabaseFailureAtApprovalCommit_whenSaving_thenRollbackSettingsApprovalAndDecisionTogether() {
        // given
        submit(); var form = admin.reviewForm(admin.detail(fixture.race().getId())); form.setReason("Test commit rollback");
        doAnswer(invocation -> {
            var locked = invocation.callRealMethod();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    entityManager.flush();
                    assertThat(entityManager.find(Race.class, fixture.race().getId()).getSettings().getNumberOfLaps()).isEqualTo(12);
                    throw new DataIntegrityViolationException("Test approval commit failure");
                }
            }); return locked;
        }).when(AopTestUtils.<PickTargetService>getUltimateTargetObject(reviewTargets)).lockForManagement(eq(fixture.race().getId()), any());
        // when / then
        assertThatThrownBy(() -> admin.decide(fixture.race().getId(), form, () -> "test-manager")).isInstanceOf(DataIntegrityViolationException.class);
        var current = admin.detail(fixture.race().getId());
        assertThat(current.row().state()).isEqualTo(PickState.SUBMITTED); assertThat(current.row().version()).isEqualTo(1);
        assertThat(current.decisions()).isEmpty();
        tx.executeWithoutResult(status -> assertThat(entityManager.find(Race.class, fixture.race().getId()).getSettings()).isNull());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(CarCategory.class)
    void givenSelectedCarClassification_whenReviewingAndApproving_thenShowTheSameGt7FilterInInternalSettings(CarCategory category) throws Exception {
        // given
        tx.executeWithoutResult(status -> entityManager.find(Car.class, payload.carId()).setCategory(category));
        submit();
        String expected = switch (category) {
            case GR_1 -> "Gr.1"; case GR_2 -> "Gr.2"; case GR_3 -> "Gr.3"; case GR_4 -> "Gr.4"; case GR_B -> "Gr.B";
            case GR_N, GR_X -> "—";
        };
        // when
        var comparison = admin.detail(fixture.race().getId()).comparison();
        // then
        assertThat(comparison).anySatisfy(value -> {
            assertThat(value.label()).isEqualTo("Filter by Category");
            assertThat(value.existing()).isEqualTo("—"); assertThat(value.submitted()).isEqualTo(expected);
        });
        mvc.perform(decision(PickReviewForm.Action.APPROVE, "Test GT7 category review")).andExpect(status().is3xxRedirection());
        assertThat(admin.detail(fixture.race().getId()).comparison()).anySatisfy(value -> {
            assertThat(value.label()).isEqualTo("Filter by Category");
            assertThat(value.existing()).isEqualTo(expected); assertThat(value.submitted()).isEqualTo(expected);
            assertThat(value.changed()).isFalse();
        });
        verifyNoInteractions(discord);
        var response = mvc.perform(get("/admin/races/" + fixture.race().getId())).andExpect(status().isOk()).andReturn();
        var document = org.jsoup.Jsoup.parse(response.getResponse().getContentAsString());
        assertThat(document.select(".detail-fields .label").stream().filter(label -> label.text().equals("Filter by Category"))
                .findFirst().orElseThrow().nextElementSibling().text()).isEqualTo(expected);
        verify(discord).canPostRaceResultToForum(argThat(race -> race.getId().equals(fixture.race().getId())), any());
        verifyNoMoreInteractions(discord); clearInvocations(discord);
    }

    @Test
    void givenSubmittedPickAndNativeOverviewFilters_whenLoading_thenKeepExactSeasonMatchdayStateAndTarget() throws Exception {
        // given
        submit();
        // when
        var overview = admin.overview(fixture.season().getId(), fixture.matchday().getId(), PickState.SUBMITTED);
        // then
        assertThat(overview.rows()).hasSize(1); assertThat(overview.rows().getFirst().raceId()).isEqualTo(fixture.race().getId());
        assertThat(overview.rows().getFirst().label()).contains(fixture.matchday().getLabel(), "Leg 1");
        assertThat(admin.overview(fixture.season().getId(), fixture.matchday().getId(), PickState.MISSING).rows()).isEmpty();
        assertThatThrownBy(() -> admin.overview(UUID.randomUUID(), fixture.matchday().getId(), null)).isInstanceOf(BusinessRuleException.class);
        mvc.perform(get("/admin/matchday-picks").param("seasonId", fixture.season().getId().toString())
                        .param("matchdayId", fixture.matchday().getId().toString()).param("state", "SUBMITTED"))
                .andExpect(status().isOk()).andExpect(view().name("admin/matchday-picks"))
                .andExpect(model().attribute("selectedSeasonId", fixture.season().getId()));
    }

    @Test
    void givenEnrolledMissingTargetChangedToBye_whenLoadingReview_thenKeepOverviewReadableWithoutOfferingDecisions() {
        // given
        tx.executeWithoutResult(status -> {
            var race = entityManager.find(Race.class, fixture.race().getId()); race.getMatch().setBye(true); race.getMatch().setAwayTeam(null);
        });
        // when / then
        assertThatCode(() -> admin.overview(fixture.season().getId(), fixture.matchday().getId(), null)).doesNotThrowAnyException();
        var row = admin.overview(fixture.season().getId(), fixture.matchday().getId(), null).rows().getFirst();
        assertThat(row.label()).contains("Bye"); assertThat(row.actions()).isEmpty();
        assertThatCode(() -> admin.detail(fixture.race().getId())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"approval", "correction"})
    void givenManagementFormOpenedBeforeRestore_whenPostingAfterRestore_thenRejectWithoutChangingRestoredState(String kind) throws Exception {
        // given
        submit();
        MockHttpServletRequestBuilder request;
        if (kind.equals("approval")) request = decision(PickReviewForm.Action.APPROVE, "Test pre-restore approval");
        else {
            mvc.perform(decision(PickReviewForm.Action.APPROVE, "Test initial approval")).andExpect(status().is3xxRedirection());
            request = correction(admin.correctionForm(fixture.race().getId())).param("laps", "22").param("weather", "Test stale correction")
                    .param("timeProgressionMode", "EQUAL_CONDITIONS");
        }
        long version = admin.detail(fixture.race().getId()).row().version();
        epoch.afterRestore(null);
        // when / then
        mvc.perform(request).andExpect(status().isOk()).andExpect(model().attributeHasErrors("form"));
        assertThat(admin.detail(fixture.race().getId()).row().version()).isEqualTo(version);
        if (kind.equals("approval")) {
            mvc.perform(decision(PickReviewForm.Action.APPROVE, "Test explicitly reloaded approval")).andExpect(status().is3xxRedirection());
        } else {
            mvc.perform(correction(admin.correctionForm(fixture.race().getId())).param("laps", "22").param("weather", "Test reloaded correction")
                    .param("timeProgressionMode", "EQUAL_CONDITIONS")).andExpect(status().is3xxRedirection());
        }
        assertThat(admin.detail(fixture.race().getId()).row().version()).isEqualTo(version + 1);
    }

    void submit() { submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, payload); }
    String path() { return "/admin/matchday-picks/" + fixture.race().getId(); }
    MockHttpServletRequestBuilder decision(PickReviewForm.Action action, String reason) {
        var detail = admin.detail(fixture.race().getId());
        var form = admin.reviewForm(detail);
        var request = post(path() + "/decision").param("action", action.name()).param("reason", reason)
                .param("expectedVersion", form.getExpectedVersion().toString()).param("settingsFingerprint", form.getSettingsFingerprint())
                .param("expectedGeneration", form.getExpectedGeneration().toString());
        if (detail.row().revisionNumber() != null) request.param("revisionNumber", detail.row().revisionNumber().toString());
        return request;
    }
    MockHttpServletRequestBuilder correction(PickCorrectionForm form) {
        return post(path() + "/correction").param("expectedVersion", form.getExpectedVersion().toString()).param("settingsFingerprint", form.getSettingsFingerprint())
                .param("expectedGeneration", form.getExpectedGeneration().toString())
                .param("lobbyRulesProfile", form.getLobbyRulesProfile().name()).param("startType", form.getStartType().name()).param("slipstream", form.getSlipstream().name())
                .param("trackId", form.getTrackId().toString()).param("carId", form.getCarId().toString()).param("tyreWear", "2")
                .param("fuelConsumption", "3").param("refuelingSpeed", "4").param("initialFuel", "DEFAULT").param("requiredPitStops", "1")
                .param("availableTyres", "RS", "RM").param("mandatoryTyres", "RM").param("timeOfDay", "Sunset")
                .param("reason", "Test internal correction reason");
    }

    @ParameterizedTest
    @ValueSource(strings = {"lobbyRulesProfile", "startType", "slipstream"})
    void givenMissingCorrectionRulesField_whenPosting_thenReturnItsFieldErrorWithoutChangingTheApprovedPick(String field) throws Exception {
        // given
        submit();
        reviews.approve(fixture.race().getId(), 1, 1, new org.ctc.domain.picks.ManagementPickActor("test-manager"), "Test approved before missing field");
        var request = correction(admin.correctionForm(fixture.race().getId())).param("laps", "12").param("weather", "Test unchanged weather")
                .param("timeProgressionMode", "EQUAL_CONDITIONS").with(raw -> { raw.removeParameter(field); return raw; });
        // when / then
        mvc.perform(request).andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", field));
        assertThat(admin.detail(fixture.race().getId()).row().version()).isEqualTo(2);
    }
}
