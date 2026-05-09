package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class SeasonPhaseGroupForm {

    private UUID id;

    @NotBlank
    private String name;

    private Integer sortIndex;       // null → service auto-set max+1

    @NotNull
    private UUID phaseId;
}
