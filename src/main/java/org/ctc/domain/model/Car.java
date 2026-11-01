package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "cars", uniqueConstraints = @UniqueConstraint(columnNames = {"manufacturer", "name"}))
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Car extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@NotBlank
	@Column(nullable = false)
	private String manufacturer;

	@NotBlank
	@Column(nullable = false)
	private String name;

	@Column(name = "gt7_id")
	private String gt7Id;

	@Column(name = "image_url", length = 500)
	private String imageUrl;

    @Enumerated(EnumType.STRING)
    private CarCategory category;
    @Enumerated(EnumType.STRING)
    private Drivetrain drivetrain;
    private Integer modelYear;
    private Integer proposedModelYear;
    @Column(length = 500)
    private String categorySource;
    @Column(length = 500)
    private String drivetrainSource;
    @Column(length = 500)
    private String modelYearSource;
    @Column(length = 1000)
    private String modelYearEvidence;
    @Column(length = 1000)
    private String proposedModelYearEvidence;
    @Column(nullable = false)
    private boolean categoryOverride;
    @Column(nullable = false)
    private boolean drivetrainOverride;
    @Column(nullable = false)
    private boolean modelYearOverride;

	public Car(String manufacturer, String name) {
		this.manufacturer = manufacturer;
		this.name = name;
	}

	public String getDisplayName() {
		return manufacturer + " — " + name;
	}
}
