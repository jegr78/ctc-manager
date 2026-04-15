package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "race_scorings")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class RaceScoring extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@NotBlank
	@Column(nullable = false, unique = true)
	private String name;

	@Column(name = "race_points", nullable = false)
	private String racePoints;

	@Column(name = "quali_points")
	private String qualiPoints;

	@Column(name = "fastest_lap_points", nullable = false)
	private int fastestLapPoints = 0;

	public RaceScoring(String name, String racePoints, String qualiPoints, int fastestLapPoints) {
		this.name = name;
		this.racePoints = racePoints;
		this.qualiPoints = qualiPoints;
		this.fastestLapPoints = fastestLapPoints;
	}

	private static int[] parseCsv(String csv) {
		return Arrays.stream(csv.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.mapToInt(Integer::parseInt)
				.toArray();
	}

	private static boolean isMonotonicallyDecreasing(int[] values) {
		for (int i = 1; i < values.length; i++) {
			if (values[i] > values[i - 1]) {
				return false;
			}
		}
		return true;
	}

	public int[] getRacePointsArray() {
		return parseCsv(racePoints);
	}

	public int[] getQualiPointsArray() {
		if (qualiPoints == null || qualiPoints.isBlank()) {
			return new int[]{};
		}
		return parseCsv(qualiPoints);
	}

	public boolean canParse() {
		try {
			getRacePointsArray();
			getQualiPointsArray();
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public boolean isValid() {
		if (!canParse()) return false;
		return isMonotonicallyDecreasing(getRacePointsArray())
				&& isMonotonicallyDecreasing(getQualiPointsArray());
	}
}
