package org.ctc.domain.model;

import static org.springframework.util.StringUtils.hasText;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "race_settings")
@Getter
@Setter
@NoArgsConstructor
public class RaceSettings extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "race_id", nullable = false, unique = true)
	private Race race;

	private Integer numberOfLaps;
	private Integer tyreWearMultiplier;
	private Integer fuelConsumptionMultiplier;
	private Integer refuelingSpeed;
	private String initialFuel;
	private Integer numberOfRequiredPitStops;
	private Integer timeProgressionMultiplier;
	private String weather;
	private String timeOfDay;
	private String availableTyres;
	private String mandatoryTyres;

	public RaceSettings(Race race) {
		this.race = race;
	}

	public boolean isComplete() {
		return numberOfLaps != null && tyreWearMultiplier != null
				&& fuelConsumptionMultiplier != null && refuelingSpeed != null
				&& hasText(initialFuel)
				&& numberOfRequiredPitStops != null && timeProgressionMultiplier != null
				&& hasText(weather)
				&& hasText(timeOfDay)
				&& hasText(availableTyres)
				&& hasText(mandatoryTyres);
	}
}
