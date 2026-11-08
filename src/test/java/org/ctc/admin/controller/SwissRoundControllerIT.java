package org.ctc.admin.controller;

import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.SwissRoundFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.ctc.domain.service.SwissRoundService;
import org.ctc.admin.dto.SwissRoundForm;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@CtcDevSpringBootContext @AutoConfigureMockMvc @Tag("integration") @WithMockUser
class SwissRoundControllerIT extends SwissRoundFixture {
    @Autowired MockMvc mvc;
    @Autowired SwissRoundService rounds;
    @Test void givenSwissPhase_whenOpeningRoundWorkspace_thenOfferManualRoundCreationBeforeAnyResult() throws Exception {
        mvc.perform(get("/admin/swiss-rounds").param("phaseId",phaseId.toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Create round manually")));
    }
    @Test void givenNewRound_whenOpeningEditor_thenRenderLabeledFieldsAndCurrentContext() throws Exception {
        mvc.perform(get("/admin/swiss-rounds/edit").param("phaseId",phaseId.toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Home team")))
                .andExpect(content().string(containsString("Missing participants")))
                .andExpect(content().string(containsString("name=\"scope\"")));
    }
    @Test void givenDuplicateTeam_whenSubmitting_thenReturnConflictWithFieldReasonAndRetainedValuesWithoutWriting() throws Exception {
        var form=rounds.editor(phaseId,null,null,null).form(); var params=params(form);
        params.add("rows[0].homeTeamId",teamIds.get(0).toString()); params.add("rows[0].awayTeamId",teamIds.get(1).toString());
        params.add("rows[1].homeTeamId",teamIds.get(0).toString()); params.add("rows[1].awayTeamId",teamIds.get(2).toString());
        mvc.perform(post("/admin/swiss-rounds/save").with(csrf()).params(params))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("only once")))
                .andExpect(content().string(containsString(teamIds.get(0).toString())));
        assertThat(days.findByPhaseIdOrderBySortIndexAsc(phaseId)).isEmpty();
    }
    @Test void givenValidRound_whenPostingThenEditing_thenSaveAndPrefillOriginalPairings() throws Exception {
        var form=rounds.editor(phaseId,null,null,null).form(); var params=params(form);
        params.add("rows[0].homeTeamId",teamIds.get(0).toString()); params.add("rows[0].awayTeamId",teamIds.get(1).toString());
        params.add("rows[1].homeTeamId",teamIds.get(2).toString()); params.add("rows[1].awayTeamId",teamIds.get(3).toString());
        mvc.perform(post("/admin/swiss-rounds/save").with(csrf()).params(params)).andExpect(status().is3xxRedirection());
        var day=days.findByPhaseIdOrderBySortIndexAsc(phaseId).getFirst();
        mvc.perform(get("/admin/swiss-rounds/edit").param("phaseId",phaseId.toString()).param("matchdayId",day.getId().toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("selected=\"selected\"")));
        assertThat(rounds.editor(phaseId,null,day.getId(),null).form().getRows()).filteredOn(row -> row.getMatchId()!=null).hasSize(2);
    }
    @Test void givenMissingPhase_whenPosting_thenReturnReadableBadRequestWithoutCreatingRound() throws Exception {
        mvc.perform(post("/admin/swiss-rounds/save").with(csrf()).param("label","Test missing phase"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Choose a valid Swiss phase")));
        assertThat(days.findByPhaseIdOrderBySortIndexAsc(phaseId)).isEmpty();
    }
    @Autowired org.ctc.domain.repository.SeasonPhaseGroupRepository groups;
    @Test void givenGroupPhaseChangedToLeagueBeforeScheduling_whenOpeningSwissWorkspace_thenHideRetainedGroupsAndKeepAllTeams() throws Exception {
        var groupId=tx.execute(status -> {
            var phase=phases.findById(phaseId).orElseThrow(); phase.setLayout(org.ctc.domain.model.PhaseLayout.GROUPS);
            var group=groups.save(new org.ctc.domain.model.SeasonPhaseGroup(phase,"Test retained group",0));
            for(var place:roster.findByPhaseId(phaseId)) place.setGroup(group);
            return group.getId();
        });
        mvc.perform(get("/admin/swiss-rounds").param("phaseId",phaseId.toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("id=\"swiss-group\"")));
        mvc.perform(post("/admin/seasons/"+seasonId+"/phases/save").with(csrf()).params(phaseParams(phaseId,org.ctc.domain.model.PhaseLayout.LEAGUE)))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("successMessage","Phase updated"));
        String html=mvc.perform(get("/admin/swiss-rounds").param("phaseId",phaseId.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(org.jsoup.Jsoup.parse(html).select("#swiss-group")).isEmpty();
        assertThat(rounds.workspace(phaseId,null).groups()).isEmpty(); assertThat(rounds.workspace(phaseId,null).teams()).hasSize(4);
        mvc.perform(get("/admin/seasons/"+seasonId+"/phases/"+phaseId)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Maintain Swiss rounds")));
        var form=rounds.editor(phaseId,null,null,null).form();
        for(int n=0;n<2;n++) {form.getRows().get(n).setHomeTeamId(teamIds.get(n*2));form.getRows().get(n).setAwayTeamId(teamIds.get(n*2+1));}
        var day=rounds.save(form); assertThat(matches.findByMatchdayId(day)).hasSize(2);
        tx.executeWithoutResult(status -> assertThat(roster.findByPhaseId(phaseId)).hasSize(4)
                .allSatisfy(place -> assertThat(place.getGroup().getId()).isEqualTo(groupId)));
    }
    @Test void givenPlayoffBracketWithSwissFormat_whenRenderingPhase_thenKeepFormatAndDoNotOfferUnsupportedRoundEditor() throws Exception {
        var bracketId=tx.execute(status -> helper.createPlayoffInPhase(seasons.findById(seasonId).orElseThrow(),"Test Swiss bracket",2).getPhase().getId());
        mvc.perform(post("/admin/seasons/"+seasonId+"/phases/save").with(csrf()).params(phaseParams(bracketId,org.ctc.domain.model.PhaseLayout.BRACKET)))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("successMessage","Phase updated"));
        String html=mvc.perform(get("/admin/seasons/"+seasonId+"/phases/"+bracketId)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(org.jsoup.Jsoup.parse(html).select("a").stream().map(org.jsoup.nodes.Element::text).toList()).doesNotContain("Maintain Swiss rounds");
        tx.executeWithoutResult(status -> {
            var phase=phases.findById(bracketId).orElseThrow(); assertThat(phase.getFormat()).isEqualTo(org.ctc.domain.model.SeasonFormat.SWISS);
            assertThat(phase.getLayout()).isEqualTo(org.ctc.domain.model.PhaseLayout.BRACKET);
        });
    }
    private MultiValueMap<String,String> phaseParams(java.util.UUID id,org.ctc.domain.model.PhaseLayout layout) {
        return tx.execute(status -> {
            var phase=phases.findById(id).orElseThrow(); var values=new LinkedMultiValueMap<String,String>();
            values.add("id",id.toString()); values.add("phaseType",phase.getPhaseType().name()); values.add("layout",layout.name()); values.add("format","SWISS");
            values.add("legs",Integer.toString(phase.getLegs())); values.add("sortIndex",Integer.toString(phase.getSortIndex()));
            if(phase.getLabel()!=null) values.add("label",phase.getLabel());
            if(phase.getRaceScoring()!=null) values.add("raceScoringId",phase.getRaceScoring().getId().toString());
            if(phase.getMatchScoring()!=null) values.add("matchScoringId",phase.getMatchScoring().getId().toString());
            if(phase.getTotalRounds()!=null) values.add("totalRounds",phase.getTotalRounds().toString());
            if(phase.getEventDurationMinutes()!=null) values.add("eventDurationMinutes",phase.getEventDurationMinutes().toString());
            if(phase.getStartDate()!=null) values.add("startDate",phase.getStartDate().toString());
            if(phase.getEndDate()!=null) values.add("endDate",phase.getEndDate().toString());
            return values;
        });
    }

    private MultiValueMap<String,String> params(SwissRoundForm form) {
        var values=new LinkedMultiValueMap<String,String>(); values.add("phaseId",phaseId.toString()); values.add("label",form.getLabel());
        values.add("roundNumber",Integer.toString(form.getRoundNumber())); values.add("scope",form.getScope()); values.add("generation",form.getGeneration()); return values;
    }

}
