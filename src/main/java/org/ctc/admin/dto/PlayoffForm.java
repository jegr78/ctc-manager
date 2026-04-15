package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class PlayoffForm {

    private UUID id;
    @NotNull
    private UUID seasonId;
    @NotBlank
    private String name;
    private int numberOfTeams = 8;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer eventDurationMinutes;
}
