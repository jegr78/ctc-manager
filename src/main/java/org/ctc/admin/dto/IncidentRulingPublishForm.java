package org.ctc.admin.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IncidentRulingPublishForm {
    @NotNull @Min(0) private Long expectedVersion;
    @NotNull @Min(1) private Integer rulingVersion;
    @NotBlank @Size(max = 200) private String generation = "";
}
