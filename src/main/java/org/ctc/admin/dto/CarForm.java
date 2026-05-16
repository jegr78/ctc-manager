package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CarForm {

	private UUID id;

	@NotBlank
	private String manufacturer;

	@NotBlank
	private String name;
}
