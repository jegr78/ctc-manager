package org.ctc.admin;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.service.TeamCardService;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.PlayoffService;
import org.ctc.domain.service.ScoringService;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class TestDataService {

	private final SeasonRepository seasonRepository;
	private final TeamRepository teamRepository;
	private final DriverRepository driverRepository;
	private final RaceScoringRepository raceScoringRepository;
	private final MatchScoringRepository matchScoringRepository;
	private final SeasonDriverRepository seasonDriverRepository;
	private final MatchdayRepository matchdayRepository;
	private final MatchRepository matchRepository;
	private final RaceRepository raceRepository;
	private final RaceLineupRepository raceLineupRepository;
	private final RaceResultRepository raceResultRepository;
	private final ScoringService scoringService;
	private final PlayoffService playoffService;
	private final PlayoffRepository playoffRepository;
	private final PlayoffRoundRepository playoffRoundRepository;
	private final PlayoffMatchupRepository playoffMatchupRepository;
	private final EntityManager entityManager;
	private final TeamCardService teamCardService;
	@org.springframework.beans.factory.annotation.Value("${app.upload-dir:data/dev/uploads}")
	private String uploadDir;

	@Transactional
	public void seed() {
		if (seasonRepository.count() > 0) {
			log.debug("Seed data already present, skipping");
			return;
		}
		var scorings = seedScorings();
		var teams = seedTeams();
		seedSubTeams(teams);
		copyDemoLogos(teams);
		seedSeasons(teams, scorings);
		seedDrivers();
		seedAliases();
		seedSeasonDrivers();
		seedMatchdaysAndResults();
		seedRaceLineups();
		seedPlayoffs();
		generateTeamCards();
		log.info("Seed data created: {} teams, {} seasons, {} drivers, {} race-lineups, {} results",
				teamRepository.count(), seasonRepository.count(), driverRepository.count(),
				raceLineupRepository.count(), raceResultRepository.count());
	}

	private ScoringDefaults seedScorings() {
		var raceScoring = raceScoringRepository.save(
				new RaceScoring("CTC Standard", "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2));
		var matchScoring = matchScoringRepository.save(
				new MatchScoring("Standard 3-1-0", 3, 1, 0));
		log.info("Created default scoring presets: {} / {}", raceScoring.getName(), matchScoring.getName());
		return new ScoringDefaults(raceScoring, matchScoring);
	}

	private List<Team> seedTeams() {
		var teams = teamRepository.saveAll(List.of(
				team("Vortex Racing", "VRX", "#FF4500", "#1A1A2E", "#FFFFFF"),
				team("Sigma Motorsport", "SGM", "#00CED1", "#2D2D44", "#FFFFFF"),
				team("Adrenaline Racing", "ADR", "#FFD700", "#1B1B2F", "#000000"),
				team("Turbo Racing", "TBR", "#32CD32", "#0D0D1A", "#FFFFFF"),
				team("Icicle Motorsport", "ICL", "#87CEEB", "#1C1C3A", "#000000"),
				team("Silverstone Velocity Team", "SVT", "#C0C0C0", "#2A2A4A", "#000000"),
				team("Nitro Fuel Racing", "NFR", "#FF1493", "#0F0F2D", "#FFFFFF"),
				team("Eclipse Grand Prix", "EGP", "#9400D3", "#121230", "#FFFFFF"),
				team("Horizon Motorsport", "HMS", "#FF6347", "#1E1E3E", "#FFFFFF"),
				team("Powershift Racing", "PWR", "#00FF7F", "#161638", "#000000")
		));
		return teams;
	}

	private Team subTeam(String name, String shortName, Team parent) {
		return subTeam(name, shortName, parent, parent.getPrimaryColor(), parent.getSecondaryColor(), parent.getAccentColor());
	}

	private Team subTeam(String name, String shortName, Team parent, String primary, String secondary, String accent) {
		var t = new Team(name, shortName, parent);
		t.setPrimaryColor(primary);
		t.setSecondaryColor(secondary);
		t.setAccentColor(accent);
		return t;
	}

	private Team team(String name, String shortName, String primary, String secondary, String accent) {
		var t = new Team(name, shortName);
		t.setPrimaryColor(primary);
		t.setSecondaryColor(secondary);
		t.setAccentColor(accent);
		return t;
	}

	private void seedSubTeams(List<Team> teams) {
		var vrx = teams.stream().filter(t -> t.getShortName().equals("VRX")).findFirst()
				.orElseThrow(() -> new EntityNotFoundException("Team", "VRX"));
		teamRepository.save(subTeam("Vortex Racing A", "VRX A", vrx));
		teamRepository.save(subTeam("Vortex Racing B", "VRX B", vrx));

		var sgm = teams.stream().filter(t -> t.getShortName().equals("SGM")).findFirst()
				.orElseThrow(() -> new EntityNotFoundException("Team", "SGM"));
		teamRepository.save(subTeam("Sigma Motorsport Blue", "SGM B", sgm));
		teamRepository.save(subTeam("Sigma Motorsport Silver", "SGM S", sgm));

		var tbr = teams.stream().filter(t -> t.getShortName().equals("TBR")).findFirst()
				.orElseThrow(() -> new EntityNotFoundException("Team", "TBR"));
		teamRepository.save(subTeam("Turbo Racing Red", "TBR R", tbr));
		teamRepository.save(subTeam("Turbo Racing Blue", "TBR B", tbr));
		teamRepository.save(subTeam("Turbo Racing Green", "TBR G", tbr));

		log.info("Created sub-teams: VRX(2), SGM(2), TBR(3)");
	}

	private void seedSeasons(List<Team> parentTeams, ScoringDefaults scorings) {
		var allTeams = teamRepository.findAll();

		// Helper to find parent team (no parent) by shortName
		java.util.function.Function<String, Team> findParent = (shortName) ->
				allTeams.stream()
						.filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() == null)
						.findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));

		// Helper to find sub-team by shortName (has parent)
		java.util.function.Function<String, Team> findSub = (shortName) ->
				allTeams.stream()
						.filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() != null)
						.findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));

		// S1 2023 Group A: Round Robin (6 teams, mix of parents and sub-teams) per D-06
		var s1a = createSeason("Group A", 2023, 1, "Group A, Regular Season", scorings);
		s1a.setFormat(SeasonFormat.ROUND_ROBIN);
		List.of(findParent.apply("ADR"), findParent.apply("ICL"), findParent.apply("SVT"),
						findParent.apply("NFR"), findParent.apply("HMS"), findSub.apply("VRX A"))
				.forEach(s1a::addTeam);
		seasonRepository.save(s1a);

		// S1 2023 Group B: Round Robin (6 teams, mix of parents and sub-teams) per D-06
		var s1b = createSeason("Group B", 2023, 1, "Group B, Regular Season", scorings);
		s1b.setFormat(SeasonFormat.ROUND_ROBIN);
		List.of(findParent.apply("EGP"), findParent.apply("PWR"),
						findSub.apply("VRX B"), findSub.apply("SGM B"), findSub.apply("SGM S"), findSub.apply("TBR R"))
				.forEach(s1b::addTeam);
		seasonRepository.save(s1b);

		// S2 2024: Swiss format (10 parent teams only) per D-05
		var s2 = createSeason("Regular Season", 2024, 2, "Round Robin", scorings);
		s2.setFormat(SeasonFormat.SWISS);
		parentTeams.forEach(s2::addTeam);
		seasonRepository.save(s2);

		// Set ratings for S1 Group A (2023, ratings -5 from Season 4 baseline)
		s1a.findSeasonTeam(findParent.apply("ADR")).ifPresent(st -> st.setRating(88));  // 93 - 5
		s1a.findSeasonTeam(findParent.apply("ICL")).ifPresent(st -> st.setRating(82));  // 87 - 5
		s1a.findSeasonTeam(findParent.apply("SVT")).ifPresent(st -> st.setRating(80));  // 85 - 5
		s1a.findSeasonTeam(findParent.apply("NFR")).ifPresent(st -> st.setRating(79));  // 84 - 5
		s1a.findSeasonTeam(findParent.apply("HMS")).ifPresent(st -> st.setRating(83));  // 88 - 5
		s1a.findSeasonTeam(findSub.apply("VRX A")).ifPresent(st -> st.setRating(87));   // 92 - 5
		seasonRepository.save(s1a);

		// Set ratings for S1 Group B (2023, ratings -5 from Season 4 baseline)
		s1b.findSeasonTeam(findParent.apply("EGP")).ifPresent(st -> st.setRating(87));  // 92 - 5
		s1b.findSeasonTeam(findParent.apply("PWR")).ifPresent(st -> st.setRating(81));  // 86 - 5
		s1b.findSeasonTeam(findSub.apply("VRX B")).ifPresent(st -> st.setRating(82));   // 87 - 5
		s1b.findSeasonTeam(findSub.apply("SGM B")).ifPresent(st -> st.setRating(88));   // 93 - 5
		s1b.findSeasonTeam(findSub.apply("SGM S")).ifPresent(st -> st.setRating(80));   // 85 - 5
		s1b.findSeasonTeam(findSub.apply("TBR R")).ifPresent(st -> st.setRating(80));   // 85 - 5
		seasonRepository.save(s1b);

		// Set ratings for S2 (2024, ratings -3 from Season 4 baseline)
		s2.findSeasonTeam(findParent.apply("ADR")).ifPresent(st -> st.setRating(90));   // 93 - 3
		s2.findSeasonTeam(findParent.apply("ICL")).ifPresent(st -> st.setRating(84));   // 87 - 3
		s2.findSeasonTeam(findParent.apply("SVT")).ifPresent(st -> st.setRating(82));   // 85 - 3
		s2.findSeasonTeam(findParent.apply("NFR")).ifPresent(st -> st.setRating(81));   // 84 - 3
		s2.findSeasonTeam(findParent.apply("HMS")).ifPresent(st -> st.setRating(85));   // 88 - 3
		s2.findSeasonTeam(findParent.apply("PWR")).ifPresent(st -> st.setRating(83));   // 86 - 3
		s2.findSeasonTeam(findParent.apply("EGP")).ifPresent(st -> st.setRating(89));   // 92 - 3
		s2.findSeasonTeam(findParent.apply("VRX")).ifPresent(st -> st.setRating(85));   // avg(92,87) - 3 = 89.5 rounded
		s2.findSeasonTeam(findParent.apply("SGM")).ifPresent(st -> st.setRating(86));   // avg(93,85) - 3 = 88 rounded
		s2.findSeasonTeam(findParent.apply("TBR")).ifPresent(st -> st.setRating(84));   // avg(85,84,83) - 3 = 81 rounded
		seasonRepository.save(s2);

		// Season 4 - 2026: 14 match teams (7 standalone parents + 7 sub-teams) per D-04
		// VRX, SGM, TBR parents do NOT participate as match teams
		var s4 = createSeason("Regular Season", 2026, 4, null, scorings);
		s4.setActive(true);
		s4.setFormat(SeasonFormat.LEAGUE);
		List.of(
				findSub.apply("VRX A"),
				findSub.apply("VRX B"),
				findSub.apply("SGM B"),
				findSub.apply("SGM S"),
				findSub.apply("TBR R"),
				findSub.apply("TBR B"),
				findSub.apply("TBR G"),
				findParent.apply("ADR"),
				findParent.apply("ICL"),
				findParent.apply("SVT"),
				findParent.apply("NFR"),
				findParent.apply("EGP"),
				findParent.apply("HMS"),
				findParent.apply("PWR")
		).forEach(s4::addTeam);
		seasonRepository.save(s4);

		// Set ratings for active season
		s4.findSeasonTeam(findSub.apply("VRX A")).ifPresent(st -> st.setRating(92));
		s4.findSeasonTeam(findSub.apply("VRX B")).ifPresent(st -> st.setRating(87));
		s4.findSeasonTeam(findSub.apply("SGM B")).ifPresent(st -> st.setRating(93));
		s4.findSeasonTeam(findSub.apply("SGM S")).ifPresent(st -> st.setRating(85));
		s4.findSeasonTeam(findSub.apply("TBR R")).ifPresent(st -> st.setRating(85));
		s4.findSeasonTeam(findSub.apply("TBR B")).ifPresent(st -> st.setRating(84));
		s4.findSeasonTeam(findSub.apply("TBR G")).ifPresent(st -> st.setRating(83));
		s4.findSeasonTeam(findParent.apply("ADR")).ifPresent(st -> st.setRating(93));
		s4.findSeasonTeam(findParent.apply("ICL")).ifPresent(st -> st.setRating(87));
		s4.findSeasonTeam(findParent.apply("SVT")).ifPresent(st -> st.setRating(85));
		s4.findSeasonTeam(findParent.apply("NFR")).ifPresent(st -> {
			st.setRating(84);
			st.setPrimaryColor("#1116aa");
			st.setAccentColor("#134f7c");
		});
		s4.findSeasonTeam(findParent.apply("EGP")).ifPresent(st -> st.setRating(92));
		s4.findSeasonTeam(findParent.apply("HMS")).ifPresent(st -> st.setRating(88));
		s4.findSeasonTeam(findParent.apply("PWR")).ifPresent(st -> st.setRating(86));
		seasonRepository.save(s4);
	}

	private void copyDemoLogos(List<Team> parentTeams) {
		var allTeams = teamRepository.findAll();
		Path uploadBase = Paths.get(uploadDir, "teams").toAbsolutePath().normalize();
		for (var team : allTeams) {
			String logoKey = team.isSubTeam() ? team.getParentTeam().getShortName() : team.getShortName();
			try {
				var resource = new ClassPathResource("demo/team-logos/" + logoKey + ".png");
				if (resource.exists()) {
					Path teamDir = uploadBase.resolve(team.getId().toString());
					Files.createDirectories(teamDir);
					Path target = teamDir.resolve(logoKey + ".png");
					try (var is = resource.getInputStream()) {
						Files.copy(is, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					}
					team.setLogoUrl("/uploads/teams/" + team.getId() + "/" + logoKey + ".png");
					teamRepository.save(team);
				}
			} catch (IOException e) {
				log.warn("Failed to copy demo logo for {}: {}", team.getShortName(), e.getMessage());
			}
		}
		log.info("Demo logos copied for {} teams", allTeams.size());
	}

	private Season createSeason(String name, int year, int number, String description, ScoringDefaults scorings) {
		var season = new Season(name, year, number);
		season.setDescription(description);
		season.setRaceScoring(scorings.raceScoring());
		season.setMatchScoring(scorings.matchScoring());
		return season;
	}

	private void seedDrivers() {
		// 10 drivers per team (100 total)
		for (String team : List.of("VRX", "SGM", "ADR", "TBR", "ICL", "SVT", "NFR", "EGP", "HMS", "PWR")) {
			for (int i = 1; i <= 10; i++) {
				driver(team + "_Driver" + String.format("%02d", i), team + " Driver " + i);
			}
		}
	}

	private void seedAliases() {
		var allDrivers = driverRepository.findAll();

		java.util.function.Function<String, org.ctc.domain.model.Driver> findDriver = psnId ->
				allDrivers.stream()
						.filter(d -> d.getPsnId().equals(psnId))
						.findFirst().orElseThrow(() -> new EntityNotFoundException("Driver", psnId));

		// Typical PSN ID changes
		var vrxDriver01 = findDriver.apply("VRX_Driver01");
		vrxDriver01.addAlias("VRX_OldPSN_01");
		driverRepository.save(vrxDriver01);

		var sgmDriver03 = findDriver.apply("SGM_Driver03");
		sgmDriver03.addAlias("SGM_PreviousID");
		sgmDriver03.addAlias("SGM_OriginalName");
		driverRepository.save(sgmDriver03);

		var adrDriver05 = findDriver.apply("ADR_Driver05");
		adrDriver05.addAlias("ADR_Veteran05");
		driverRepository.save(adrDriver05);
	}

	private void seedSeasonDrivers() {
		var allTeams = teamRepository.findAll();
		var allDrivers = driverRepository.findAll();
		var allSeasons = seasonRepository.findAll();

		java.util.function.Function<String, Team> findParent = shortName ->
				allTeams.stream()
						.filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() == null)
						.findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));

		java.util.function.Function<String, Team> findSub = shortName ->
				allTeams.stream()
						.filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() != null)
						.findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));

		java.util.function.Function<String, Driver> findDriver = psnId ->
				allDrivers.stream()
						.filter(d -> d.getPsnId().equals(psnId))
						.findFirst().orElseThrow(() -> new EntityNotFoundException("Driver", psnId));

		java.util.function.Function<Integer, Season> findSeason = year ->
				allSeasons.stream()
						.filter(s -> s.getYear() == year)
						.findFirst().orElseThrow(() -> new EntityNotFoundException("Season", year));

		// Season 4 - 2026: All 10 parent teams with 10 drivers each
		var s4 = findSeason.apply(2026);
		for (String team : List.of("VRX", "SGM", "ADR", "TBR", "ICL", "SVT", "NFR", "EGP", "HMS", "PWR")) {
			for (int i = 1; i <= 10; i++) {
				seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(team + "_Driver" + String.format("%02d", i)), findParent.apply(team)));
			}
		}

		// Helper to find season by year and name
		java.util.function.BiFunction<Integer, String, Season> findSeasonByName = (year, name) ->
				allSeasons.stream()
						.filter(s -> s.getYear() == year && s.getName().equals(name))
						.findFirst().orElseThrow(() -> new EntityNotFoundException("Season", name));

		// S1 2023 Group A: 6 drivers per team (ADR, ICL, SVT, NFR, HMS + VRX A via parent VRX)
		var s1a = findSeasonByName.apply(2023, "Group A");
		assignSeasonDrivers(s1a, "ADR", driverIds("ADR", 1, 6), findParent, findDriver);
		assignSeasonDrivers(s1a, "ICL", driverIds("ICL", 1, 6), findParent, findDriver);
		assignSeasonDrivers(s1a, "SVT", driverIds("SVT", 1, 6), findParent, findDriver);
		assignSeasonDrivers(s1a, "NFR", driverIds("NFR", 1, 6), findParent, findDriver);
		assignSeasonDrivers(s1a, "HMS", driverIds("HMS", 1, 6), findParent, findDriver);
		// VRX A sub-team: SeasonDriver uses parent team VRX
		assignSeasonDrivers(s1a, "VRX", driverIds("VRX", 1, 6), findParent, findDriver);

		// S1 2023 Group B: 6 drivers per team (EGP, PWR + VRX B, SGM B, SGM S, TBR R via parents)
		var s1b = findSeasonByName.apply(2023, "Group B");
		assignSeasonDrivers(s1b, "EGP", driverIds("EGP", 1, 6), findParent, findDriver);
		assignSeasonDrivers(s1b, "PWR", driverIds("PWR", 1, 6), findParent, findDriver);
		// VRX B: different drivers than Group A, parent team VRX
		assignSeasonDrivers(s1b, "VRX", driverIds("VRX", 5, 10), findParent, findDriver);
		// SGM B + SGM S: combined unique drivers 1-10 for parent team SGM (unique constraint)
		assignSeasonDrivers(s1b, "SGM", driverIds("SGM", 1, 10), findParent, findDriver);
		// TBR R: parent team TBR
		assignSeasonDrivers(s1b, "TBR", driverIds("TBR", 1, 6), findParent, findDriver);

		// S2 2024: 10 parent teams, 6 drivers each (01-06)
		var s2 = findSeasonByName.apply(2024, "Regular Season");
		for (String team : List.of("VRX", "SGM", "ADR", "TBR", "ICL", "SVT", "NFR", "EGP", "HMS", "PWR")) {
			assignSeasonDrivers(s2, team, driverIds(team, 1, 6), findParent, findDriver);
		}

		log.info("Created season-driver assignments: s4={}, s1a={}, s1b={}, s2={}",
				seasonDriverRepository.findBySeasonId(s4.getId()).size(),
				seasonDriverRepository.findBySeasonId(s1a.getId()).size(),
				seasonDriverRepository.findBySeasonId(s1b.getId()).size(),
				seasonDriverRepository.findBySeasonId(s2.getId()).size());
	}

	private List<String> driverIds(String teamPrefix, int from, int to) {
		var ids = new java.util.ArrayList<String>();
		for (int i = from; i <= to; i++) {
			ids.add(teamPrefix + "_Driver" + String.format("%02d", i));
		}
		return ids;
	}

	private void assignSeasonDrivers(Season season, String teamShortName,
	                                 List<String> driverPsnIds,
	                                 java.util.function.Function<String, Team> teamFinder,
	                                 java.util.function.Function<String, Driver> driverFinder) {
		var team = teamFinder.apply(teamShortName);
		for (String psnId : driverPsnIds) {
			seasonDriverRepository.save(new SeasonDriver(season, driverFinder.apply(psnId), team));
		}
	}

	private void seedMatchdaysAndResults() {
		var raceScoring = raceScoringRepository.findAll().getFirst();
		var allSeasons = seasonRepository.findAll();
		var allTeams = teamRepository.findAll();
		var allDrivers = driverRepository.findAll();

		// Helper lambdas
		java.util.function.Function<String, Team> findParent = shortName ->
				allTeams.stream()
						.filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() == null)
						.findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));

		java.util.function.Function<String, Team> findSub = shortName ->
				allTeams.stream()
						.filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() != null)
						.findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));

		java.util.function.Function<String, Driver> findDriver = psnId ->
				allDrivers.stream()
						.filter(d -> d.getPsnId().equals(psnId))
						.findFirst().orElseThrow(() -> new EntityNotFoundException("Driver", psnId));

		// Season lookups
		var s4 = allSeasons.stream().filter(s -> s.getYear() == 2026 && s.getNumber() == 4).findFirst().orElseThrow();
		var s2 = allSeasons.stream().filter(s -> s.getYear() == 2024 && s.getNumber() == 2).findFirst().orElseThrow();
		var s1a = allSeasons.stream().filter(s -> s.getYear() == 2023 && s.getName().equals("Group A")).findFirst().orElseThrow();
		var s1b = allSeasons.stream().filter(s -> s.getYear() == 2023 && s.getName().equals("Group B")).findFirst().orElseThrow();

		// Position rotation patterns (6 home + 6 away = positions 1-12)
		int[][] homePositions = {
				{1, 3, 5, 7, 9, 11},   // MD1: home wins
				{2, 4, 6, 8, 10, 12},  // MD2: away wins
				{1, 2, 5, 6, 9, 10},   // MD3: home wins (different spread)
				{3, 4, 7, 8, 11, 12},  // MD4: away wins
				{1, 4, 5, 8, 9, 12}    // MD5: close match
		};
		int[] awayPositions0 = {2, 4, 6, 8, 10, 12};
		int[] awayPositions1 = {1, 3, 5, 7, 9, 11};
		int[] awayPositions2 = {3, 4, 7, 8, 11, 12};
		int[] awayPositions3 = {1, 2, 5, 6, 9, 10};
		int[] awayPositions4 = {2, 3, 6, 7, 10, 11};
		int[][] awayPositions = {awayPositions0, awayPositions1, awayPositions2, awayPositions3, awayPositions4};
		int[] fastestLapPositions = {1, 2, 3, 4, 5}; // Rotates per matchday

		// Driver assignments per team (6 drivers each) - reuse SeasonDriver assignments
		// S4 League: 14 teams - 7 standalone parents + 7 sub-teams
		var s4TeamDrivers = new java.util.LinkedHashMap<Team, Driver[]>();
		// 7 standalone parents
		s4TeamDrivers.put(findParent.apply("ADR"), new Driver[]{
				findDriver.apply("ADR_Driver01"), findDriver.apply("ADR_Driver02"), findDriver.apply("ADR_Driver03"),
				findDriver.apply("ADR_Driver04"), findDriver.apply("ADR_Driver05"), findDriver.apply("ADR_Driver06")});
		s4TeamDrivers.put(findParent.apply("ICL"), new Driver[]{
				findDriver.apply("ICL_Driver01"), findDriver.apply("ICL_Driver02"), findDriver.apply("ICL_Driver03"),
				findDriver.apply("ICL_Driver04"), findDriver.apply("ICL_Driver05"), findDriver.apply("ICL_Driver06")});
		s4TeamDrivers.put(findParent.apply("SVT"), new Driver[]{
				findDriver.apply("SVT_Driver01"), findDriver.apply("SVT_Driver02"), findDriver.apply("SVT_Driver03"),
				findDriver.apply("SVT_Driver04"), findDriver.apply("SVT_Driver05"), findDriver.apply("SVT_Driver06")});
		s4TeamDrivers.put(findParent.apply("NFR"), new Driver[]{
				findDriver.apply("NFR_Driver01"), findDriver.apply("NFR_Driver02"), findDriver.apply("NFR_Driver03"),
				findDriver.apply("NFR_Driver04"), findDriver.apply("NFR_Driver05"), findDriver.apply("NFR_Driver06")});
		s4TeamDrivers.put(findParent.apply("EGP"), new Driver[]{
				findDriver.apply("EGP_Driver01"), findDriver.apply("EGP_Driver02"), findDriver.apply("EGP_Driver03"),
				findDriver.apply("EGP_Driver04"), findDriver.apply("EGP_Driver05"), findDriver.apply("EGP_Driver06")});
		s4TeamDrivers.put(findParent.apply("HMS"), new Driver[]{
				findDriver.apply("HMS_Driver01"), findDriver.apply("HMS_Driver02"), findDriver.apply("HMS_Driver03"),
				findDriver.apply("HMS_Driver04"), findDriver.apply("HMS_Driver05"), findDriver.apply("HMS_Driver06")});
		s4TeamDrivers.put(findParent.apply("PWR"), new Driver[]{
				findDriver.apply("PWR_Driver01"), findDriver.apply("PWR_Driver02"), findDriver.apply("PWR_Driver03"),
				findDriver.apply("PWR_Driver04"), findDriver.apply("PWR_Driver05"), findDriver.apply("PWR_Driver06")});
		// 7 sub-teams (using parent team's drivers)
		s4TeamDrivers.put(findSub.apply("VRX A"), new Driver[]{
				findDriver.apply("VRX_Driver01"), findDriver.apply("VRX_Driver02"), findDriver.apply("VRX_Driver03"),
				findDriver.apply("VRX_Driver04"), findDriver.apply("VRX_Driver05"), findDriver.apply("VRX_Driver06")});
		s4TeamDrivers.put(findSub.apply("VRX B"), new Driver[]{
				findDriver.apply("VRX_Driver05"), findDriver.apply("VRX_Driver06"), findDriver.apply("VRX_Driver07"),
				findDriver.apply("VRX_Driver08"), findDriver.apply("VRX_Driver09"), findDriver.apply("VRX_Driver10")});
		s4TeamDrivers.put(findSub.apply("SGM B"), new Driver[]{
				findDriver.apply("SGM_Driver01"), findDriver.apply("SGM_Driver02"), findDriver.apply("SGM_Driver03"),
				findDriver.apply("SGM_Driver04"), findDriver.apply("SGM_Driver05"), findDriver.apply("SGM_Driver06")});
		s4TeamDrivers.put(findSub.apply("SGM S"), new Driver[]{
				findDriver.apply("SGM_Driver05"), findDriver.apply("SGM_Driver06"), findDriver.apply("SGM_Driver07"),
				findDriver.apply("SGM_Driver08"), findDriver.apply("SGM_Driver09"), findDriver.apply("SGM_Driver10")});
		s4TeamDrivers.put(findSub.apply("TBR R"), new Driver[]{
				findDriver.apply("TBR_Driver01"), findDriver.apply("TBR_Driver02"), findDriver.apply("TBR_Driver03"),
				findDriver.apply("TBR_Driver04"), findDriver.apply("TBR_Driver05"), findDriver.apply("TBR_Driver06")});
		s4TeamDrivers.put(findSub.apply("TBR B"), new Driver[]{
				findDriver.apply("TBR_Driver04"), findDriver.apply("TBR_Driver05"), findDriver.apply("TBR_Driver06"),
				findDriver.apply("TBR_Driver07"), findDriver.apply("TBR_Driver08"), findDriver.apply("TBR_Driver09")});
		s4TeamDrivers.put(findSub.apply("TBR G"), new Driver[]{
				findDriver.apply("TBR_Driver05"), findDriver.apply("TBR_Driver06"), findDriver.apply("TBR_Driver07"),
				findDriver.apply("TBR_Driver08"), findDriver.apply("TBR_Driver09"), findDriver.apply("TBR_Driver10")});

		// League season (S4 2026): 5 matchdays, 7 matches per MD, 1 race per match
		var s4Teams = new java.util.ArrayList<>(s4TeamDrivers.keySet());
		seedLeagueSeason(s4, s4Teams, s4TeamDrivers, homePositions, awayPositions, fastestLapPositions, raceScoring);

		// Swiss season (S2 2024): 5 matchdays, 5 matches per MD, 2 races per match
		var s2TeamDrivers = new java.util.LinkedHashMap<Team, Driver[]>();
		s2TeamDrivers.put(findParent.apply("VRX"), new Driver[]{
				findDriver.apply("VRX_Driver01"), findDriver.apply("VRX_Driver02"), findDriver.apply("VRX_Driver03"),
				findDriver.apply("VRX_Driver04"), findDriver.apply("VRX_Driver05"), findDriver.apply("VRX_Driver06")});
		s2TeamDrivers.put(findParent.apply("SGM"), new Driver[]{
				findDriver.apply("SGM_Driver01"), findDriver.apply("SGM_Driver02"), findDriver.apply("SGM_Driver03"),
				findDriver.apply("SGM_Driver04"), findDriver.apply("SGM_Driver05"), findDriver.apply("SGM_Driver06")});
		s2TeamDrivers.put(findParent.apply("ADR"), s4TeamDrivers.get(findParent.apply("ADR")));
		s2TeamDrivers.put(findParent.apply("TBR"), new Driver[]{
				findDriver.apply("TBR_Driver01"), findDriver.apply("TBR_Driver02"), findDriver.apply("TBR_Driver03"),
				findDriver.apply("TBR_Driver04"), findDriver.apply("TBR_Driver05"), findDriver.apply("TBR_Driver06")});
		s2TeamDrivers.put(findParent.apply("ICL"), s4TeamDrivers.get(findParent.apply("ICL")));
		s2TeamDrivers.put(findParent.apply("SVT"), s4TeamDrivers.get(findParent.apply("SVT")));
		s2TeamDrivers.put(findParent.apply("NFR"), s4TeamDrivers.get(findParent.apply("NFR")));
		s2TeamDrivers.put(findParent.apply("EGP"), s4TeamDrivers.get(findParent.apply("EGP")));
		s2TeamDrivers.put(findParent.apply("HMS"), s4TeamDrivers.get(findParent.apply("HMS")));
		s2TeamDrivers.put(findParent.apply("PWR"), s4TeamDrivers.get(findParent.apply("PWR")));
		var s2Teams = new java.util.ArrayList<>(s2TeamDrivers.keySet());
		seedSwissSeason(s2, s2Teams, s2TeamDrivers, homePositions, awayPositions, fastestLapPositions, raceScoring);

		// Round Robin (S1 2023 Group A): 3 matchdays, 3 matches per MD, 2 races per match
		var s1aTeamDrivers = new java.util.LinkedHashMap<Team, Driver[]>();
		s1aTeamDrivers.put(findParent.apply("ADR"), s4TeamDrivers.get(findParent.apply("ADR")));
		s1aTeamDrivers.put(findParent.apply("ICL"), s4TeamDrivers.get(findParent.apply("ICL")));
		s1aTeamDrivers.put(findParent.apply("SVT"), s4TeamDrivers.get(findParent.apply("SVT")));
		s1aTeamDrivers.put(findParent.apply("NFR"), s4TeamDrivers.get(findParent.apply("NFR")));
		s1aTeamDrivers.put(findParent.apply("HMS"), s4TeamDrivers.get(findParent.apply("HMS")));
		s1aTeamDrivers.put(findSub.apply("VRX A"), s4TeamDrivers.get(findSub.apply("VRX A")));
		var s1aTeams = new java.util.ArrayList<>(s1aTeamDrivers.keySet());
		seedRoundRobinSeason(s1a, s1aTeams, s1aTeamDrivers, homePositions, awayPositions, fastestLapPositions, raceScoring);

		// Round Robin (S1 2023 Group B): 3 matchdays, 3 matches per MD, 2 races per match
		var s1bTeamDrivers = new java.util.LinkedHashMap<Team, Driver[]>();
		s1bTeamDrivers.put(findParent.apply("EGP"), s4TeamDrivers.get(findParent.apply("EGP")));
		s1bTeamDrivers.put(findParent.apply("PWR"), s4TeamDrivers.get(findParent.apply("PWR")));
		s1bTeamDrivers.put(findSub.apply("VRX B"), s4TeamDrivers.get(findSub.apply("VRX B")));
		s1bTeamDrivers.put(findSub.apply("SGM B"), s4TeamDrivers.get(findSub.apply("SGM B")));
		s1bTeamDrivers.put(findSub.apply("SGM S"), s4TeamDrivers.get(findSub.apply("SGM S")));
		s1bTeamDrivers.put(findSub.apply("TBR R"), s4TeamDrivers.get(findSub.apply("TBR R")));
		var s1bTeams = new java.util.ArrayList<>(s1bTeamDrivers.keySet());
		seedRoundRobinSeason(s1b, s1bTeams, s1bTeamDrivers, homePositions, awayPositions, fastestLapPositions, raceScoring);

		log.info("Seeded matchdays and results: {} matchdays, {} races, {} results",
				matchdayRepository.count(), raceRepository.count(), raceResultRepository.count());
	}

	private void seedLeagueSeason(Season season, java.util.List<Team> teams,
	                              java.util.Map<Team, Driver[]> teamDrivers,
	                              int[][] homePositions, int[][] awayPositions, int[] fastestLapPositions,
	                              RaceScoring raceScoring) {
		// 5 matchdays, 7 matches per MD (14 teams), 1 race per match
		for (int mdIndex = 0; mdIndex < 5; mdIndex++) {
			var md = matchdayRepository.save(new Matchday(season, "Matchday " + (mdIndex + 1), mdIndex + 1));
			// Pair teams: rotate away teams each matchday
			for (int matchIdx = 0; matchIdx < 7; matchIdx++) {
				int homeIdx = matchIdx;
				int awayIdx = 7 + ((matchIdx + mdIndex) % 7);
				var homeTeam = teams.get(homeIdx);
				var awayTeam = teams.get(awayIdx);
				var match = matchRepository.save(new Match(md, homeTeam, awayTeam));
				seedRace(md, match, raceScoring, teamDrivers.get(homeTeam), teamDrivers.get(awayTeam),
						homePositions[mdIndex], homePositions[mdIndex], awayPositions[mdIndex], awayPositions[mdIndex],
						fastestLapPositions[mdIndex]);
			}
		}
	}

	private void seedSwissSeason(Season season, java.util.List<Team> teams,
	                             java.util.Map<Team, Driver[]> teamDrivers,
	                             int[][] homePositions, int[][] awayPositions, int[] fastestLapPositions,
	                             RaceScoring raceScoring) {
		// 5 matchdays, 5 matches per MD (10 teams), 2 races per match
		for (int mdIndex = 0; mdIndex < 5; mdIndex++) {
			var md = matchdayRepository.save(new Matchday(season, "Matchday " + (mdIndex + 1), mdIndex + 1));
			for (int matchIdx = 0; matchIdx < 5; matchIdx++) {
				int homeIdx = matchIdx;
				int awayIdx = 5 + ((matchIdx + mdIndex) % 5);
				var homeTeam = teams.get(homeIdx);
				var awayTeam = teams.get(awayIdx);
				var match = matchRepository.save(new Match(md, homeTeam, awayTeam));
				// Race 1
				seedRace(md, match, raceScoring, teamDrivers.get(homeTeam), teamDrivers.get(awayTeam),
						homePositions[mdIndex], homePositions[mdIndex], awayPositions[mdIndex], awayPositions[mdIndex],
						fastestLapPositions[mdIndex]);
				// Race 2 (reversed positions for variety)
				int nextMd = (mdIndex + 1) % 5;
				seedRace(md, match, raceScoring, teamDrivers.get(homeTeam), teamDrivers.get(awayTeam),
						homePositions[nextMd], homePositions[nextMd], awayPositions[nextMd], awayPositions[nextMd],
						fastestLapPositions[nextMd]);
			}
		}
	}

	private void seedRoundRobinSeason(Season season, java.util.List<Team> teams,
	                                  java.util.Map<Team, Driver[]> teamDrivers,
	                                  int[][] homePositions, int[][] awayPositions, int[] fastestLapPositions,
	                                  RaceScoring raceScoring) {
		// 3 matchdays, 3 matches per MD (6 teams), 2 races per match
		for (int mdIndex = 0; mdIndex < 3; mdIndex++) {
			var md = matchdayRepository.save(new Matchday(season, "Matchday " + (mdIndex + 1), mdIndex + 1));
			// Round-robin pairing: pair team[i] with team[5-i], rotating each matchday
			for (int matchIdx = 0; matchIdx < 3; matchIdx++) {
				int homeIdx = matchIdx;
				int awayIdx = 3 + ((matchIdx + mdIndex) % 3);
				var homeTeam = teams.get(homeIdx);
				var awayTeam = teams.get(awayIdx);
				var match = matchRepository.save(new Match(md, homeTeam, awayTeam));
				// Race 1
				seedRace(md, match, raceScoring, teamDrivers.get(homeTeam), teamDrivers.get(awayTeam),
						homePositions[mdIndex], homePositions[mdIndex], awayPositions[mdIndex], awayPositions[mdIndex],
						fastestLapPositions[mdIndex]);
				// Race 2 (reversed positions)
				int nextMd = (mdIndex + 1) % 3;
				seedRace(md, match, raceScoring, teamDrivers.get(homeTeam), teamDrivers.get(awayTeam),
						homePositions[nextMd], homePositions[nextMd], awayPositions[nextMd], awayPositions[nextMd],
						fastestLapPositions[nextMd]);
			}
		}
	}

	private void seedRace(Matchday md, Match match, RaceScoring raceScoring,
	                      Driver[] homeDrivers, Driver[] awayDrivers,
	                      int[] homeRacePositions, int[] homeQualiPositions,
	                      int[] awayRacePositions, int[] awayQualiPositions,
	                      int fastestLapPosition) {
		var race = new Race();
		race.setMatchday(md);
		race.setMatch(match);
		raceRepository.save(race);
		race.setSettings(createTestSettings(race));
		raceRepository.save(race);

		// Save lineups first (Source of Truth per CLAUDE.md)
		for (var d : homeDrivers) raceLineupRepository.save(new RaceLineup(race, d, match.getHomeTeam()));
		for (var d : awayDrivers) raceLineupRepository.save(new RaceLineup(race, d, match.getAwayTeam()));

		// Save results and calculate points
		var results = new java.util.ArrayList<RaceResult>();
		for (int i = 0; i < homeDrivers.length; i++) {
			boolean fl = (homeRacePositions[i] == fastestLapPosition);
			results.add(new RaceResult(race, homeDrivers[i], homeRacePositions[i], homeQualiPositions[i], fl));
		}
		for (int i = 0; i < awayDrivers.length; i++) {
			boolean fl = (awayRacePositions[i] == fastestLapPosition);
			results.add(new RaceResult(race, awayDrivers[i], awayRacePositions[i], awayQualiPositions[i], fl));
		}
		raceResultRepository.saveAll(results);
		scoringService.calculatePoints(results, raceScoring);
		raceResultRepository.saveAll(results);
		raceResultRepository.flush();

		// Detach and reload race to ensure results collection is populated for aggregation (Pitfall 2)
		entityManager.detach(race);
		var reloadedRace = raceRepository.findById(race.getId()).orElseThrow();
		scoringService.aggregateMatchScores(reloadedRace);
		matchRepository.save(reloadedRace.getMatch());
	}

	private void seedRaceLineups() {
		var scorings = new ScoringDefaults(
				raceScoringRepository.findAll().getFirst(),
				matchScoringRepository.findAll().getFirst());

		// === Completely isolated test data (no relation to seed teams/drivers) ===

		// Test-Teams
		var testAlpha = teamRepository.save(new Team("Test Alpha Racing", "T-ALF"));
		var testBravo = teamRepository.save(new Team("Test Bravo Racing", "T-BRV"));
		var testBravo1 = teamRepository.save(new Team("Test Bravo Racing 1", "T-BRV 1", testBravo));
		var testBravo2 = teamRepository.save(new Team("Test Bravo Racing 2", "T-BRV 2", testBravo));

		// Test-Drivers
		var tda1 = driver("Test_Alpha_1", "Test Alpha Driver 1");
		var tda2 = driver("Test_Alpha_2", "Test Alpha Driver 2");
		var tdb1 = driver("Test_Bravo1_1", "Test Bravo1 Driver 1");
		var tdb2 = driver("Test_Bravo1_2", "Test Bravo1 Driver 2");
		var tdb3 = driver("Test_Bravo2_1", "Test Bravo2 Driver 1");
		var tdb4 = driver("Test_Bravo2_2", "Test Bravo2 Driver 2");

		// Test-Season 2026: T-ALF vs T-BRV 1, T-ALF vs T-BRV 2
		var testSeason1 = createSeason("Test-Season 2026", 2026, 99, "Test", scorings);
		List.of(testAlpha, testBravo, testBravo1, testBravo2).forEach(testSeason1::addTeam);
		seasonRepository.save(testSeason1);

		var md1 = matchdayRepository.save(new Matchday(testSeason1, "Test MD 1", 1));

		var match1 = new Match();
		match1.setMatchday(md1);
		match1.setHomeTeam(testAlpha);
		match1.setAwayTeam(testBravo1);
		matchRepository.save(match1);
		var race1 = new Race();
		race1.setMatchday(md1);
		race1.setMatch(match1);
		race1.setSettings(createTestSettings(race1));
		raceRepository.save(race1);
		raceLineupRepository.save(new RaceLineup(race1, tda1, testAlpha));
		raceLineupRepository.save(new RaceLineup(race1, tda2, testAlpha));
		raceLineupRepository.save(new RaceLineup(race1, tdb1, testBravo1));
		raceLineupRepository.save(new RaceLineup(race1, tdb2, testBravo1));

		var match2 = new Match();
		match2.setMatchday(md1);
		match2.setHomeTeam(testAlpha);
		match2.setAwayTeam(testBravo2);
		matchRepository.save(match2);
		var race2 = new Race();
		race2.setMatchday(md1);
		race2.setMatch(match2);
		raceRepository.save(race2);
		raceLineupRepository.save(new RaceLineup(race2, tda1, testAlpha));
		raceLineupRepository.save(new RaceLineup(race2, tda2, testAlpha));
		raceLineupRepository.save(new RaceLineup(race2, tdb3, testBravo2));
		raceLineupRepository.save(new RaceLineup(race2, tdb4, testBravo2));

		// Test-Season 2025: T-ALF vs T-BRV (multi-season test)
		var testSeason2 = createSeason("Test-Season 2025", 2025, 98, "Test", scorings);
		List.of(testAlpha, testBravo).forEach(testSeason2::addTeam);
		seasonRepository.save(testSeason2);

		var md2 = matchdayRepository.save(new Matchday(testSeason2, "Test MD 1", 1));
		var match3 = new Match();
		match3.setMatchday(md2);
		match3.setHomeTeam(testAlpha);
		match3.setAwayTeam(testBravo);
		matchRepository.save(match3);
		var race3 = new Race();
		race3.setMatchday(md2);
		race3.setMatch(match3);
		raceRepository.save(race3);
		raceLineupRepository.save(new RaceLineup(race3, tda1, testAlpha));
		raceLineupRepository.save(new RaceLineup(race3, tda2, testAlpha));

		log.info("Created test data: {} test-teams, {} test-drivers, {} races, {} lineups",
				4, 6, raceRepository.count(), raceLineupRepository.count());
	}

	private void seedPlayoffs() {
		var allSeasons = seasonRepository.findAll();
		var allTeams = teamRepository.findAll();
		var raceScoring = raceScoringRepository.findAll().getFirst();

		// Helper lambdas
		java.util.function.Function<String, Team> findParent = shortName ->
				allTeams.stream()
						.filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() == null)
						.findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));

		java.util.function.Function<String, Team> findSub = shortName ->
				allTeams.stream()
						.filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() != null)
						.findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));

		// Get seasons
		var s1a = allSeasons.stream().filter(s -> s.getYear() == 2023 && s.getName().equals("Group A")).findFirst().orElseThrow();
		var s1b = allSeasons.stream().filter(s -> s.getYear() == 2023 && s.getName().equals("Group B")).findFirst().orElseThrow();
		var s2 = allSeasons.stream().filter(s -> s.getYear() == 2024 && s.getNumber() == 2).findFirst().orElseThrow();

		// === 2023 PLAYOFFS: SEMIFINAL (4 teams) ===
		var s1aResults = raceResultRepository.findByRaceMatchdaySeasonId(s1a.getId());
		var s1bResults = raceResultRepository.findByRaceMatchdaySeasonId(s1b.getId());

		// Calculate Group A standings
		var s1aTeamScores = new java.util.LinkedHashMap<Team, Integer>();
		for (var result : s1aResults) {
			var team = result.getRace().getMatch().getHomeTeam();
			if (team != null) {
				var homeTeamId = team.getId();
				var teamResults = s1aResults.stream()
						.filter(r -> r.getRace().getMatch() != null && r.getRace().getMatch().getHomeTeam() != null && r.getRace().getMatch().getHomeTeam().getId().equals(homeTeamId))
						.toList();
				s1aTeamScores.putIfAbsent(team, scoringService.calculateTeamTotal(teamResults));

				var awayTeam = result.getRace().getMatch().getAwayTeam();
				if (awayTeam != null) {
					var awayTeamId = awayTeam.getId();
					var awayTeamResults = s1aResults.stream()
							.filter(r -> r.getRace().getMatch() != null && r.getRace().getMatch().getAwayTeam() != null && r.getRace().getMatch().getAwayTeam().getId().equals(awayTeamId))
							.toList();
					s1aTeamScores.putIfAbsent(awayTeam, scoringService.calculateTeamTotal(awayTeamResults));
				}
			}
		}

		// Calculate Group B standings
		var s1bTeamScores = new java.util.LinkedHashMap<Team, Integer>();
		for (var result : s1bResults) {
			var team = result.getRace().getMatch().getHomeTeam();
			if (team != null) {
				var homeTeamId = team.getId();
				var teamResults = s1bResults.stream()
						.filter(r -> r.getRace().getMatch() != null && r.getRace().getMatch().getHomeTeam() != null && r.getRace().getMatch().getHomeTeam().getId().equals(homeTeamId))
						.toList();
				s1bTeamScores.putIfAbsent(team, scoringService.calculateTeamTotal(teamResults));

				var awayTeam = result.getRace().getMatch().getAwayTeam();
				if (awayTeam != null) {
					var awayTeamId = awayTeam.getId();
					var awayTeamResults = s1bResults.stream()
							.filter(r -> r.getRace().getMatch() != null && r.getRace().getMatch().getAwayTeam() != null && r.getRace().getMatch().getAwayTeam().getId().equals(awayTeamId))
							.toList();
					s1bTeamScores.putIfAbsent(awayTeam, scoringService.calculateTeamTotal(awayTeamResults));
				}
			}
		}

		// Get top 2 from each group
		var s1aSorted = s1aTeamScores.entrySet().stream()
				.sorted((a, b) -> b.getValue().compareTo(a.getValue()))
				.map(java.util.Map.Entry::getKey)
				.toList();
		var s1bSorted = s1bTeamScores.entrySet().stream()
				.sorted((a, b) -> b.getValue().compareTo(a.getValue()))
				.map(java.util.Map.Entry::getKey)
				.toList();

		if (s1aSorted.size() < 2 || s1bSorted.size() < 2) {
			log.warn("Not enough teams in playoff groups to create 2023 playoff");
		} else {
			var winnerA = s1aSorted.get(0);
			var runnerUpA = s1aSorted.get(1);
			var winnerB = s1bSorted.get(0);
			var runnerUpB = s1bSorted.get(1);

			// Create 2023 Playoff
			var playoff2023 = playoffService.createPlayoff(s1a.getId(), "2023 Playoffs", 4);
			// Link both groups as team sources (for UI transparency)
			playoff2023.getSeasons().add(s1b);
			playoffRepository.save(playoff2023);

			var semifinal = playoff2023.getRounds().getFirst();
			semifinal.setBestOfLegs(2);
			playoffRoundRepository.save(semifinal);

			// Wire matchups
			var matchup0 = semifinal.getMatchups().get(0);
			matchup0.setTeam1(winnerA);
			matchup0.setTeam2(runnerUpB);
			playoffMatchupRepository.save(matchup0);

			var matchup1 = semifinal.getMatchups().get(1);
			matchup1.setTeam1(winnerB);
			matchup1.setTeam2(runnerUpA);
			playoffMatchupRepository.save(matchup1);

			// Create matchday for playoff races
			var playoffMatchday = matchdayRepository.save(new Matchday(s1a, "2023 Playoffs", 4));

			// Create races for Semifinal (2 per matchup, 4 total)
			createPlayoffRaces(playoffMatchday, matchup0, s1a, raceScoring, 2);
			createPlayoffRaces(playoffMatchday, matchup1, s1a, raceScoring, 2);

			log.info("Created 2023 Playoffs: {} vs {}, {} vs {}",
					winnerA.getShortName(), runnerUpB.getShortName(),
					winnerB.getShortName(), runnerUpA.getShortName());
		}

		// === 2024 PLAYOFFS: FINAL (2 teams) ===
		var s2Results = raceResultRepository.findByRaceMatchdaySeasonId(s2.getId());

		// Calculate 2024 standings
		var s2TeamScores = new java.util.LinkedHashMap<Team, Integer>();
		for (var result : s2Results) {
			var team = result.getRace().getMatch().getHomeTeam();
			if (team != null) {
				var homeTeamId = team.getId();
				var teamResults = s2Results.stream()
						.filter(r -> r.getRace().getMatch() != null && r.getRace().getMatch().getHomeTeam() != null && r.getRace().getMatch().getHomeTeam().getId().equals(homeTeamId))
						.toList();
				s2TeamScores.putIfAbsent(team, scoringService.calculateTeamTotal(teamResults));

				var awayTeam = result.getRace().getMatch().getAwayTeam();
				if (awayTeam != null) {
					var awayTeamId = awayTeam.getId();
					var awayTeamResults = s2Results.stream()
							.filter(r -> r.getRace().getMatch() != null && r.getRace().getMatch().getAwayTeam() != null && r.getRace().getMatch().getAwayTeam().getId().equals(awayTeamId))
							.toList();
					s2TeamScores.putIfAbsent(awayTeam, scoringService.calculateTeamTotal(awayTeamResults));
				}
			}
		}

		var s2Sorted = s2TeamScores.entrySet().stream()
				.sorted((a, b) -> b.getValue().compareTo(a.getValue()))
				.map(java.util.Map.Entry::getKey)
				.toList();

		if (s2Sorted.size() < 2) {
			log.warn("Not enough teams in 2024 season to create playoff");
		} else {
			var topTeam = s2Sorted.get(0);
			var runnerUpTeam = s2Sorted.get(1);

			// Create 2024 Playoff
			var playoff2024 = playoffService.createPlayoff(s2.getId(), "2024 Playoffs", 2);
			var finalRound = playoff2024.getRounds().getFirst();
			finalRound.setBestOfLegs(2);
			playoffRoundRepository.save(finalRound);

			// Wire matchup
			var matchup = finalRound.getMatchups().getFirst();
			matchup.setTeam1(topTeam);
			matchup.setTeam2(runnerUpTeam);
			playoffMatchupRepository.save(matchup);

			// Create matchday for playoff races
			var playoffMatchday = matchdayRepository.save(new Matchday(s2, "2024 Playoffs", 5));

			// Create races for Final (2 total)
			createPlayoffRaces(playoffMatchday, matchup, s2, raceScoring, 2);

			log.info("Created 2024 Playoffs: {} vs {}",
					topTeam.getShortName(), runnerUpTeam.getShortName());
		}

		log.info("Seeded playoffs: {} playoff entities, {} playoff races",
				playoffRepository.count(), raceRepository.count());
	}

	private void createPlayoffRaces(Matchday matchday, PlayoffMatchup matchup, Season season, RaceScoring raceScoring, int numRaces) {
		var allSeasonDrivers = seasonDriverRepository.findBySeasonId(season.getId());

		// Group drivers by team
		var team1Drivers = allSeasonDrivers.stream()
				.filter(sd -> sd.getTeam().getId().equals(matchup.getTeam1().getId()))
				.map(SeasonDriver::getDriver)
				.limit(6)
				.toList();
		var team2Drivers = allSeasonDrivers.stream()
				.filter(sd -> sd.getTeam().getId().equals(matchup.getTeam2().getId()))
				.map(SeasonDriver::getDriver)
				.limit(6)
				.toList();

		for (int i = 0; i < numRaces; i++) {
			var race = new Race();
			race.setMatchday(matchday);
			race.setPlayoffMatchup(matchup);
			race.setSettings(createTestSettings(race));
			raceRepository.save(race);

			// Add lineups for team 1
			for (var driver : team1Drivers) {
				raceLineupRepository.save(new RaceLineup(race, driver, matchup.getTeam1()));
			}

			// Add lineups for team 2
			for (var driver : team2Drivers) {
				raceLineupRepository.save(new RaceLineup(race, driver, matchup.getTeam2()));
			}
		}
	}

	private Driver driver(String psnId, String nickname) {
		return driverRepository.save(new Driver(psnId, nickname));
	}

	private RaceSettings createTestSettings(Race race) {
		var settings = new RaceSettings(race);
		settings.setNumberOfLaps(20);
		settings.setTyreWearMultiplier(3);
		settings.setFuelConsumptionMultiplier(3);
		settings.setRefuelingSpeed(10);
		settings.setInitialFuel("90");
		settings.setNumberOfRequiredPitStops(0);
		settings.setTimeProgressionMultiplier(5);
		settings.setWeather("Preset S02");
		settings.setTimeOfDay("Afternoon");
		settings.setAvailableTyres("RS, RM, RH, I, W");
		settings.setMandatoryTyres("RS, RM, RH");
		return settings;
	}

	private void generateTeamCards() {
		try {
			var seasons = seasonRepository.findAll();
			for (var season : seasons) {
				log.info("Generating team cards for season: {}", season.getName());
				var paths = teamCardService.generateAllCards(season);
				log.info("Generated {} team cards for season {}", paths.size(), season.getName());
			}
		} catch (IOException e) {
			log.error("Failed to generate team cards", e);
		}
	}

	private record ScoringDefaults(RaceScoring raceScoring, MatchScoring matchScoring) {
	}
}
