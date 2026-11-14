package org.ctc.admin.service;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.admin.dto.DataAuditFinding;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@CtcDevSpringBootContext
@Transactional
@Tag("integration")
class PickIntegrityAuditIT {
    @Autowired TestHelper helper;
    @Autowired DataAuditService audit;
    @Autowired CarRepository cars;
    @Autowired TrackRepository tracks;
    @Autowired RaceRepository races;
    @Autowired MatchdayPickRepository picks;
    @Autowired MatchdayPickRevisionRepository revisions;
    @Autowired EntityManager em;
    @Autowired TeamPickChannelBindingRepository bindings;
    @Autowired JdbcTemplate jdbc;
    TestHelper.SeasonFixture fixture;
    PickPayload payload;

    @BeforeEach
    void setUp() {
        // given
        fixture = helper.createFullSeasonFixture("TPA" + UUID.randomUUID().toString().substring(0, 7));
        fixture.race().setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0));
        fixture.matchday().setPickDeadline(LocalDateTime.of(2035, 1, 1, 12, 0));
            fixture.matchday().setPicksOpen(true);
        var car = cars.save(new Car("Test pick audit", UUID.randomUUID().toString()));
        var track = tracks.save(new Track("Test pick audit " + UUID.randomUUID()));
        fixture.season().getCars().add(car);
        fixture.season().getTracks().add(track);
        payload = new PickPayload(track.getId(), car.getId(), null, 12, 2, 3, 4, PickInitialFuel.DEFAULT, 1,
                TimeProgressionMode.MULTIPLIER, 0, "Test private weather must stay out of findings", "Sunset", Set.of(PickTyre.RS), Set.of());
        persistHistory(fixture.race());
        fixture.matchday().setPickDeadline(LocalDateTime.of(2020, 1, 1, 12, 0));
        em.flush();
    }

    @Test
    void givenValidClosedPendingHistory_whenAuditing_thenDoNotInventExpiryErrorsOrMutateState() {
        // given
        var pick = picks.findByRaceId(fixture.race().getId()).orElseThrow();
        pick.returnForCorrection();
        // The synthetic history must include every explicit management transition.
        var decision = new MatchdayPickDecision(pick, PickDecisionKind.RETURN, 2, "test-admin", "Confirm setup", Instant.parse("2026-11-02T19:01:00Z"));
        em.persist(decision);
        em.flush();
        long count = picks.count();
        // when / then
        assertThat(findings()).isEmpty();
        assertThat(picks.count()).isEqualTo(count);
        assertThat(pick.getVersion()).isEqualTo(2);
        assertThat(pick.isManualOpen()).isTrue();
    }

    @Test
    void givenHistoricalOpponentMismatch_whenAuditing_thenReportOriginalTargetWithoutExposingPayloadOrRepairing() {
        // given
        var replacement = helper.createTeam("Test wrong opponent " + UUID.randomUUID(), "TA" + UUID.randomUUID().toString().substring(0, 7));
        fixture.match().setAwayTeam(replacement);
        em.flush();
        long count = revisions.count();
        // when
        var findings = findings();
        // then
        assertThat(findings).anySatisfy(finding -> {
            assertThat(finding.evidence()).contains(fixture.race().getId().toString(), "identity");
            assertThat(finding.toString()).doesNotContain(payload.weather());
            assertThat(finding.resolution()).isEqualTo(DataAuditFinding.Resolution.AMBIGUOUS);
        });
        assertThat(revisions.count()).isEqualTo(count);
        assertThat(fixture.match().getAwayTeam().getId()).isEqualTo(replacement.getId());
    }

    @Test
    void givenDuplicatePendingReservation_whenAuditing_thenReportConflictWithoutDroppingEitherRevision() {
        // given
        var other = new Race();
        other.setMatchday(fixture.matchday());
        other.setMatch(fixture.match());
        other.setDateTime(fixture.race().getDateTime().plusHours(1));
        other = races.save(other);
        persistHistory(other);
        em.flush();
        long count = revisions.count();
        // when / then
        assertThat(findings()).anySatisfy(finding -> {
            assertThat(finding.evidence()).contains("reservation");
            assertThat(finding.toString()).doesNotContain(payload.weather());
        });
        assertThat(revisions.count()).isEqualTo(count);
    }

    @Test
    void givenBindingReusedAsSharedMatchChannel_whenAuditing_thenReportConflictWithoutRepairOrExternalLookup() {
        // given
        var binding = bindings.save(new TeamPickChannelBinding(fixture.season(), fixture.homeTeam(), "100", "200"));
        fixture.match().setDiscordChannelId("200");
        em.flush();
        // when / then
        assertThat(findings()).anySatisfy(finding -> assertThat(finding.evidence()).contains("channel binding"));
        assertThat(bindings.findById(binding.getId())).isPresent();
        assertThat(fixture.match().getDiscordChannelId()).isEqualTo("200");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-03-29T02:30:00", "2026-10-25T02:30:00"})
    void givenUnreviewedAmbiguousBerlinDeadline_whenAuditing_thenReportTheTargetWithoutOpeningOrChangingHistory(String local) {
        var deadline = LocalDateTime.parse(local);
        fixture.matchday().setPickDeadline(deadline);
        em.flush();
        var before = picks.findByRaceId(fixture.race().getId()).orElseThrow();
        long count = revisions.count();

        assertThat(findings()).anySatisfy(finding -> {
            assertThat(finding.subject()).contains(fixture.race().getId().toString());
            assertThat(finding.evidence()).contains("Europe/Berlin", "management review");
            assertThat(finding.toString()).doesNotContain(payload.weather());
        });

        assertThat(before.getVersion()).isEqualTo(1);
        assertThat(before.isManualOpen()).isFalse();
        assertThat(before.isManualClosed()).isFalse();
        assertThat(revisions.count()).isEqualTo(count);
        assertThat(fixture.matchday().getPickDeadline()).isEqualTo(deadline);
    }

    @ParameterizedTest
    @ValueSource(strings = {"latest_revision_number", "approved_revision_number"})
    void givenCorruptStoredRevisionPointer_whenAuditing_thenReportItWithoutRepairingTheRecord(String column) {
        var id = picks.findByRaceId(fixture.race().getId()).orElseThrow().getId();
        String sql = column.equals("latest_revision_number")
                ? "UPDATE matchday_picks SET latest_revision_number=99 WHERE id=?"
                : "UPDATE matchday_picks SET state='APPROVED', approved_revision_number=1 WHERE id=?";
        jdbc.update(sql, id);
        em.clear();
        long count = revisions.count();

        assertThat(findings()).anySatisfy(finding -> {
            assertThat(finding.evidence()).contains("immutable history");
            assertThat(finding.toString()).doesNotContain(payload.weather());
        });

        var unchanged = picks.findById(id).orElseThrow();
        assertThat(unchanged.getLatestRevisionNumber()).isEqualTo(column.equals("latest_revision_number") ? 99 : 1);
        assertThat(unchanged.getApprovedRevisionNumber()).isEqualTo(column.equals("approved_revision_number") ? 1 : null);
        assertThat(revisions.count()).isEqualTo(count);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RETURN", "CLOSE"})
    void givenReturnedOrClosedAmbiguousDeadline_whenAuditing_thenPreserveHistoryAndReportAnUnresolvedReturnCutoff(String action) {
        fixture.matchday().setPickDeadline(LocalDateTime.of(2026, 10, 25, 2, 30));
        var pick = picks.findByRaceId(fixture.race().getId()).orElseThrow();
        if (action.equals("RETURN")) pick.returnForCorrection(); else pick.close();
        em.persist(new MatchdayPickDecision(pick, PickDecisionKind.valueOf(action), 2, "test-admin", "Test reviewed deadline", Instant.parse("2026-11-02T19:01:00Z")));
        em.flush();

        if (action.equals("RETURN")) {
            assertThat(findings()).anySatisfy(finding -> assertThat(finding.evidence()).contains("Deadline", "management review"));
        } else assertThat(findings()).isEmpty();
        assertThat(pick.getVersion()).isEqualTo(2);
        assertThat(pick.isManualOpen()).isEqualTo(action.equals("RETURN"));
        assertThat(pick.isManualClosed()).isEqualTo(action.equals("CLOSE"));
    }

    @Test
    void givenSnapshotWhoseDeliveryIsUnconfirmed_whenAuditing_thenReportItWithoutReleasingOrRepairingAnything() {
        var legacy = helper.createFullSeasonFixture("TestPubAudit" + UUID.randomUUID().toString().substring(0, 6));
        legacy.season().getCars().add(cars.findById(payload.carId()).orElseThrow());
        legacy.season().getTracks().add(tracks.findById(payload.trackId()).orElseThrow());
        legacy.race().setCar(cars.findById(payload.carId()).orElseThrow());
        legacy.race().setTrack(tracks.findById(payload.trackId()).orElseThrow());
        legacy.race().setSettings(helper.completeRaceSettings(legacy.race()));
        var at = Instant.parse("2026-11-02T19:00:00Z");
        var attempt = new PickPublicationAttempt(new PickPublicationAttempt.Capture(UUID.randomUUID(), legacy.race(), 1, 0,
                PickPublicationOrigin.LEGACY, PickPublicationOutput.SETTINGS, PickPublicationContent.from(legacy.race()),
                PickDisclosureField.all(), "test-admin", "200000000000000020", "300000000000000030", null,
                "a".repeat(64), "{}", UUID.randomUUID() + ":0", at));
        attempt.confirm("400000000000000040", at.plusSeconds(1));
        em.persist(attempt);
        em.persist(new PickPublicationSnapshot(attempt));
        em.flush();
        var id = attempt.getId();
        jdbc.update("UPDATE pick_publication_attempts SET state='UNCERTAIN', active_race_id=race_id, failure_code='TEST_UNKNOWN' WHERE id=?", id);
        em.clear();

        assertThat(findings()).anySatisfy(finding -> {
            assertThat(finding.evidence()).contains("Snapshot requires unique successful disclosed content");
            assertThat(finding.toString()).doesNotContain(payload.weather());
        });

        assertThat(em.find(PickPublicationAttempt.class, id).getState()).isEqualTo(PickPublicationState.UNCERTAIN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pick_publication_snapshots WHERE attempt_id=?", Long.class, id)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"legacy", "completed"})
    void givenLegacyOrCompletedTargetWithAmbiguousDeadline_whenAuditing_thenKeepItsExistingLifecycle(String kind) {
        if (kind.equals("legacy")) {
            var legacy = helper.createFullSeasonFixture("TestLegacyDeadline" + UUID.randomUUID().toString().substring(0, 6));
            legacy.matchday().setPickDeadline(LocalDateTime.of(2026, 10, 25, 2, 30));
        } else {
            fixture.matchday().setPickDeadline(LocalDateTime.of(2026, 10, 25, 2, 30));
            fixture.match().setHomeScore(0);
        }
        em.flush();
        long count = revisions.count();

        assertThat(assertDoesNotThrow(this::findings)).isEmpty();

        assertThat(revisions.count()).isEqualTo(count);
        assertThat(picks.findByRaceId(fixture.race().getId()).orElseThrow().getState()).isEqualTo(PickState.SUBMITTED);
    }

    @Test
    void givenOpenTargetWithoutDeadline_whenAuditing_thenDoNotInventACutoffOrAnOpening() {
        fixture.matchday().setPickDeadline(null);
        em.flush();

        assertThat(assertDoesNotThrow(this::findings)).isEmpty();

        assertThat(fixture.matchday().getPickDeadline()).isNull();
        var pick = picks.findByRaceId(fixture.race().getId()).orElseThrow();
        assertThat(pick.isManualOpen()).isFalse();
        assertThat(pick.isManualClosed()).isFalse();
        assertThat(pick.getVersion()).isEqualTo(1);
    }

    private void persistHistory(Race race) {
        var pick = new MatchdayPick(race, fixture.season(), fixture.homeTeam());
        pick.submit();
        picks.save(pick);
        revisions.save(new MatchdayPickRevision(pick, 1, payload, Instant.parse("2026-11-02T19:00:00Z"), "12345", UUID.randomUUID(), 1));
    }
    private java.util.List<DataAuditFinding> findings() {
        return audit.audit().sections().stream().filter(section -> section.category().name().equals("PRIVATE_PICKS"))
                .flatMap(section -> section.findings().stream()).toList();
    }
}
