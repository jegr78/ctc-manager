package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class RaceScoringForm {

    private UUID id;

    @NotBlank
    private String name;

    @NotBlank
    private String racePoints;

    private String qualiPoints;

    private int fastestLapPoints;
}
