package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TeamForm {

	private UUID id;

	@NotBlank
	private String name;

	@NotBlank
	private String shortName;

	private String primaryColor;

	private String secondaryColor;

	private String accentColor;
}
