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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

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
    TestHelper.SeasonFixture fixture;
    PickPayload payload;

    @BeforeEach
    void setUp() {
        // given
        fixture = helper.createFullSeasonFixture("TPA" + UUID.randomUUID().toString().substring(0, 7));
        fixture.race().setPicksManaged(true);
        fixture.race().setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0));
        fixture.matchday().setPickDeadline(LocalDateTime.of(2035, 1, 1, 12, 0));
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
        other.setPicksManaged(true);
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
