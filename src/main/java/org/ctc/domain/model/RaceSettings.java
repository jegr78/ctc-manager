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
	@Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimeProgressionMode timeProgressionMode = TimeProgressionMode.MULTIPLIER;
    private Integer timeProgressionMultiplier;


	private String weather;
	private String timeOfDay;
	private String availableTyres;
	private String mandatoryTyres;

	public RaceSettings(Race race) {
		this.race = race;
	}

    public String getTimeProgressionDisplay() {
        if (timeProgressionMode == TimeProgressionMode.EQUAL_CONDITIONS) return "Equal Conditions";
        return timeProgressionMultiplier == null ? "Not set" : timeProgressionMultiplier + "x";
    }

    public boolean isTimeProgressionComplete() {
        return timeProgressionMode == TimeProgressionMode.EQUAL_CONDITIONS
                ? timeProgressionMultiplier == null
                : timeProgressionMode == TimeProgressionMode.MULTIPLIER
                    && timeProgressionMultiplier != null && timeProgressionMultiplier >= 0;
    }

	public boolean isComplete() {
		return numberOfLaps != null && tyreWearMultiplier != null
				&& fuelConsumptionMultiplier != null && refuelingSpeed != null
				&& hasText(initialFuel)
				&& numberOfRequiredPitStops != null && isTimeProgressionComplete()
				&& hasText(weather)
				&& hasText(timeOfDay)
				&& hasText(availableTyres)
				&& hasText(mandatoryTyres);
	}
}
