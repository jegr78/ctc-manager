package org.ctc.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class IncidentDeadlineForm {
    @NotNull(message = "Choose a reporting deadline.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private LocalDateTime deadline;
    @NotBlank(message = "Enter the Berlin UTC offset.") @Size(max = 9, message = "Use a UTC offset such as +01:00 or +02:00.") private String offset;
    @NotNull(message = "Reload the reporting window before saving.") @Min(value = 0, message = "Reload the reporting window before saving.") private Long expectedVersion;
    @Size(max = 1000, message = "Use at most 1000 characters for the reason.") private String reason;
}
