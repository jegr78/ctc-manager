package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ctc.domain.model.CarPoolMode;

@Getter
@Setter
@NoArgsConstructor
public class SeasonForm {

	private UUID id;

	@NotBlank
	private String name;

	private int year;

	private int number;

	private String description;

	private boolean active;

    private CarPoolMode carPoolMode = CarPoolMode.FIXED_CARS;
}
