package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class DriverForm {

    private UUID id;

    @NotBlank
    private String psnId;

    @NotBlank
    private String nickname;

    private boolean active = true;

    private List<String> aliases = new ArrayList<>();
}
