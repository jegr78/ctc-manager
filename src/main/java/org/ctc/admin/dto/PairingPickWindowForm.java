package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class PairingPickWindowForm {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime pickDeadlineOverride;
    @NotNull @PositiveOrZero
    private Long expectedVersion;
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}")
    private String expectedScope;
    @NotBlank @Pattern(regexp = "[0-9a-f-]{36}:[0-9]+")
    private String expectedGeneration;
}
