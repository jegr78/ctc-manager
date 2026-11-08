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
    private MultiValueMap<String,String> params(SwissRoundForm form) {
        var values=new LinkedMultiValueMap<String,String>(); values.add("phaseId",phaseId.toString()); values.add("label",form.getLabel());
        values.add("roundNumber",Integer.toString(form.getRoundNumber())); values.add("scope",form.getScope()); values.add("generation",form.getGeneration()); return values;
    }

}
