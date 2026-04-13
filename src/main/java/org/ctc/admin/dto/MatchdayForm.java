package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class MatchdayForm {

    private UUID id;

    @NotBlank
    private String label;

    private int sortIndex;

    private UUID seasonId;
}
