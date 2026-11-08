package org.ctc.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SwissRoundForm {
    @NotNull private UUID phaseId;
    private UUID groupId;
    private UUID matchdayId;
    @NotBlank @Size(max=255) private String label;
    @Min(0) private int roundNumber;
    @NotBlank private String scope;
    @NotBlank private String generation;
    @NotNull @Size(max=256) private List<@Valid Pairing> rows = new ArrayList<>();

    @Getter @Setter
    public static class Pairing {
        private UUID matchId;
        private UUID homeTeamId;
        private UUID awayTeamId;
        private boolean bye;
        private boolean remove;
        public boolean blank() { return matchId == null && homeTeamId == null && awayTeamId == null && !bye; }
    }
}
