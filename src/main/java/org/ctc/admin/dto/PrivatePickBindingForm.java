package org.ctc.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PrivatePickBindingForm {
    @NotNull private UUID seasonId;
    @NotNull private UUID teamId;
    @Pattern(regexp = "[0-9]{1,32}", message = "Enter a Discord channel ID containing 1 to 32 digits")
    @jakarta.validation.constraints.NotBlank
    private String channelId = "";
}
