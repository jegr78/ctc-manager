package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "tracks")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Track extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@NotBlank
	@Column(nullable = false, unique = true)
	private String name;

	private String country;

	@Column(name = "image_url", length = 500)
	private String imageUrl;

    @Convert(converter = TrackTimeOptionsConverter.class)
    @Column(name = "time_of_day_options", length = 3000)
    private java.util.List<String> timeOfDayOptions = java.util.List.of();

    @Column(name = "rain_capability", nullable = false, length = 16)
    private String rainCapability = "UNKNOWN";

    @Column(name = "environment_version", nullable = false)
    private long environmentVersion;

	public Track(String name, String country) {
		this.name = name;
		this.country = country;
	}

	public Track(String name) {
		this.name = name;
	}
}
