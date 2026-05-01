package org.ctc.domain.service;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// LENIENT: existing tests stub seasonRepository.findById which is no longer invoked by the
// @Deprecated calculateStandings(UUID seasonId) bridge (now delegates to seasonPhaseService).
// UnnecessaryStubbingException would break regression tests. Lenient mode preserves all
// existing test logic while allowing the new phase-aware tests to work alongside.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StandingsServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private RaceRepository raceRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private SeasonRepository seasonRepository;

    @Mock private SeasonPhaseService seasonPhaseService;

    @Mock private PhaseTeamRepository phaseTeamRepository;

    @InjectMocks
    private StandingsService standingsService;

    private RaceScoring raceScoring;
    private MatchScoring matchScoring;
    private Season season;
    private SeasonPhase regularPhase; // bridge target for legacy seasonId-overload tests
    private Team tnr;
    private Team p1r;
    private Team clr;

    @BeforeEach
    void setUp() {
        raceScoring = new RaceScoring("CTC Standard", "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2);
        matchScoring = new MatchScoring("Standard 3-1-0", 3, 1, 0);

        // Phase 61 MIGR-06: scoring lives on the SeasonPhase only (regularPhase below).
        season = new Season("2026");
        season.setId(UUID.randomUUID());

        tnr = new Team("The Neutrals Racing", "TNR");
        tnr.setId(UUID.randomUUID());

        p1r = new Team("Project One Racing", "P1R");
        p1r.setId(UUID.randomUUID());

        clr = new Team("Community League Racing", "CLR");
        clr.setId(UUID.randomUUID());

        // Bridge setup for @Deprecated calculateStandings(UUID seasonId) — routes through
        // seasonPhaseService.findRegularPhase → canonical calculateStandings(phaseId, null).
        // Uses a seasonId→phaseId cache so each season gets a consistent phaseId.
        // Lenient mode (@MockitoSettings LENIENT) covers unused seasonRepository.findById stubs
        // from the 22 legacy tests that no longer trigger that code path.
        //
        // IMPORTANT: phases are built dynamically (not cached by value) so that per-test changes
        // to season.setMatchScoring/setRaceScoring are reflected when findById is called.
        java.util.Map<UUID, UUID> seasonToPhaseId = new java.util.HashMap<>();
        regularPhase = PhaseTestFixtures.regularPhase(season, raceScoring, matchScoring);
        seasonToPhaseId.put(season.getId(), regularPhase.getId());

        // Phase 61 MIGR-06: scoring lives on SeasonPhase. Build phases from the test-class-level
        // raceScoring/matchScoring fields (per-test overrides assign new values to those fields).
        lenient().when(seasonPhaseService.findByType(any(UUID.class), any())).thenAnswer(inv -> {
            UUID sid = inv.getArgument(0);
            var seasonOpt = seasonRepository.findById(sid);
            if (seasonOpt.isPresent()) {
                var s = seasonOpt.get();
                UUID pid = seasonToPhaseId.computeIfAbsent(sid, id -> UUID.randomUUID());
                var phase = PhaseTestFixtures.regularPhase(s, raceScoring, matchScoring);
                phase.setId(pid);
                return Optional.of(phase);
            }
            if (sid.equals(season.getId())) {
                var phase = PhaseTestFixtures.regularPhase(season, raceScoring, matchScoring);
                phase.setId(regularPhase.getId());
                return Optional.of(phase);
            }
            return Optional.empty();
        });

        lenient().when(seasonPhaseService.findRegularPhase(any(UUID.class))).thenAnswer(inv -> {
            UUID sid = inv.getArgument(0);
            var seasonOpt = seasonRepository.findById(sid);
            if (seasonOpt.isPresent()) {
                var s = seasonOpt.get();
                UUID pid = seasonToPhaseId.computeIfAbsent(sid, id -> UUID.randomUUID());
                var phase = PhaseTestFixtures.regularPhase(s, raceScoring, matchScoring);
                phase.setId(pid);
                return phase;
            }
            if (sid.equals(season.getId())) {
                var phase = PhaseTestFixtures.regularPhase(season, raceScoring, matchScoring);
                phase.setId(regularPhase.getId());
                return phase;
            }
            throw new IllegalStateException("No REGULAR phase for season " + sid);
        });

        lenient().when(seasonPhaseService.findById(any(UUID.class))).thenAnswer(inv -> {
            UUID pid = inv.getArgument(0);
            for (var entry : seasonToPhaseId.entrySet()) {
                if (entry.getValue().equals(pid)) {
                    UUID sid = entry.getKey();
                    var seasonOpt = seasonRepository.findById(sid);
                    if (seasonOpt.isPresent()) {
                        var s = seasonOpt.get();
                        var phase = PhaseTestFixtures.regularPhase(s, raceScoring, matchScoring);
                        phase.setId(pid);
                        return phase;
                    }
                    if (sid.equals(season.getId())) {
                        var phase = PhaseTestFixtures.regularPhase(season, raceScoring, matchScoring);
                        phase.setId(pid);
                        return phase;
                    }
                }
            }
            return null; // not found — caller should have set up their own stub
        });

        // Team roster: phaseTeamRepository.findByPhaseId → derive from the season's active teams
        lenient().when(phaseTeamRepository.findByPhaseId(any(UUID.class))).thenAnswer(inv -> {
            UUID pid = inv.getArgument(0);
            for (var entry : seasonToPhaseId.entrySet()) {
                if (entry.getValue().equals(pid)) {
                    UUID sid = entry.getKey();
                    var seasonOpt = seasonRepository.findById(sid);
                    Season targetSeason = seasonOpt.orElse(sid.equals(season.getId()) ? season : null);
                    if (targetSeason != null) {
                        final Season ts = targetSeason;
                        return ts.getActiveTeams().stream()
                                .map(t -> {
                                    // Phase 61 MIGR-06: scoring lives on the phase; reuse class-level fields.
                                    var ph = PhaseTestFixtures.regularPhase(ts, raceScoring, matchScoring);
                                    ph.setId(pid);
                                    return PhaseTestFixtures.assignTeam(ph, t, null);
                                })
                                .collect(Collectors.toList());
                    }
                }
            }
            return java.util.List.of();
        });

        // Matches: redirect findByMatchdayPhaseId → findByMatchdaySeasonId via the cache
        lenient().when(matchRepository.findByMatchdayPhaseId(any(UUID.class))).thenAnswer(inv -> {
            UUID pid = inv.getArgument(0);
            for (var entry : seasonToPhaseId.entrySet()) {
                if (entry.getValue().equals(pid)) {
                    return matchRepository.findByMatchdaySeasonId(entry.getKey());
                }
            }
            return java.util.List.of();
        });
    }

    @Nested
    class MatchBasedStandingsTest {

        @Test
        void givenOneMatch_whenCalculateStandings_thenWinnerGetThreePoints() {
            // given
            // TNR beats P1R 70:46
            var matchday = new Matchday(regularPhase, "Matchday1", 1);
            var match = createMatchWithScore(matchday, tnr, p1r, 70, 46);

            season.addTeam(tnr);
            season.addTeam(p1r);
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then
            assertEquals(2, standings.size());

            var tnrStanding = findStanding(standings, tnr);
            assertEquals(1, tnrStanding.getWins());
            assertEquals(0, tnrStanding.getDraws());
            assertEquals(0, tnrStanding.getLosses());
            assertEquals(3, tnrStanding.getPoints()); // matchScoring: 3-1-0
            assertEquals(70, tnrStanding.getPointsFor());
            assertEquals(46, tnrStanding.getPointsAgainst());

            var p1rStanding = findStanding(standings, p1r);
            assertEquals(0, p1rStanding.getWins());
            assertEquals(1, p1rStanding.getLosses());
            assertEquals(0, p1rStanding.getPoints());
        }

        @Test
        void givenEqualScores_whenCalculateStandings_thenBothTeamsGetDrawPoint() {
            // given
            var matchday = new Matchday(regularPhase, "Matchday1", 1);
            var match = createMatchWithScore(matchday, clr, tnr, 54, 54);

            season.addTeam(clr);
            season.addTeam(tnr);
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then
            assertEquals(2, standings.size());
            standings.forEach(s -> {
                assertEquals(1, s.getDraws());
                assertEquals(1, s.getPoints()); // matchScoring: 3-1-0, draw = 1
            });
        }

        @Test
        void givenCustomMatchScoring_whenCalculateStandings_thenCustomPointsApplied() {
            // given — Phase 61 MIGR-06: scoring lives on the SeasonPhase. Override the
            // class-level matchScoring field so the seasonPhaseService mocks build phases
            // with the custom 2-1-0 scoring rule.
            matchScoring = new MatchScoring("Classic 2-1-0", 2, 1, 0);

            var matchday = new Matchday(regularPhase, "Matchday1", 1);
            var match = createMatchWithScore(matchday, tnr, p1r, 70, 46);

            season.addTeam(tnr);
            season.addTeam(p1r);
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then
            var tnrStanding = findStanding(standings, tnr);
            assertEquals(2, tnrStanding.getPoints()); // 2 for win
        }

        @Test
        void givenByeMatch_whenCalculateStandings_thenTeamGetsWin() {
            // given
            var matchday = new Matchday(regularPhase, "Matchday1", 1);
            var byeMatch = new Match(matchday, tnr, null);
            byeMatch.setId(UUID.randomUUID());
            byeMatch.setBye(true);

            season.addTeam(tnr);
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(byeMatch));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then
            assertEquals(1, standings.size());
            var tnrStanding = findStanding(standings, tnr);
            assertEquals(1, tnrStanding.getWins());
            assertEquals(3, tnrStanding.getPoints());
        }

        @Test
        void givenMultipleMatches_whenCalculateStandings_thenSortedByPointsThenPointDifference() {
            // given
            var md1 = new Matchday(regularPhase, "Matchday1", 1);
            var md2 = new Matchday(regularPhase, "Matchday2", 2);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);
            var match2 = createMatchWithScore(md2, clr, p1r, 80, 40);

            season.addTeam(tnr);
            season.addTeam(p1r);
            season.addTeam(clr);
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match1, match2));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then
            // CLR first (+40), TNR second (+24), P1R last
            assertEquals(clr.getId(), standings.get(0).getTeam().getId());
            assertEquals(tnr.getId(), standings.get(1).getTeam().getId());
            assertEquals(p1r.getId(), standings.get(2).getTeam().getId());
        }

        @Test
        void givenTeamWithNoGames_whenCalculateStandings_thenTeamExcluded() {
            // given
            var matchday = new Matchday(regularPhase, "Matchday1", 1);
            var match = createMatchWithScore(matchday, tnr, p1r, 70, 46);

            season.addTeam(tnr);
            season.addTeam(p1r);
            season.addTeam(clr);
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then
            assertEquals(2, standings.size());
            assertTrue(standings.stream().noneMatch(s -> s.getTeam().getId().equals(clr.getId())));
        }

        @Test
        void givenMatchWithNoScores_whenCalculateStandings_thenMatchSkipped() {
            // given
            var matchday = new Matchday(regularPhase, "Matchday1", 1);
            var match = new Match(matchday, tnr, p1r);
            match.setId(UUID.randomUUID());
            // No scores set

            season.addTeam(tnr);
            season.addTeam(p1r);
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then
            assertTrue(standings.isEmpty());
        }
    }

    @Nested
    class TeamSuccessionTest {

        @Test
        void givenReplacedTeam_whenCalculateStandings_thenSuccessorInheritsResults() {
            // given
            // Team A (TNR) wins match 1, then gets replaced by Team C (CLR)
            // CLR should inherit TNR's win
            var md1 = new Matchday(regularPhase, "Matchday1", 1);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);

            season.addTeam(tnr);
            season.addTeam(p1r);
            season.addTeam(clr);

            // TNR replaced by CLR
            var stTnr = season.findSeasonTeam(tnr).orElseThrow();
            var stClr = season.findSeasonTeam(clr).orElseThrow();
            stTnr.setSuccessor(stClr);

            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match1));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then — CLR inherits TNR's win
            var clrStanding = findStanding(standings, clr);
            assertEquals(1, clrStanding.getWins());
            assertEquals(3, clrStanding.getPoints());
            assertEquals(70, clrStanding.getPointsFor());
            assertEquals(46, clrStanding.getPointsAgainst());
        }

        @Test
        void givenReplacedTeam_whenCalculateStandings_thenPredecessorNotInStandings() {
            // given
            var md1 = new Matchday(regularPhase, "Matchday1", 1);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);

            season.addTeam(tnr);
            season.addTeam(p1r);
            season.addTeam(clr);

            var stTnr = season.findSeasonTeam(tnr).orElseThrow();
            var stClr = season.findSeasonTeam(clr).orElseThrow();
            stTnr.setSuccessor(stClr);

            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match1));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then — TNR should not appear
            assertTrue(standings.stream().noneMatch(s -> s.getTeam().getId().equals(tnr.getId())));
        }

        @Test
        void givenReplacedTeamAndNewMatches_whenCalculateStandings_thenBothResultsMerged() {
            // given
            // TNR wins match 1, gets replaced by CLR, CLR wins match 2
            var md1 = new Matchday(regularPhase, "Matchday1", 1);
            var md2 = new Matchday(regularPhase, "Matchday2", 2);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);
            var match2 = createMatchWithScore(md2, clr, p1r, 60, 50);

            season.addTeam(tnr);
            season.addTeam(p1r);
            season.addTeam(clr);

            var stTnr = season.findSeasonTeam(tnr).orElseThrow();
            var stClr = season.findSeasonTeam(clr).orElseThrow();
            stTnr.setSuccessor(stClr);

            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match1, match2));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then — CLR has 2 wins (inherited + own)
            var clrStanding = findStanding(standings, clr);
            assertEquals(2, clrStanding.getWins());
            assertEquals(6, clrStanding.getPoints());
            assertEquals(130, clrStanding.getPointsFor());
            assertEquals(96, clrStanding.getPointsAgainst());
        }

        @Test
        void givenSuccessionChain_whenCalculateStandings_thenFinalSuccessorInheritsAll() {
            // given
            // TNR wins, replaced by P1R, P1R replaced by CLR
            var newTeam = new Team("New Team", "NEW");
            newTeam.setId(UUID.randomUUID());

            var md1 = new Matchday(regularPhase, "Matchday1", 1);
            var match1 = createMatchWithScore(md1, tnr, newTeam, 70, 46);

            season.addTeam(tnr);
            season.addTeam(p1r);
            season.addTeam(clr);
            season.addTeam(newTeam);

            // TNR → P1R → CLR
            var stTnr = season.findSeasonTeam(tnr).orElseThrow();
            var stP1r = season.findSeasonTeam(p1r).orElseThrow();
            var stClr = season.findSeasonTeam(clr).orElseThrow();
            stTnr.setSuccessor(stP1r);
            stP1r.setSuccessor(stClr);

            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match1));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then — CLR inherits TNR's win through chain
            var clrStanding = findStanding(standings, clr);
            assertEquals(1, clrStanding.getWins());
            assertEquals(3, clrStanding.getPoints());

            // TNR and P1R should not appear
            assertTrue(standings.stream().noneMatch(s -> s.getTeam().getId().equals(tnr.getId())));
            assertTrue(standings.stream().noneMatch(s -> s.getTeam().getId().equals(p1r.getId())));
        }

        @Test
        void givenReplacedTeamWithBye_whenCalculateStandings_thenSuccessorInheritsByeWin() {
            // given
            var matchday = new Matchday(regularPhase, "Matchday1", 1);
            var byeMatch = new Match(matchday, tnr, null);
            byeMatch.setId(UUID.randomUUID());
            byeMatch.setBye(true);

            season.addTeam(tnr);
            season.addTeam(clr);

            // TNR replaced by CLR
            var stTnr = season.findSeasonTeam(tnr).orElseThrow();
            var stClr = season.findSeasonTeam(clr).orElseThrow();
            stTnr.setSuccessor(stClr);

            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(byeMatch));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then — CLR inherits TNR's bye win
            assertEquals(1, standings.size());
            var clrStanding = findStanding(standings, clr);
            assertEquals(1, clrStanding.getWins());
            assertEquals(3, clrStanding.getPoints());
        }
    }

    @Nested
    class CalculateStandingsWithBuchholzTest {

        @Test
        void givenSwissSeason_whenCalculateStandingsWithBuchholz_thenStandingsSortedByPointsThenBuchholzThenPointDiffThenPointsFor() {
            // given
            season.addTeam(tnr);
            season.addTeam(p1r);
            season.addTeam(clr);

            // Matchday 1: TNR beats P1R (70:46), CLR has bye
            var md1 = new Matchday(regularPhase, "Round 1", 1);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);

            // Matchday 2: TNR beats CLR (60:50), P1R has bye
            var md2 = new Matchday(regularPhase, "Round 2", 2);
            var match2 = createMatchWithScore(md2, tnr, clr, 60, 50);

            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match1, match2));

            // Races for Buchholz opponent tracking
            var race1 = new Race();
            race1.setId(UUID.randomUUID());
            race1.setMatch(match1);
            race1.setMatchday(md1);
            var race2 = new Race();
            race2.setId(UUID.randomUUID());
            race2.setMatch(match2);
            race2.setMatchday(md2);
            when(raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull(season.getId()))
                    .thenReturn(List.of(race1, race2));

            // when
            var standings = standingsService.calculateStandingsWithBuchholz(regularPhase.getId(), null);

            // then
            assertFalse(standings.isEmpty());
            // TNR: 6pts, played P1R(0pts) + CLR(0pts), Buchholz=0
            // P1R: 0pts, played TNR(6pts), Buchholz=6
            // CLR: 0pts, played TNR(6pts), Buchholz=6
            assertEquals(tnr.getId(), standings.get(0).getTeam().getId());
            // P1R and CLR both have 0 points and Buchholz 6, sorted by pointDiff then pointsFor
            // P1R: pointsFor=46, pointsAgainst=70, diff=-24
            // CLR: pointsFor=50, pointsAgainst=60, diff=-10
            // CLR should be ahead of P1R (better point difference)
            assertEquals(clr.getId(), standings.get(1).getTeam().getId());
            assertEquals(p1r.getId(), standings.get(2).getTeam().getId());
        }

        @Test
        void givenNonSwissSeason_whenCalculateStandingsWithBuchholz_thenBuchholzIsZeroAndStandingsSortedNormally() {
            // given
            season.addTeam(tnr);
            season.addTeam(p1r);

            var md1 = new Matchday(regularPhase, "Matchday1", 1);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);

            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match1));
            when(raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull(season.getId()))
                    .thenReturn(List.of());

            // when
            var standings = standingsService.calculateStandingsWithBuchholz(regularPhase.getId(), null);

            // then
            assertEquals(2, standings.size());
            assertEquals(tnr.getId(), standings.get(0).getTeam().getId());
            assertEquals(0, standings.get(0).getBuchholz());
            assertEquals(0, standings.get(1).getBuchholz());
        }

        @Test
        void givenNoMatches_whenCalculateStandingsWithBuchholz_thenEmptyList() {
            // given
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of());

            // when
            var standings = standingsService.calculateStandingsWithBuchholz(regularPhase.getId(), null);

            // then
            assertTrue(standings.isEmpty());
        }
    }


    private Match createMatchWithScore(Matchday matchday, Team home, Team away, int homeScore, int awayScore) {
        var match = new Match(matchday, home, away);
        match.setId(UUID.randomUUID());
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        return match;
    }

    private StandingsService.TeamStanding findStanding(List<StandingsService.TeamStanding> standings, Team team) {
        return standings.stream()
                .filter(s -> s.getTeam().getId().equals(team.getId()))
                .findFirst().orElseThrow();
    }

    // -------------------------------------------------------------------------
    // Phase 58: phase/group/Buchholz/bridge tests (SVC-02, D-01, D-04, D-05, D-06)
    // -------------------------------------------------------------------------

    @Nested
    class PhaseAwareStandingsTest {

        @Test
        void givenLeaguePhase_whenCalculateStandingsByPhaseId_thenReturnsAllPhaseTeams() {
            // given
            var rs = new RaceScoring("RS", "20,15,10", "3,2,1", 2);
            var ms = new MatchScoring("MS", 3, 1, 0);
            var regular = PhaseTestFixtures.regularPhase(season, rs, ms);

            var pt1 = PhaseTestFixtures.assignTeam(regular, tnr, null);
            var pt2 = PhaseTestFixtures.assignTeam(regular, p1r, null);

            var matchday = new Matchday(regularPhase, "Phase58-Test-MD1", 1);
            matchday.setPhase(regular);
            var match = createMatchWithScore(matchday, tnr, p1r, 70, 46);

            when(seasonPhaseService.findById(regular.getId())).thenReturn(regular);
            when(matchRepository.findByMatchdayPhaseId(regular.getId())).thenReturn(List.of(match));
            when(phaseTeamRepository.findByPhaseId(regular.getId())).thenReturn(List.of(pt1, pt2));

            // when
            var result = standingsService.calculateStandings(regular.getId(), null);

            // then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(s -> s.getGroup() == null); // LEAGUE => group is null (D-05)
        }

        @Test
        void givenGroupsLayout_whenCalculateStandingsWithoutGroupId_thenFlatListWithGroupBadge() {
            // given — GROUPS layout, 2 groups, 2 teams per group (D-04, D-05)
            var rs = new RaceScoring("RS", "20,15,10", "3,2,1", 2);
            var ms = new MatchScoring("MS", 3, 1, 0);
            var groupsPhase = PhaseTestFixtures.groupsRegularPhase(season, rs, ms, "Phase58-Test-Group-A", "Phase58-Test-Group-B");
            var groupA = groupsPhase.getGroups().get(0);
            var groupB = groupsPhase.getGroups().get(1);

            var teamA1 = new Team("Phase58-Test-Alpha1", "A1");
            teamA1.setId(UUID.randomUUID());
            var teamA2 = new Team("Phase58-Test-Alpha2", "A2");
            teamA2.setId(UUID.randomUUID());
            var teamB1 = new Team("Phase58-Test-Beta1", "B1");
            teamB1.setId(UUID.randomUUID());
            var teamB2 = new Team("Phase58-Test-Beta2", "B2");
            teamB2.setId(UUID.randomUUID());

            var ptA1 = PhaseTestFixtures.assignTeam(groupsPhase, teamA1, groupA);
            var ptA2 = PhaseTestFixtures.assignTeam(groupsPhase, teamA2, groupA);
            var ptB1 = PhaseTestFixtures.assignTeam(groupsPhase, teamB1, groupB);
            var ptB2 = PhaseTestFixtures.assignTeam(groupsPhase, teamB2, groupB);

            var mdA = new Matchday(regularPhase, "Phase58-Test-MD-A", 1);
            mdA.setPhase(groupsPhase);
            mdA.setGroup(groupA);
            var matchA = createMatchWithScore(mdA, teamA1, teamA2, 70, 46);

            var mdB = new Matchday(regularPhase, "Phase58-Test-MD-B", 2);
            mdB.setPhase(groupsPhase);
            mdB.setGroup(groupB);
            var matchB = createMatchWithScore(mdB, teamB1, teamB2, 60, 50);

            when(seasonPhaseService.findById(groupsPhase.getId())).thenReturn(groupsPhase);
            when(matchRepository.findByMatchdayPhaseId(groupsPhase.getId())).thenReturn(List.of(matchA, matchB));
            when(phaseTeamRepository.findByPhaseId(groupsPhase.getId())).thenReturn(List.of(ptA1, ptA2, ptB1, ptB2));

            // when — combined view: groupId=null (D-04)
            var result = standingsService.calculateStandings(groupsPhase.getId(), null);

            // then — flat list with all 4 teams, each has group set (D-05)
            assertThat(result).hasSize(4);
            assertThat(result).allMatch(s -> s.getGroup() != null);
        }

        @Test
        void givenGroupsPhase_whenCalculateStandingsByGroup_thenOnlyGroupTeams() {
            // given — GROUPS layout, only request group A (D-04 per-group view)
            var rs = new RaceScoring("RS", "20,15,10", "3,2,1", 2);
            var ms = new MatchScoring("MS", 3, 1, 0);
            var groupsPhase = PhaseTestFixtures.groupsRegularPhase(season, rs, ms, "Phase58-Test-Group-A", "Phase58-Test-Group-B");
            var groupA = groupsPhase.getGroups().get(0);

            var teamA1 = new Team("Phase58-Test-GA1", "GA1");
            teamA1.setId(UUID.randomUUID());
            var teamA2 = new Team("Phase58-Test-GA2", "GA2");
            teamA2.setId(UUID.randomUUID());

            var ptA1 = PhaseTestFixtures.assignTeam(groupsPhase, teamA1, groupA);
            var ptA2 = PhaseTestFixtures.assignTeam(groupsPhase, teamA2, groupA);

            var mdA = new Matchday(regularPhase, "Phase58-Test-MD-GA", 1);
            mdA.setPhase(groupsPhase);
            mdA.setGroup(groupA);
            var matchA = createMatchWithScore(mdA, teamA1, teamA2, 70, 46);

            when(seasonPhaseService.findById(groupsPhase.getId())).thenReturn(groupsPhase);
            when(matchRepository.findByMatchdayPhaseId(groupsPhase.getId())).thenReturn(List.of(matchA));
            when(phaseTeamRepository.findByPhaseIdAndGroupId(groupsPhase.getId(), groupA.getId())).thenReturn(List.of(ptA1, ptA2));

            // when — per-group view (D-04)
            var result = standingsService.calculateStandings(groupsPhase.getId(), groupA.getId());

            // then — only 2 teams from group A
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(s -> s.getGroup() != null && s.getGroup().getId().equals(groupA.getId()));
        }

        @Test
        void givenSeasonId_whenCalculateStandings_thenDelegatesToRegularPhase() {
            // given
            var rs = new RaceScoring("RS", "20,15,10", "3,2,1", 2);
            var ms = new MatchScoring("MS", 3, 1, 0);
            SeasonPhase regular = PhaseTestFixtures.regularPhase(season, rs, ms);

            // The @Deprecated bridge uses findByType (Optional) to avoid tx rollback-only (D-01).
            when(seasonPhaseService.findByType(season.getId(), PhaseType.REGULAR)).thenReturn(Optional.of(regular));
            when(seasonPhaseService.findById(regular.getId())).thenReturn(regular);
            when(matchRepository.findByMatchdayPhaseId(regular.getId())).thenReturn(List.of());
            when(phaseTeamRepository.findByPhaseId(regular.getId())).thenReturn(List.of());

            // when — @Deprecated bridge (D-01)
            var result = standingsService.calculateStandings(season.getId()); // seasonId-overload

            // then — bridge resolves REGULAR phase via findByType, then delegates to canonical (D-02)
            verify(seasonPhaseService).findByType(season.getId(), PhaseType.REGULAR);
            verify(matchRepository).findByMatchdayPhaseId(regular.getId());
            assertThat(result).isEmpty();
        }

        @Test
        void givenSwissGroups_whenCalculateStandingsCombined_thenBuchholzNotUsedAsTiebreaker() {
            // given — GROUPS layout, combined-view; Buchholz must NOT affect sort order (D-06)
            var rs = new RaceScoring("RS", "20,15,10", "3,2,1", 2);
            var ms = new MatchScoring("MS", 3, 1, 0);
            var groupsPhase = PhaseTestFixtures.groupsRegularPhase(season, rs, ms, "Phase58-Test-Group-A", "Phase58-Test-Group-B");
            var groupA = groupsPhase.getGroups().get(0);
            var groupB = groupsPhase.getGroups().get(1);

            var teamA1 = new Team("Phase58-Test-SA1", "SA1");
            teamA1.setId(UUID.randomUUID());
            var teamA2 = new Team("Phase58-Test-SA2", "SA2");
            teamA2.setId(UUID.randomUUID());
            var teamB1 = new Team("Phase58-Test-SB1", "SB1");
            teamB1.setId(UUID.randomUUID());
            var teamB2 = new Team("Phase58-Test-SB2", "SB2");
            teamB2.setId(UUID.randomUUID());

            var ptA1 = PhaseTestFixtures.assignTeam(groupsPhase, teamA1, groupA);
            var ptA2 = PhaseTestFixtures.assignTeam(groupsPhase, teamA2, groupA);
            var ptB1 = PhaseTestFixtures.assignTeam(groupsPhase, teamB1, groupB);
            var ptB2 = PhaseTestFixtures.assignTeam(groupsPhase, teamB2, groupB);

            // teamA1 beats teamA2 by big margin (+40), teamB1 beats teamB2 by small margin (+10)
            var mdA = new Matchday(regularPhase, "Phase58-Test-MD-SA", 1);
            mdA.setPhase(groupsPhase);
            mdA.setGroup(groupA);
            var matchA = createMatchWithScore(mdA, teamA1, teamA2, 80, 40);

            var mdB = new Matchday(regularPhase, "Phase58-Test-MD-SB", 2);
            mdB.setPhase(groupsPhase);
            mdB.setGroup(groupB);
            var matchB = createMatchWithScore(mdB, teamB1, teamB2, 60, 50);

            when(seasonPhaseService.findById(groupsPhase.getId())).thenReturn(groupsPhase);
            when(matchRepository.findByMatchdayPhaseId(groupsPhase.getId())).thenReturn(List.of(matchA, matchB));
            when(phaseTeamRepository.findByPhaseId(groupsPhase.getId())).thenReturn(List.of(ptA1, ptA2, ptB1, ptB2));

            // when — combined view with groupId=null; Buchholz ignored in sorting (D-06)
            var result = standingsService.calculateStandingsWithBuchholz(groupsPhase.getId(), null);

            // then — sorted by points -> pointDifference -> pointsFor only (NOT buchholz)
            // teamA1 has 3pts, +40 diff; teamB1 has 3pts, +10 diff; losers have 0pts
            assertThat(result).hasSize(4);
            assertThat(result.get(0).getTeam().getId()).isEqualTo(teamA1.getId()); // 3pts, +40
            assertThat(result.get(1).getTeam().getId()).isEqualTo(teamB1.getId()); // 3pts, +10
        }

        @Test
        void givenSwissGroupsAndGroupId_whenCalculateStandingsWithBuchholz_thenBuchholzUsedAsTiebreaker() {
            // given — GROUPS phase, per-group Buchholz allowed (D-06)
            var rs = new RaceScoring("RS", "20,15,10", "3,2,1", 2);
            var ms = new MatchScoring("MS", 3, 1, 0);
            var groupsPhase = PhaseTestFixtures.groupsRegularPhase(season, rs, ms, "Phase58-Test-Group-X");
            var groupX = groupsPhase.getGroups().get(0);

            var teamX1 = new Team("Phase58-Test-GX1", "GX1");
            teamX1.setId(UUID.randomUUID());
            var teamX2 = new Team("Phase58-Test-GX2", "GX2");
            teamX2.setId(UUID.randomUUID());

            var ptX1 = PhaseTestFixtures.assignTeam(groupsPhase, teamX1, groupX);
            var ptX2 = PhaseTestFixtures.assignTeam(groupsPhase, teamX2, groupX);

            var md = new Matchday(regularPhase, "Phase58-Test-MD-GX", 1);
            md.setPhase(groupsPhase);
            md.setGroup(groupX);
            var match = createMatchWithScore(md, teamX1, teamX2, 70, 46);

            when(seasonPhaseService.findById(groupsPhase.getId())).thenReturn(groupsPhase);
            when(matchRepository.findByMatchdayPhaseId(groupsPhase.getId())).thenReturn(List.of(match));
            when(phaseTeamRepository.findByPhaseIdAndGroupId(groupsPhase.getId(), groupX.getId())).thenReturn(List.of(ptX1, ptX2));
            when(raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull(season.getId())).thenReturn(List.of());

            // when — per-group Buchholz (D-06)
            var result = standingsService.calculateStandingsWithBuchholz(groupsPhase.getId(), groupX.getId());

            // then — list returned with Buchholz populated
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getTeam().getId()).isEqualTo(teamX1.getId()); // winner first
        }

        @Test
        void givenSwissGroupsAndNullGroupId_whenCalculateStandingsWithBuchholz_thenStillFlatListWithBuchholzPopulatedButFallbackTiebreaker() {
            // given — GROUPS phase, combined-view; buchholz field populated for display but NOT used as tiebreaker (D-06)
            var rs = new RaceScoring("RS", "20,15,10", "3,2,1", 2);
            var ms = new MatchScoring("MS", 3, 1, 0);
            var groupsPhase = PhaseTestFixtures.groupsRegularPhase(season, rs, ms, "Phase58-Test-Group-Y", "Phase58-Test-Group-Z");
            var groupY = groupsPhase.getGroups().get(0);
            var groupZ = groupsPhase.getGroups().get(1);

            var teamY1 = new Team("Phase58-Test-GY1", "GY1");
            teamY1.setId(UUID.randomUUID());
            var teamZ1 = new Team("Phase58-Test-GZ1", "GZ1");
            teamZ1.setId(UUID.randomUUID());

            var ptY1 = PhaseTestFixtures.assignTeam(groupsPhase, teamY1, groupY);
            var ptZ1 = PhaseTestFixtures.assignTeam(groupsPhase, teamZ1, groupZ);

            var mdY = new Matchday(regularPhase, "Phase58-Test-MD-GY", 1);
            mdY.setPhase(groupsPhase);
            mdY.setGroup(groupY);

            var mdZ = new Matchday(regularPhase, "Phase58-Test-MD-GZ", 2);
            mdZ.setPhase(groupsPhase);
            mdZ.setGroup(groupZ);

            // teamY1 wins with bye (3pts), teamZ1 wins with bye (3pts) — equal points
            var byeY = new Match(mdY, teamY1, null);
            byeY.setId(UUID.randomUUID());
            byeY.setBye(true);
            var byeZ = new Match(mdZ, teamZ1, null);
            byeZ.setId(UUID.randomUUID());
            byeZ.setBye(true);

            when(seasonPhaseService.findById(groupsPhase.getId())).thenReturn(groupsPhase);
            when(matchRepository.findByMatchdayPhaseId(groupsPhase.getId())).thenReturn(List.of(byeY, byeZ));
            when(phaseTeamRepository.findByPhaseId(groupsPhase.getId())).thenReturn(List.of(ptY1, ptZ1));

            // when — combined-view Buchholz (D-06 fallback: standard tiebreaker)
            var result = standingsService.calculateStandingsWithBuchholz(groupsPhase.getId(), null);

            // then — both teams returned; Buchholz field may be populated but sort is standard chain
            assertThat(result).hasSize(2);
            // Both have 3pts, 0 pointDiff, 0 pointsFor (bye wins don't record scores) — stable order
            assertThat(result.get(0).getPoints()).isEqualTo(3);
            assertThat(result.get(1).getPoints()).isEqualTo(3);
        }
    }
}
