package org.ctc.domain.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.picks.*;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.TeamPickChannelBinding;
import org.ctc.domain.picks.ChannelPickActor;
import org.ctc.domain.picks.PickChannelPermissionSource;
import org.ctc.domain.repository.TeamPickChannelBindingRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.ctc.domain.picks.AuthorizedPickActor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("integration")
class DiscordPicksRestoreAdmissionIT extends PickPersistenceIT {
    static final String GUILD = "100000000000000001", CHANNEL = "200000000000000002";
    @Autowired PickAccessService access;
    @Autowired PickTargetService targets;
    @Autowired PickSessionEpoch epoch;
    @Autowired ImportLockService imports;
    @Autowired TeamPickChannelBindingRepository bindings;
    @Autowired DiscordGlobalConfigRepository configs;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PickChannelPermissionSource permissions;
    @MockitoSpyBean PickTargetService gatewayTargets;
    PickGatewayDispatcher dispatcher;
    UUID bindingId;
    Long configId;
    String originalGuild;
    boolean createdConfig;
    final java.util.concurrent.atomic.AtomicReference<RuntimeException> workFailure = new java.util.concurrent.atomic.AtomicReference<>();

    @BeforeEach
    void bindCurrentChannel() {
        // given
        tx.executeWithoutResult(status -> {
            var config = configs.findFirstByOrderByIdAsc();
            createdConfig = config == null;
            if (config == null) config = new DiscordGlobalConfig();
            originalGuild = config.getGuildId(); config.setGuildId(GUILD);
            configId = configs.saveAndFlush(config).getId();
            bindingId = bindings.saveAndFlush(new TeamPickChannelBinding(
                    seasons.findById(fixture.season().getId()).orElseThrow(), teams.findById(fixture.homeTeam().getId()).orElseThrow(),
                    GUILD, CHANNEL)).getId();
        });
        when(permissions.hasCurrentPrivateAccess(GUILD, CHANNEL, "12345")).thenReturn(true);
        actor = access.authorizeTarget(access.authorizeChannel(GUILD, CHANNEL, "12345"), fixture.race().getId());
    }

    @AfterEach
    void stopGatewayAndRestoreConfiguration() {
        if (dispatcher != null) dispatcher.stop();
        imports.unlock();
        tx.executeWithoutResult(status -> {
            if (createdConfig) configs.deleteById(configId);
            else configs.findById(configId).orElseThrow().setGuildId(originalGuild);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"permission", "binding", "guild", "restore-generation"})
    void givenAuthorityRevokedAfterSelection_whenConfirmingThroughRealTransaction_thenSaveNoRevision(String change) {
        // given
        if (change.equals("permission")) when(permissions.hasCurrentPrivateAccess(GUILD, CHANNEL, "12345")).thenReturn(false);
        if (change.equals("binding")) tx.executeWithoutResult(status -> bindings.deleteById(bindingId));
        if (change.equals("guild")) tx.executeWithoutResult(status -> configs.findById(configId).orElseThrow().setGuildId("999"));
        if (change.equals("restore-generation")) epoch.afterRestore(null);
        var request = UUID.randomUUID();
        // when / then
        assertThatThrownBy(() -> submissions.submit(fixture.race().getId(), 0, request, actor, payload))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("unavailable");
        tx.executeWithoutResult(status -> assertThat(revisions.findByRequestId(request)).isEmpty());
        assertWriterReleased();
    }

    @Test
    void givenRevokedOrForgedChooserActor_whenListing_thenUseCurrentBindingAndAccess() {
        // given
        var forged = new ChannelPickActor(GUILD, CHANNEL, "12345", java.util.Set.of(
                new ChannelPickActor.Binding(fixture.season().getId(), fixture.awayTeam().getId())));
        // when / then
        assertThat(targets.listHomeRaces(forged)).extracting(org.ctc.domain.picks.HomeRaceTarget::raceId)
                .contains(fixture.race().getId());
        when(permissions.hasCurrentPrivateAccess(GUILD, CHANNEL, "12345")).thenReturn(false);
        assertThatThrownBy(() -> targets.listHomeRaces(forged)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenAdmittedGatewayWriteAtCommit_whenRestoreStarts_thenDrainAfterCommitAndRejectNewInteractionPrivately() throws Exception {
        // given
        var atCommit = new CountDownLatch(1); var release = new CountDownLatch(1);
        var request = UUID.randomUUID();
        pauseCommit(atCommit, release);
        var messages = new CopyOnWriteArrayList<String>(); var replied = new CountDownLatch(1);
        startGateway(request);
        dispatcher.accept(GUILD, interaction(privateResponse(messages, replied)));
        assertThat(atCommit.await(5, TimeUnit.SECONDS)).withFailMessage(() -> "Commit was not reached: " + workFailure.get()).isTrue();
        assertThat(messages).isEmpty();
        var waiting = Executors.newSingleThreadExecutor();
        try {
            assertThat(imports.tryLock()).isTrue();
            var drained = waiting.submit(() -> { imports.awaitWritersDrained(); return true; });
            assertThatThrownBy(() -> drained.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            var refused = mock(PickGatewayResponse.class);
            // when
            dispatcher.accept(GUILD, interaction(refused));
            // then
            verify(refused).replyPrivate("A backup restore is in progress. Try again later.");
            release.countDown();
            assertThat(drained.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(replied.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(messages).containsExactly("Saved revision 1");
            tx.executeWithoutResult(status -> assertThat(revisions.findByRequestId(request)).isPresent());
        } finally {
            release.countDown(); waiting.shutdownNow(); imports.unlock();
        }
    }

    @Test
    void givenDatabaseWriteThatFailsAtCommit_whenGatewayCompletes_thenReturnNoSavedReceiptAndReleaseAdmission() throws Exception {
        // given
        var request = UUID.randomUUID(); var flushed = new AtomicBoolean();
        doAnswer(invocation -> {
            var saved = invocation.callRealMethod();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    entityManager.flush();
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM matchday_pick_revisions WHERE request_id=?", Long.class, request)).isEqualTo(1);
                    flushed.set(true);
                    throw new IllegalStateException("Test commit failure with private input");
                }
            });
            return saved;
        }).when(AopTestUtils.<PickTargetService>getUltimateTargetObject(gatewayTargets)).lockForTeam(any(UUID.class), any(AuthorizedPickActor.class));
        var messages = new CopyOnWriteArrayList<String>(); var replied = new CountDownLatch(1);
        startGateway(request);
        // when
        dispatcher.accept(GUILD, interaction(privateResponse(messages, replied)));
        // then
        assertThat(replied.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(flushed).withFailMessage(() -> "Flush was not reached: " + workFailure.get()).isTrue();
        assertThat(messages).containsExactly("This request could not be completed. Check your current submission before retrying.");
        tx.executeWithoutResult(status -> assertThat(revisions.findByRequestId(request)).isEmpty());
        assertWriterReleased();
    }

    @Test
    void givenActiveAndQueuedGatewayWork_whenStopping_thenRollBackInterruptedWriteAndReleaseAllAdmission() throws Exception {
        // given
        var atCommit = new CountDownLatch(1); var release = new CountDownLatch(1);
        var request = UUID.randomUUID(); pauseCommit(atCommit, release);
        var messages = new CopyOnWriteArrayList<String>(); var replied = new CountDownLatch(1);
        startGateway(request);
        dispatcher.accept(GUILD, interaction(privateResponse(messages, replied)));
        assertThat(atCommit.await(5, TimeUnit.SECONDS)).withFailMessage(() -> "Commit was not reached: " + workFailure.get()).isTrue();
        dispatcher.accept(GUILD, interaction(privateResponse(new CopyOnWriteArrayList<>(), new CountDownLatch(1))));
        try {
            // when
            dispatcher.stop();
            // then
            assertWriterReleased();
            tx.executeWithoutResult(status -> assertThat(revisions.findByRequestId(request)).isEmpty());
            assertThat(messages).noneMatch(message -> message.startsWith("Saved"));
        } finally { release.countDown(); }
    }

    void startGateway(UUID request) {
        var settings = new PickGatewaySettings(); settings.setWorkers(1); settings.setQueueCapacity(1);
        dispatcher = new PickGatewayDispatcher(settings, (event, reply) -> reply.deferPrivate(continuation -> {
            try {
                var receipt = submissions.submit(fixture.race().getId(), 0, request, actor, payload);
                continuation.replyPrivate("Saved revision " + receipt.revisionNumber());
            } catch (RuntimeException failure) {
                workFailure.set(failure); throw failure;
            }
        }), imports, clock, epoch);
        dispatcher.start();
    }

    PickGatewayInteraction interaction(PickGatewayResponse response) {
        return new PickGatewayInteraction(UUID.randomUUID().toString(), GUILD, CHANNEL, "12345",
                PickGatewayInteraction.Kind.COMMAND, "picks", Map.of(), clock.instant(), response);
    }

    PickGatewayResponse privateResponse(List<String> messages, CountDownLatch replied) {
        var response = mock(PickGatewayResponse.class);
        when(response.deferPrivate()).thenReturn(CompletableFuture.completedFuture(message -> {
            messages.add(message); replied.countDown();
        }));
        return response;
    }

    void pauseCommit(CountDownLatch entered, CountDownLatch release) {
        doAnswer(invocation -> {
            var saved = invocation.callRealMethod();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    entered.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test commit was not released");
                    } catch (InterruptedException stopped) {
                        Thread.currentThread().interrupt(); throw new IllegalStateException("Test commit interrupted");
                    }
                }
            });
            return saved;
        }).when(AopTestUtils.<PickTargetService>getUltimateTargetObject(gatewayTargets)).lockForTeam(any(UUID.class), any(AuthorizedPickActor.class));
    }

    void assertWriterReleased() {
        assertThat(imports.tryLock()).isTrue();
        try { assertThatCode(imports::awaitWritersDrained).doesNotThrowAnyException(); }
        finally { imports.unlock(); }
    }
}
