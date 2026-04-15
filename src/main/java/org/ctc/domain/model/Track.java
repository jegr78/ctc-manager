package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Entity
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

	public Track(String name, String country) {
		this.name = name;
		this.country = country;
	}

	public Track(String name) {
		this.name = name;
	}
}
