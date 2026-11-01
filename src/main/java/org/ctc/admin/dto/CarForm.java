package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.ctc.domain.model.CarCategory;
import org.ctc.domain.model.Drivetrain;
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

    private boolean metadataSubmitted;
    private CarCategory category;
    private Drivetrain drivetrain;
    @Min(value = 1000, message = "Model year must have four digits")
    @Max(value = 9999, message = "Model year must have four digits")
    private Integer modelYear;
    private boolean categoryOverride;
    private boolean drivetrainOverride;
    private boolean modelYearOverride;
}
