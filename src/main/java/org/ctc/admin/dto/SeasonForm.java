package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

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
}
