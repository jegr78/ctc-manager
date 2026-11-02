package org.ctc.admin.service;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.SeasonCarPoolService;
import org.ctc.domain.service.RaceService;
import org.ctc.domain.service.PlayoffService;
import org.ctc.TestHelper;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@CtcDevSpringBootContext
@Transactional
@Tag("integration")
class SeasonCarPoolAuditIT {
    @Autowired DataAuditService audit;
    @Autowired SeasonRepository seasons;
    @Autowired CarRepository cars;
    @Autowired SeasonCarPoolEntryRepository entries;
    @Autowired SeasonCarPoolMemberRepository members;
    @Autowired SeasonCarPoolService pools;
    @Autowired EntityManager em;
    @Autowired TestHelper helper;
    @Autowired RaceService raceService;
    @Autowired RaceRepository races;
    @Autowired MatchRepository matches;
    @Autowired PlayoffService playoffService;
    @Autowired MatchdayRepository matchdays;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired org.ctc.domain.service.CarMetadataService metadata;

    @Test
    void givenOverlappingFrozenSnapshots_whenAudited_thenEvidenceIsReportedWithoutRepair() {
        // given
        var season = new Season("Test Inconsistent Pool " + UUID.randomUUID(), 2030, 1);
        season.setCarPoolMode(CarPoolMode.CATEGORY_POOL);
        seasons.saveAndFlush(season);
        var car = cars.saveAndFlush(new Car("Test Audit", "Shared " + UUID.randomUUID()));
        var firstId = pools.saveEntry(season.getId(), null, "First entry", List.of(new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, null, null)));
        var secondId = pools.saveEntry(season.getId(), null, "Second entry", List.of(new SeasonCarPoolService.ClauseInput(CarCategory.GR_4, null, null)));
        for (var id : List.of(firstId, secondId)) {
            var member = new SeasonCarPoolMember();
            member.setEntry(entries.findById(id).orElseThrow());
            member.setCar(car);
            members.saveAndFlush(member);
        }
        season.setCarPoolState(CarPoolState.FROZEN);
        seasons.saveAndFlush(season);
        em.clear();
        long count = members.count();
        // when
        var findings = audit.audit().sections().stream().filter(section -> section.category().name().equals("CAR_POOL"))
                .flatMap(section -> section.findings().stream()).toList();
        // then
        assertThat(findings).anySatisfy(finding -> {
            assertThat(finding.subject()).contains(season.getId().toString());
            assertThat(finding.evidence()).contains("overlap", car.getId().toString(), "First entry", "Second entry");
        });
        assertThat(members.count()).isEqualTo(count);
        assertThat(seasons.findById(season.getId()).orElseThrow().getCarPoolState()).isEqualTo(CarPoolState.FROZEN);
    }
    @Test
    void givenNativeSelectionAndHistoricalDuplicate_whenAudited_thenReportConsumptionWithoutRepairingEitherRace() {
        // given
        var fixture = nativeFixture(false);
        em.flush();
        em.clear();
        assertThat(poolFindings()).noneMatch(finding -> finding.subject().contains(fixture.race().toString()));
        var first = races.findById(fixture.race()).orElseThrow();
        var duplicate = new Race();
        duplicate.setMatchday(first.getMatchday());
        duplicate.setMatch(first.getMatch());
        duplicate.setCar(first.getCar());
        duplicate.setCarPoolEntry(first.getCarPoolEntry());
        races.saveAndFlush(duplicate);
        UUID duplicateId = duplicate.getId();
        em.clear();
        long count = races.count();
        // when
        var findings = poolFindings();
        // then
        assertThat(findings).anySatisfy(finding -> {
            assertThat(finding.subject()).containsAnyOf(fixture.race().toString(), duplicateId.toString());
            assertThat(finding.evidence()).contains("Duplicate entry consumption");
        });
        assertThat(races.count()).isEqualTo(count);
        for (var id : List.of(fixture.race(), duplicateId)) {
            assertThat(races.findById(id).orElseThrow().getCarPoolEntry().getId()).isEqualTo(fixture.entry());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"regular-bye", "playoff-bye", "fixed-playoff-bye", "missing-home"})
    void givenConfiguredByeOrMissingHome_whenAudited_thenMirrorSelectionRejectionWithoutChangingHistory(String kind) {
        // given
        var fixture = nativeFixture(kind.startsWith("fixed"));
        var race = races.findById(fixture.race()).orElseThrow();
        if (kind.equals("regular-bye")) {
            matches.findById(fixture.match()).orElseThrow().setBye(true);
        } else {
            var playoff = playoffService.createPlayoff(fixture.season(), "Test audit playoff " + UUID.randomUUID(), 2);
            var matchup = playoff.getRounds().getFirst().getMatchups().getFirst();
            boolean bye = !kind.equals("missing-home");
            matchup.setTeam1(bye ? race.getHomeTeam() : null);
            matchup.setTeam2(bye ? null : race.getAwayTeam());
            matchup.setBye(bye);
            var md = matchdays.save(new Matchday(playoff.getPhase(), "Test audit playoff leg", 100));
            race.setMatch(null);
            race.setMatchday(md);
            race.setPlayoffMatchup(matchup);
        }
        em.flush();
        em.clear();
        long count = races.count();
        // when
        var findings = poolFindings();
        // then
        assertThat(findings).anySatisfy(finding -> {
            assertThat(finding.subject()).contains(fixture.race().toString());
            assertThat(finding.evidence()).contains(kind.equals("missing-home") ? "home team" : "bye");
        });
        assertThat(races.count()).isEqualTo(count);
        var saved = races.findById(fixture.race()).orElseThrow();
        assertThat(saved.getCar().getId()).isEqualTo(fixture.car());
        if (fixture.entry() == null) assertThat(saved.getCarPoolEntry()).isNull();
        else assertThat(saved.getCarPoolEntry().getId()).isEqualTo(fixture.entry());
        if (kind.equals("missing-home")) assertThat(saved.getHomeTeam()).isNull();
        else assertThat(saved.isBye() || saved.getPlayoffMatchup() != null && saved.getPlayoffMatchup().isBye()).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"foreign-entry", "nonmember-car", "car-without-entry", "entry-without-car", "draft-reference"})
    void givenHistoricalSelectionMismatch_whenAudited_thenReportExactIntegrityProblemWithoutRepair(String kind) {
        // given
        var fixture = nativeFixture(false);
        var race = races.findById(fixture.race()).orElseThrow();
        String expected;
        switch (kind) {
            case "foreign-entry" -> {
                var other = helper.createSeason("Test audit foreign " + UUID.randomUUID());
                pools.changeMode(other.getId(), CarPoolMode.CATEGORY_POOL);
                var otherEntry = pools.saveEntry(other.getId(), null, "Test foreign ownership", List.of(
                        new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, null, 9893)));
                pools.freeze(other.getId());
                race.setCarPoolEntry(entries.findById(otherEntry).orElseThrow());
                expected = "different season";
            }
            case "nonmember-car" -> {
                race.setCar(cars.saveAndFlush(new Car("Test audit nonmember", UUID.randomUUID().toString())));
                expected = "not a frozen entry member";
            }
            case "car-without-entry" -> { race.setCarPoolEntry(null); expected = "without a pool entry"; }
            case "entry-without-car" -> { race.setCar(null); expected = "not a frozen entry member"; }
            default -> { seasons.findById(fixture.season()).orElseThrow().setCarPoolState(CarPoolState.DRAFT); expected = "outside a frozen category pool"; }
        }
        em.flush();
        var before = jdbc.queryForMap("SELECT * FROM races WHERE id=?", fixture.race());
        var seasonBefore = jdbc.queryForMap("SELECT * FROM seasons WHERE id=?", fixture.season());
        long membersBefore = members.count();
        // when
        var findings = poolFindings();
        // then
        assertThat(findings).anySatisfy(finding -> {
            assertThat(finding.subject()).contains(fixture.race().toString());
            assertThat(finding.evidence()).contains(expected);
        });
        em.flush();
        assertThat(jdbc.queryForMap("SELECT * FROM races WHERE id=?", fixture.race())).isEqualTo(before);
        assertThat(jdbc.queryForMap("SELECT * FROM seasons WHERE id=?", fixture.season())).isEqualTo(seasonBefore);
        assertThat(members.count()).isEqualTo(membersBefore);
    }

    @Test
    void givenUsedFrozenMemberYearCleared_whenAudited_thenExplainFutureEligibilityWithoutInvalidatingHistory() {
        // given
        var fixture = nativeFixture(false);
        var car = cars.findById(fixture.car()).orElseThrow();
        metadata.applyManual(car, car.getCategory(), car.getDrivetrain(), null,
                car.isCategoryOverride(), car.isDrivetrainOverride(), true);
        em.flush();
        em.clear();
        var memberIds = entries.findById(fixture.entry()).orElseThrow().getMembers().stream().map(SeasonCarPoolMember::getId).toList();
        var before = jdbc.queryForMap("SELECT * FROM cars WHERE id=?", fixture.car());
        // when
        var report = audit.audit();
        // then
        assertThat(report.findings(org.ctc.admin.dto.DataAuditFinding.Category.CAR_POOL))
                .noneMatch(finding -> finding.subject().contains(fixture.race().toString()) || finding.subject().contains(fixture.season().toString()));
        assertThat(report.findings(org.ctc.admin.dto.DataAuditFinding.Category.CAR_ELIGIBILITY)).anySatisfy(finding -> {
            assertThat(finding.subject()).contains(fixture.car().toString());
            assertThat(finding.evidence()).contains("draft and future pools", "model year is unknown");
        });
        em.flush();
        assertThat(jdbc.queryForMap("SELECT * FROM cars WHERE id=?", fixture.car())).isEqualTo(before);
        var frozen = entries.findById(fixture.entry()).orElseThrow();
        assertThat(frozen.getMembers()).extracting(SeasonCarPoolMember::getId).containsExactlyElementsOf(memberIds);
        assertThat(frozen.getClauses().getFirst().getModelYear()).isEqualTo(9893);
        assertThat(races.findById(fixture.race()).orElseThrow().getCarPoolEntry().getId()).isEqualTo(fixture.entry());
    }

    private List<org.ctc.admin.dto.DataAuditFinding> poolFindings() {
        return audit.audit().sections().stream().filter(section -> section.category().name().equals("CAR_POOL"))
                .flatMap(section -> section.findings().stream()).toList();
    }

    private NativeFixture nativeFixture(boolean fixed) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var graph = helper.createFullSeasonFixture("TCA" + suffix);
        var car = new Car("Test native audit " + suffix, "MR");
        car.setCategory(CarCategory.GR_3);
        car.setDrivetrain(Drivetrain.MR);
        car.setModelYear(9893);
        car.setModelYearOverride(true);
        car.setModelYearSource("Test fixture");
        car.setModelYearEvidence("Test accepted local year");
        cars.saveAndFlush(car);
        UUID entry = null;
        if (fixed) graph.season().getCars().add(car);
        else {
            pools.changeMode(graph.season().getId(), CarPoolMode.CATEGORY_POOL);
            entry = pools.saveEntry(graph.season().getId(), null, "Test native audit entry", List.of(
                    new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, null, 9893)));
            pools.freeze(graph.season().getId());
        }
        assertThat(raceService.saveRace(graph.race().getId(), graph.matchday().getId(), graph.homeTeam().getId(), graph.awayTeam().getId(),
                null, car.getId(), null, 5, null, null, null, null, null, TimeProgressionMode.MULTIPLIER, 0,
                null, null, null, null, entry).success()).isTrue();
        return new NativeFixture(graph.season().getId(), graph.race().getId(), graph.match().getId(), car.getId(), entry);
    }

    private record NativeFixture(UUID season, UUID race, UUID match, UUID car, UUID entry) {}

}
