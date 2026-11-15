package org.ctc.admin.dto;

import jakarta.validation.constraints.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LineupCorrectionForm {
    @NotNull @Min(0) private Long expectedVersion;
    @NotBlank @Pattern(regexp = "[0-9a-f-]{36}:[0-9]+") private String expectedGeneration;
    @NotNull private UUID requestId;
    private List<UUID> drivers = new ArrayList<>();
    @NotBlank @Size(max = 500) private String reason;
}
