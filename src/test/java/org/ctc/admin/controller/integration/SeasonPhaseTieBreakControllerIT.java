package org.ctc.admin.controller.integration;

import org.ctc.TestHelper;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@Tag("integration")
class SeasonPhaseTieBreakControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private SeasonPhaseRepository phases;
    @Autowired private MatchdayRepository matchdays;
    @Autowired private MatchRepository matches;
    @Autowired private TeamRepository teams;
    @Autowired private TestHelper testHelper;

    @Test
    void givenPhase_whenGetEditForm_thenLabeledTieBreakCheckboxRendered() throws Exception {
        // given
        var phase = regularPhase("T-TieBreak-Form");
        // when / then
        mockMvc.perform(get("/admin/seasons/{sid}/phases/{pid}/edit", phase.getSeason().getId(), phase.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<label for=\"tieBreakRaceWinner1\">Tie-break by race winner</label>")))
                .andExpect(content().string(containsString("A tied match goes to the team whose driver wins the first leg with results.")));
    }

    @Test
    void givenOnlyByes_whenEnableTieBreak_thenPersisted() throws Exception {
        // given
        var phase = regularPhase("T-TieBreak-Byes");
        var bye = new Match(matchday(phase), team("TBB"), null);
        bye.setBye(true);
        matches.saveAndFlush(bye);
        // when
        save(phase, true).andExpect(status().is3xxRedirection()).andExpect(flash().attribute("successMessage", "Phase updated"));
        // then
        assertThat(phases.findById(phase.getId()).orElseThrow().isTieBreakRaceWinner()).isTrue();
    }

    @Test
    void givenScoredMatch_whenEnableTieBreak_thenRejectedWithMessage() throws Exception {
        // given
        var phase = regularPhase("T-TieBreak-Locked");
        var match = new Match(matchday(phase), team("TBH"), team("TBA"));
        match.setHomeScore(40);
        match.setAwayScore(30);
        matches.saveAndFlush(match);
        // when
        save(phase, true).andExpect(flash().attribute("errorMessage",
                "This phase has scored pairings. Keep its tie-break setting and use a new phase for a different rule."));
        // then
        assertThat(phases.findById(phase.getId()).orElseThrow().isTieBreakRaceWinner()).isFalse();
    }

    private org.springframework.test.web.servlet.ResultActions save(SeasonPhase phase, boolean tieBreak) throws Exception {
        return mockMvc.perform(post("/admin/seasons/{sid}/phases/save", phase.getSeason().getId())
                .param("id", phase.getId().toString())
                .param("phaseType", phase.getPhaseType().name())
                .param("layout", phase.getLayout().name())
                .param("format", phase.getFormat().name())
                .param("legs", String.valueOf(phase.getLegs()))
                .param("sortIndex", String.valueOf(phase.getSortIndex()))
                .param("tieBreakRaceWinner", String.valueOf(tieBreak)));
    }

    private SeasonPhase regularPhase(String seasonName) {
        var season = testHelper.createSeason(seasonName);
        return phases.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR).orElseThrow();
    }

    private Matchday matchday(SeasonPhase phase) {
        return matchdays.saveAndFlush(new Matchday(phase, "T-TieBreak MD", 1));
    }

    private Team team(String shortName) {
        return teams.saveAndFlush(new Team("T-" + shortName + " " + java.util.UUID.randomUUID().toString().substring(0, 6), shortName));
    }
}
