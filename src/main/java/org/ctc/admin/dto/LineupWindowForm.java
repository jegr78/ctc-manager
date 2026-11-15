package org.ctc.admin.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.ctc.domain.model.LineupWindowKind;

@Getter
@Setter
public class LineupWindowForm {
    @NotNull private LineupWindowKind kind;
    @NotNull @Min(0) private Integer expectedSequence;
    @NotBlank @Pattern(regexp = "[0-9a-f-]{36}:[0-9]+") private String expectedGeneration;
    @NotBlank @Size(max = 500) private String reason;
}
