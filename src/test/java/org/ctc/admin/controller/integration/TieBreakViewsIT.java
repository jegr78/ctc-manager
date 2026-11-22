package org.ctc.admin.controller.integration;

import java.util.ArrayList;
import java.util.List;
import org.ctc.TestHelper;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.service.ScoringService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@Tag("integration")
class TieBreakViewsIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestHelper testHelper;
    @Autowired private SeasonPhaseRepository phases;
    @Autowired private RaceRepository races;
    @Autowired private RaceLineupRepository lineups;
    @Autowired private ScoringService scoring;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    @Test
    void givenTieBreakMatch_whenViewMatchday_thenWinnerAndTieBreakMarkerShown() throws Exception {
        // given
        var race = tieBreakRace("T-TieBreak-Matchday");
        // when / then
        mockMvc.perform(get("/admin/matchdays/{id}", race.getMatchday().getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("score-win")))
                .andExpect(content().string(not(containsString("score-draw"))))
                .andExpect(content().string(containsString("tie-break")));
    }

    @Test
    void givenSingleLegTieBreakMatch_whenViewRace_thenTieBreakBadgeInsteadOfDraw() throws Exception {
        // given
        var race = tieBreakRace("T-TieBreak-Race");
        // when / then
        mockMvc.perform(get("/admin/races/{id}", race.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TIE-BREAK")))
                .andExpect(content().string(not(containsString(">DRAW<"))));
    }

    private Race tieBreakRace(String seasonName) {
        var season = testHelper.createSeason(seasonName);
        var phase = phases.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR).orElseThrow();
        phase.setTieBreakRaceWinner(true);
        phases.save(phase);
        var matchday = testHelper.createMatchdayInRegularPhase(season, "T-TieBreak MD", 1);
        var home = testHelper.createTeam("T-TieBreak Home", "TBH");
        var away = testHelper.createTeam("T-TieBreak Away", "TBA");
        var match = testHelper.createMatch(matchday, home, away);
        var race = testHelper.createRace(matchday, match);
        var awayDriver = testHelper.createDriver("t_tb_away", "TB Away");
        var homeDriver = testHelper.createDriver("t_tb_home", "TB Home");
        lineups.save(new RaceLineup(race, awayDriver, away));
        lineups.save(new RaceLineup(race, homeDriver, home));
        var results = new ArrayList<RaceResult>();
        var winner = new RaceResult(race, awayDriver, 1, 2, false);
        winner.setPointsTotal(20);
        var second = new RaceResult(race, homeDriver, 2, 1, false);
        second.setPointsTotal(20);
        results.addAll(List.of(winner, second));
        race.getResults().addAll(results);
        race = races.saveAndFlush(race);
        scoring.aggregateMatchScores(race);
        entityManager.flush();
        entityManager.clear();
        return race;
    }
}
