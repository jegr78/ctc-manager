package org.ctc.discord.incidents;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.ctc.discord.picks.PickGatewayInteraction;
import org.ctc.domain.incidents.IncidentMatchRef;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IncidentInteractionIT extends IncidentIntakeIntegrationFixture {
    @org.springframework.beans.factory.annotation.Autowired org.ctc.domain.service.IncidentPrivateReadService privateReads;
    @Test
    void givenConfirmedPrivateReport_whenReplayingEditingAndWithdrawing_thenKeepOneCaseAndOneFirstReceiptRequest() {
        var client = flow().draft(fixture.race().getId(), PRIVATE); assertThat(rows()).isEmpty();
        String confirmation = client.button("Confirm report"); client.send(PickGatewayInteraction.Kind.BUTTON, confirmation, Map.of());
        String saved = client.message().content(); assertThat(saved).contains("Stored", "pending");
        client.send(PickGatewayInteraction.Kind.BUTTON, confirmation, Map.of()); assertThat(client.message().content()).isEqualTo(saved);
        var report = rows().getFirst(); UUID id = report.getId();
        assertThat(rows()).hasSize(1); assertThat(firstRequests()).isEqualTo(1);
        assertThat(report.getAuthorId()).isEqualTo(USER); assertThat(report.getRace().getId()).isEqualTo(fixture.race().getId());
        var other = flow(OTHER, CHANNEL).command().click("My reports"); assertThat(other.message().content()).contains("no accessible").doesNotContain(PRIVATE);
        client.edit(id, "Test corrected case").click("Confirm changes"); assertThat(latestDescription(id)).isEqualTo("Test corrected case");
        client.command().click("My reports").select(id).click("Withdraw report").click("Confirm withdrawal");
        var withdrawn = rows().getFirst(); assertThat(withdrawn.getState()).isEqualTo(IncidentReportState.WITHDRAWN);
        assertThat(withdrawn.getVersion()).isEqualTo(3); assertThat(withdrawn.getLatestRevisionNumber()).isEqualTo(2);
        assertThat(firstRequests()).isEqualTo(1);
        client.command().click("My reports").select(id); assertThat(client.message().content()).contains("WITHDRAWN", "Test corrected case");
        assertThat(client.message().buttons()).extracting(org.ctc.discord.picks.PickGatewayMessage.Button::label).doesNotContain("Edit report", "Withdraw report");
    }

    @ParameterizedTest
    @ValueSource(strings = {"inherited", "organization"})
    void givenInheritedOrSharedOrganizationRole_whenSubmitting_thenStoreItsDeterministicOriginalParty(String mode) {
        var organization = parent();
        tx.executeWithoutResult(status -> {
            var home = teams.findById(fixture.homeTeam().getId()).orElseThrow(); home.setParentTeam(teams.findById(organization.getId()).orElseThrow()); home.setDiscordRoleId(null);
            if (mode.equals("organization")) { var away = teams.findById(fixture.awayTeam().getId()).orElseThrow(); away.setParentTeam(home.getParentTeam()); away.setDiscordRoleId(null); }
        });
        var client = flow().draft(fixture.race().getId(), PRIVATE);
        if (mode.equals("organization")) assertThat(client.message().content()).contains("Test reporting organization");
        client.click("Confirm report"); var party = rows().getFirst().getParty();
        assertThat(party.getType()).isEqualTo(mode.equals("organization") ? IncidentPartyType.ORGANIZATION : IncidentPartyType.TEAM);
        assertThat(party.getId()).isEqualTo(mode.equals("organization") ? organization.getId() : fixture.homeTeam().getId());
        assertThat(party.getRoleId()).isEqualTo(HOME_ROLE); assertThat(firstRequests()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"opposing", "unrelated-shared", "no-relevant-role"})
    void givenAmbiguousOrUnrelatedRoles_whenStarting_thenDenyPrivatelyWithoutCreatingACase(String mode) {
        if (mode.equals("opposing")) roles.put(USER, Set.of(HOME_ROLE, AWAY_ROLE));
        if (mode.equals("no-relevant-role")) roles.put(USER, Set.of("999999999999999999"));
        if (mode.equals("unrelated-shared")) tx.executeWithoutResult(status -> teams.findById(fixture.awayTeam().getId()).orElseThrow().setDiscordRoleId(HOME_ROLE));
        var client = flow().command(); assertThat(client.text()).contains("unavailable", "Ask management").doesNotContain(PRIVATE);
        assertThat(rows()).isEmpty(); assertThat(firstRequests()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"roles", "channel"})
    void givenRevokedAccessAfterConfirmation_whenReadingAgain_thenHideStoredCaseAndPreserveItsHistory(String mode) {
        UUID id = submit();
        if (mode.equals("roles")) roles.put(USER, Set.of()); else inaccessible.add(CHANNEL);
        var denied = flow().command(); assertThat(denied.text()).contains("unavailable").doesNotContain(PRIVATE);
        assertThat(rows()).singleElement().satisfies(report -> { assertThat(report.getId()).isEqualTo(id); assertThat(report.getVersion()).isEqualTo(1); });
        assertThat(latestDescription(id)).isEqualTo(PRIVATE);
    }

    @Test
    void givenCurrentEligibilityWithRenamedTeamAndChangedRole_whenEditing_thenRetainTheOriginalSnapshot() {
        UUID id = submit(); var original = rows().getFirst().getParty();
        tx.executeWithoutResult(status -> { var team = teams.findById(fixture.homeTeam().getId()).orElseThrow(); team.setName("Test renamed reporting team"); team.setDiscordRoleId("700000000000000007"); });
        roles.put(USER, Set.of("700000000000000007"));
        flow().edit(id, "Test after role change").click("Confirm changes");
        assertThat(rows().getFirst().getParty()).isEqualTo(original); assertThat(latestDescription(id)).isEqualTo("Test after role change");
    }

    @ParameterizedTest
    @ValueSource(strings = {"submit", "edit", "withdraw"})
    void givenExactCutoffAfterReview_whenConfirming_thenRejectWithoutChangingTheLastAcceptedCase(String operation) {
        UUID id = operation.equals("submit") ? null : submit(); var client = flow(); String confirmation;
        if (operation.equals("submit")) { client.draft(fixture.race().getId(), PRIVATE); confirmation = client.button("Confirm report"); }
        else if (operation.equals("edit")) { client.edit(id, "Test too late"); confirmation = client.button("Confirm changes"); }
        else { client.command().click("My reports").select(id).click("Withdraw report"); confirmation = client.button("Confirm withdrawal"); }
        clock.set(NOW.plusSeconds(60)); client.send(PickGatewayInteraction.Kind.BUTTON, confirmation, Map.of());
        assertThat(client.text()).contains("unavailable");
        if (id == null) assertThat(rows()).isEmpty();
        else { assertThat(rows().getFirst().getVersion()).isEqualTo(1); assertThat(rows().getFirst().getState()).isEqualTo(IncidentReportState.ACTIVE); assertThat(latestDescription(id)).isEqualTo(PRIVATE); }
    }

    @Test
    void givenLostReplyAndAcceptedRequest_whenReplayingAtCutoff_thenRecoverTheOriginalStoredReceipt() {
        var client = flow().draft(fixture.race().getId(), PRIVATE); String confirmation = client.button("Confirm report");
        client.send(PickGatewayInteraction.Kind.BUTTON, confirmation, Map.of()); String original = client.message().content();
        clock.set(NOW.plusSeconds(60)); client.send(PickGatewayInteraction.Kind.BUTTON, confirmation, Map.of());
        assertThat(client.message().content()).isEqualTo(original); assertThat(rows()).hasSize(1); assertThat(firstRequests()).isEqualTo(1);
    }

    @Test
    void givenTwoReviewedEditsFromOneVersion_whenConfirmingConcurrently_thenCommitOnlyOneNewRevision() throws Exception {
        UUID id = submit(); var first = flow().edit(id, "Test first competing edit"); var second = flow().edit(id, "Test second competing edit");
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = workers.submit(() -> first.click("Confirm changes")); var two = workers.submit(() -> second.click("Confirm changes"));
            one.get(10, TimeUnit.SECONDS); two.get(10, TimeUnit.SECONDS);
        }
        assertThat(List.of(first.current, second.current).stream().filter(value -> value instanceof String).count()).isEqualTo(1);
        assertThat(rows().getFirst().getVersion()).isEqualTo(2); assertThat(rows().getFirst().getLatestRevisionNumber()).isEqualTo(2);
        assertThat(latestDescription(id)).isIn("Test first competing edit", "Test second competing edit"); assertThat(firstRequests()).isEqualTo(1);
    }

    @Test
    void givenDeadlineChangedAfterReview_whenConfirming_thenKeepTheReviewedWindowVersion() {
        var client = flow().draft(fixture.race().getId(), PRIVATE); String confirmation = client.button("Confirm report");
        windows.changeDeadline(contextId, NOW.plusSeconds(120), 1, "Test reviewed deadline change", manager);
        client.send(PickGatewayInteraction.Kind.BUTTON, confirmation, Map.of()); assertThat(client.text()).contains("changed");
        assertThat(rows()).isEmpty(); assertThat(firstRequests()).isZero();
    }

    @Test
    void givenAnotherMemberWithTheSameTeamRole_whenReusingPrivateControls_thenDenyWithoutDisclosingTheCase() {
        UUID id = submit(); var owner = flow().command().click("My reports"); String control = owner.message().selection().id();
        var intruder = flow(OTHER, CHANNEL).send(PickGatewayInteraction.Kind.SELECT, control, Map.of("selection", List.of(id.toString())));
        assertThat(intruder.text()).contains("unavailable").doesNotContain(PRIVATE); assertThat(rows().getFirst().getVersion()).isEqualTo(1);
        var otherActor = access.authorizeChannel(GUILD, CHANNEL, OTHER);
        assertThatThrownBy(() -> privateReads.load(otherActor, id)).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class).hasMessageNotContaining(PRIVATE);
    }

    @Test
    void givenReportingPartyChangedAfterReview_whenConfirming_thenDoNotStoreAnUnreviewedAttribution() {
        var client = flow().draft(fixture.race().getId(), PRIVATE); String confirmation = client.button("Confirm report");
        roles.put(USER, Set.of(AWAY_ROLE)); client.send(PickGatewayInteraction.Kind.BUTTON, confirmation, Map.of());
        assertThat(rows()).isEmpty(); assertThat(firstRequests()).isZero(); assertThat(client.text()).contains("unavailable");
    }

    @Test
    void givenChangedRestoreGeneration_whenOldConfirmationArrives_thenPreserveTheDatabase() {
        var client = flow().draft(fixture.race().getId(), PRIVATE); String confirmation = client.button("Confirm report");
        epoch.afterRestore(null); client.send(PickGatewayInteraction.Kind.BUTTON, confirmation, Map.of());
        assertThat(client.text()).contains("unavailable"); assertThat(rows()).isEmpty();
    }

    @Test
    void givenAdmittedPrivateGatewayWork_whenRestoreDrainsWriters_thenWaitForTheCompleteOperation() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var calls = new AtomicInteger();
        when(source.currentRoles(eq(GUILD), eq(CHANNEL), eq(USER))).thenAnswer(call -> {
            if (calls.incrementAndGet() == 1) { entered.countDown(); assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); }
            return Set.of(HOME_ROLE);
        });
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var request = workers.submit(() -> flow().command());
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue(); assertThat(imports.tryLock()).isTrue();
                try {
                    var blocked = flow(OTHER, CHANNEL).command(); assertThat(blocked.text()).contains("restore");
                    var drain = workers.submit(() -> { imports.awaitWritersDrained(); return true; });
                    assertThatThrownBy(() -> drain.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                    release.countDown(); request.get(10, TimeUnit.SECONDS); assertThat(drain.get(10, TimeUnit.SECONDS)).isTrue();
                } finally { imports.unlock(); }
            } finally { release.countDown(); }
        }
        assertThat(rows()).isEmpty();
    }

    @Test
    void givenStandalonePlayoffAndForeignWindow_whenSubmitting_thenUseOnlyItsConcreteRaceAndCanonicalContext() {
        var target = tx.execute(status -> {
            var playoff = helper.createPlayoffInPhase(seasons.findById(fixture.season().getId()).orElseThrow(), "Test intake playoff", 2);
            var matchup = playoff.getRounds().getFirst().getMatchups().getFirst();
            matchup.setTeam1(teams.findById(fixture.homeTeam().getId()).orElseThrow()); matchup.setTeam2(teams.findById(fixture.awayTeam().getId()).orElseThrow());
            playoff.getPhase().setRaceScoring(fixture.matchday().getPhase().getRaceScoring());
            var day = new Matchday(playoff.getPhase(), "Test standalone playoff day", 1); em.persist(day);
            var race = new Race(); race.setMatchday(day); race.setPlayoffMatchup(matchup); race.setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0)); em.persist(race); em.flush();
            return List.of(matchup.getId(), race.getId());
        });
        String playoffChannel = "200000000000000099"; UUID context = contexts.bind(IncidentMatchRef.playoff(target.getFirst()), GUILD, playoffChannel, manager);
        windows.open(context, NOW.plusSeconds(60), 0, manager); UUID ownWindow = tx.execute(status -> windowRows.findByContextId(context).orElseThrow().getId());
        assertThatThrownBy(() -> access.authorizeWindow(GUILD, CHANNEL, USER, ownWindow)).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);
        flow(USER, playoffChannel).draft(target.get(1), PRIVATE).click("Confirm report");
        tx.executeWithoutResult(status -> { var report = reportRows.findByWindowIdOrderByCaseReferenceAsc(ownWindow).getFirst();
            assertThat(report.getRace().getId()).isEqualTo(target.get(1)); assertThat(report.getRace().getMatch()).isNull();
            assertThat(report.getWindow().getContext().getPlayoffMatchup().getId()).isEqualTo(target.getFirst());
        });
        assertThat(rows()).isEmpty();
    }

    @Test
    void givenAmbiguousLegacyChannelLinks_whenStarting_thenDenyRatherThanChoosingTheFirstMatch() {
        tx.executeWithoutResult(status -> { var extra = helper.createFullSeasonFixture("TIRL" + UUID.randomUUID().toString().substring(0, 6)); extras.add(extra); extra.match().setDiscordChannelId(CHANNEL); });
        var denied = flow().command(); assertThat(denied.text()).contains("unavailable"); assertThat(rows()).isEmpty();
    }

    @Test
    void givenLongContextNamesAndTwoLegs_whenOfferingRaces_thenKeepEachConcreteLegVisible() {
        tx.executeWithoutResult(status -> {
            seasons.findById(fixture.season().getId()).orElseThrow().setName("Test " + "long season ".repeat(16));
            var match = matches.findById(fixture.match().getId()).orElseThrow();
            var second = new Race(); second.setMatch(match); second.setMatchday(em.find(Matchday.class, fixture.matchday().getId()));
            second.setDateTime(LocalDateTime.of(2035, 1, 1, 21, 0)); em.persist(second);
        });
        var client = flow().command().click("New report"); var options = client.message().selection().options();
        assertThat(options).hasSize(2); assertThat(options.getFirst().label()).contains("Leg 1"); assertThat(options.getLast().label()).contains("Leg 2");
        assertThat(options.getFirst().label()).isNotEqualTo(options.getLast().label());
    }

    @ParameterizedTest
    @ValueSource(strings = {"team", "parent"})
    void givenRoleConfigurationCommittedWhileTheWriteWaits_whenConfirming_thenRecheckItsCurrentRoleMapping(String roleOwner) throws Exception {
        UUID configuredTeam = fixture.homeTeam().getId();
        if (roleOwner.equals("parent")) {
            var organization = parent(); configuredTeam = organization.getId();
            tx.executeWithoutResult(status -> { var home = teams.findById(fixture.homeTeam().getId()).orElseThrow(); home.setParentTeam(teams.findById(organization.getId()).orElseThrow()); home.setDiscordRoleId(null); });
        }
        UUID changedTeam = configuredTeam;
        var client = flow().draft(fixture.race().getId(), PRIVATE); String confirmation = client.button("Confirm report");
        var locked = new CountDownLatch(1); var preauthorized = new CountDownLatch(1); var once = new java.util.concurrent.atomic.AtomicBoolean();
        when(source.currentRoles(eq(GUILD), eq(CHANNEL), eq(USER))).thenAnswer(call -> {
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                    && !org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                    && once.compareAndSet(false, true)) preauthorized.countDown();
            return Set.of(HOME_ROLE);
        });
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var configuration = workers.submit(() -> tx.execute(status -> {
                seasons.findByIdForUpdate(fixture.season().getId()).orElseThrow(); locked.countDown();
                try { assertThat(preauthorized.await(5, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
                teams.findById(changedTeam).orElseThrow().setDiscordRoleId("700000000000000007");
                return true;
            }));
            try {
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue(); client.send(PickGatewayInteraction.Kind.BUTTON, confirmation, Map.of());
                assertThat(configuration.get(10, TimeUnit.SECONDS)).isTrue();
            } finally { preauthorized.countDown(); }
        }
        String currentRole = tx.execute(status -> teams.findById(changedTeam).orElseThrow().getDiscordRoleId());
        assertThat(currentRole).isEqualTo("700000000000000007"); assertThat(rows()).isEmpty(); assertThat(client.text()).contains("unavailable");
    }
}
