package org.ctc.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.AutoPopulatingList;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PhaseTeamForm {

    private UUID phaseId;

    private List<Assignment> assignments = new AutoPopulatingList<>(Assignment.class);

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Assignment {
        private UUID teamId;
        private boolean included;
        private UUID groupId;   // nullable
    }
}
