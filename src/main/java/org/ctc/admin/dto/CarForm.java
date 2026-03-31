package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class CarForm {

    private UUID id;

    @NotBlank
    private String manufacturer;

    @NotBlank
    private String name;
}
