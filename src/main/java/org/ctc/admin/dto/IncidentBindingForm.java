package org.ctc.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IncidentBindingForm {
    @NotBlank(message = "Configure a Discord guild before binding.") @Pattern(regexp = "[0-9]{1,32}", message = "Use the configured numeric Discord guild ID.") private String guildId;
    @NotBlank(message = "Choose the pairing channel ID.") @Pattern(regexp = "[0-9]{1,32}", message = "Enter a numeric Discord channel ID, up to 32 digits.") private String channelId;
    @NotNull(message = "Reload the pairing before binding.") @Min(value = 0, message = "Reload the pairing before binding.") private Long expectedVersion;
}
