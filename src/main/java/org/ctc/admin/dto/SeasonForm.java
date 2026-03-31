package org.ctc.admin.dto;

import org.ctc.domain.model.SeasonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class SeasonForm {

    private UUID id;

    @NotBlank
    private String name;

    private int year;

    private int number;

    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    private boolean active;

    private SeasonFormat format = SeasonFormat.LEAGUE;

    private Integer totalRounds;

    private int legs = 1;
}
