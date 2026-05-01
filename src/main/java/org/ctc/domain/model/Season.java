package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "seasons")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"phases", "seasonDrivers", "seasonTeams", "cars", "tracks"})
public class Season extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@NotBlank
	@Column(nullable = false)
	private String name;

	@Column(name = "season_year", nullable = false)
	private int year;

	@Column(name = "season_number", nullable = false)
	private int number;

	private String description;

	@Column(nullable = false)
	private boolean active = false;

	@OneToMany(mappedBy = "season", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("sortIndex ASC")
	private List<SeasonPhase> phases = new ArrayList<>();

	@OneToMany(mappedBy = "season", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<SeasonDriver> seasonDrivers = new ArrayList<>();

	@OneToMany(mappedBy = "season", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<SeasonTeam> seasonTeams = new ArrayList<>();

	@ManyToMany
	@JoinTable(name = "season_cars",
			joinColumns = @JoinColumn(name = "season_id"),
			inverseJoinColumns = @JoinColumn(name = "car_id"))
	@OrderBy("manufacturer ASC, name ASC")
	private List<Car> cars = new ArrayList<>();

	@ManyToMany
	@JoinTable(name = "season_tracks",
			joinColumns = @JoinColumn(name = "season_id"),
			inverseJoinColumns = @JoinColumn(name = "track_id"))
	@OrderBy("name ASC")
	private List<Track> tracks = new ArrayList<>();

	public Season(String name) {
		this.name = name;
	}

	public Season(String name, int year, int number) {
		this.name = name;
		this.year = year;
		this.number = number;
	}

	public String getDisplayLabel() {
		return year + " | #" + number + " | " + name;
	}

	/**
	 * Convenience method: returns the list of Teams participating in this season,
	 * ordered by short name. Derived from the seasonTeams association.
	 */
	public List<Team> getTeams() {
		return seasonTeams.stream()
				.map(SeasonTeam::getTeam)
				.sorted(Comparator.comparing(Team::getShortName))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * Convenience method: returns all matchdays across all phases of this season,
	 * sorted first by phase sortIndex, then by matchday sortIndex. Derived from the phases association.
	 */
	public List<Matchday> getMatchdays() {
		return phases.stream()
				.flatMap(p -> p.getMatchdays().stream())
				.sorted(Comparator.comparingInt((Matchday m) -> m.getPhase().getSortIndex())
						.thenComparingInt(Matchday::getSortIndex))
				.toList();
	}

	/**
	 * Convenience method: adds a team to this season by creating a SeasonTeam entry.
	 * Does nothing if the team is already registered for this season.
	 */
	public void addTeam(Team team) {
		boolean alreadyPresent = seasonTeams.stream()
				.anyMatch(st -> st.getTeam().getId().equals(team.getId()));
		if (!alreadyPresent) {
			seasonTeams.add(new SeasonTeam(this, team));
		}
	}

	/**
	 * Convenience method: removes a team from this season by removing the SeasonTeam entry.
	 */
	public void removeTeam(Team team) {
		seasonTeams.removeIf(st -> st.getTeam().getId().equals(team.getId()));
	}

	/**
	 * Convenience method: removes a team by ID from this season.
	 */
	public void removeTeamById(UUID teamId) {
		seasonTeams.removeIf(st -> st.getTeam().getId().equals(teamId));
	}

	/**
	 * Convenience method: checks if this season contains the given team.
	 */
	public boolean containsTeam(Team team) {
		return seasonTeams.stream().anyMatch(st -> st.getTeam().getId().equals(team.getId()));
	}

	/**
	 * Convenience method: finds the SeasonTeam entry for the given team.
	 */
	public Optional<SeasonTeam> findSeasonTeam(Team team) {
		return seasonTeams.stream()
				.filter(st -> st.getTeam().getId().equals(team.getId()))
				.findFirst();
	}

	/**
	 * Returns only active (non-replaced) teams, ordered by short name.
	 */
	public List<Team> getActiveTeams() {
		return seasonTeams.stream()
				.filter(st -> !st.isReplaced())
				.map(SeasonTeam::getTeam)
				.sorted(Comparator.comparing(Team::getShortName))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * Returns eligible teams for match pairings: filters out parent teams
	 * that have sub-teams in the season (only sub-teams compete).
	 */
	public List<Team> getEligibleTeams() {
		List<Team> activeTeams = getActiveTeams();
		Set<UUID> parentIdsWithSubs = activeTeams.stream()
				.filter(Team::isSubTeam)
				.map(t -> t.getParentTeam().getId())
				.collect(Collectors.toSet());
		return activeTeams.stream()
				.filter(t -> !parentIdsWithSubs.contains(t.getId()))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * Builds a map from replaced team IDs to their final active successor team ID.
	 */
	public Map<UUID, UUID> buildSuccessionMap() {
		Map<UUID, UUID> map = new HashMap<>();
		for (SeasonTeam st : seasonTeams) {
			if (st.isReplaced()) {
				UUID activeTeamId = st.getActiveSeasonTeam().getTeam().getId();
				map.put(st.getTeam().getId(), activeTeamId);
			}
		}
		return map;
	}
}
