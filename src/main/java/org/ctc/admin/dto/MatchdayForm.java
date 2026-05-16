package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MatchdayForm {

    private UUID id;

    @NotBlank
    private String label;

    private int sortIndex;

    @NotNull
    private UUID seasonId;
}
