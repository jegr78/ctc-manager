package org.ctc.admin.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.DataBinder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring indexed-property binding test for PhaseTeamForm assignments list.
 */
class PhaseTeamFormTest {

    @Test
    void givenAutoPopulatingList_whenBindIndexedProperties_thenAssignmentsParsed() {
        // given
        var form = new PhaseTeamForm();
        form.setPhaseId(UUID.randomUUID());
        var binder = new DataBinder(form);
        var teamId0 = UUID.randomUUID();
        var teamId1 = UUID.randomUUID();
        var groupId0 = UUID.randomUUID();

        var pvs = new MutablePropertyValues();
        pvs.add("assignments[0].teamId", teamId0.toString());
        pvs.add("assignments[0].included", "true");
        pvs.add("assignments[0].groupId", groupId0.toString());
        pvs.add("assignments[1].teamId", teamId1.toString());
        pvs.add("assignments[1].included", "false");

        // when
        binder.bind(pvs);

        // then
        assertThat(form.getAssignments()).hasSize(2);
        assertThat(form.getAssignments().get(0).getTeamId()).isEqualTo(teamId0);
        assertThat(form.getAssignments().get(0).isIncluded()).isTrue();
        assertThat(form.getAssignments().get(0).getGroupId()).isEqualTo(groupId0);
        assertThat(form.getAssignments().get(1).getTeamId()).isEqualTo(teamId1);
        assertThat(form.getAssignments().get(1).isIncluded()).isFalse();
    }

    @Test
    void givenEmptyAssignments_whenBindNoIndexedProperties_thenAssignmentsEmpty() {
        // given
        var form = new PhaseTeamForm();
        form.setPhaseId(UUID.randomUUID());
        var binder = new DataBinder(form);
        var pvs = new MutablePropertyValues();

        // when
        binder.bind(pvs);

        // then
        assertThat(form.getAssignments()).isEmpty();
    }

    @Test
    void givenPhaseIdSet_whenFormCreated_thenAssignmentsListInitialized() {
        // given
        var form = new PhaseTeamForm();
        var phaseId = UUID.randomUUID();
        form.setPhaseId(phaseId);

        // when / then
        assertThat(form.getPhaseId()).isEqualTo(phaseId);
        assertThat(form.getAssignments()).isNotNull();
        assertThat(form.getAssignments()).isEmpty();
    }
}
