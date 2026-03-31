package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class DriverForm {

    private UUID id;

    @NotBlank
    private String psnId;

    @NotBlank
    private String nickname;

    private boolean active = true;
}
