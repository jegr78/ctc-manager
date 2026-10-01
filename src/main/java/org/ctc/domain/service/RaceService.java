package org.ctc.domain.service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.service.TeamCardService;
import org.ctc.discord.event.MatchScheduleFieldsChangedEvent;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaceService {

	static final String NO_RACE_SCORING = "This phase has no race scoring. Set one on the phase before entering results.";

	private final RaceRepository raceRepository;
	private final MatchRepository matchRepository;
	private final MatchdayRepository matchdayRepository;
	private final SeasonRepository seasonRepository;
	private final TeamRepository teamRepository;
	private final DriverRepository driverRepository;
	private final SeasonDriverRepository seasonDriverRepository;
	private final RaceLineupRepository raceLineupRepository;
	private final CarRepository carRepository;
	private final TrackRepository trackRepository;
	private final SeasonTeamRepository seasonTeamRepository;
	private final ScoringService scoringService;
	private final TeamCardService teamCardService;
	private final RaceCalendarService raceCalendarService;
	private final ApplicationEventPublisher eventPublisher;

	public RaceListData getRaceListData(UUID matchdayId, UUID seasonId) {
		List<Race> races;
		Matchday matchday = null;
		UUID selectedSeasonId = null;

		if (matchdayId != null) {
			races = raceRepository.findByMatchdayId(matchdayId);
			matchday = matchdayRepository.findById(matchdayId).orElse(null);
		} else if (seasonId != null) {
			races = raceRepository.findByMatchdaySeasonId(seasonId);
			selectedSeasonId = seasonId;
		} else {
			races = raceRepository.findAll();
		}

		var raceScores = new HashMap<UUID, int[]>();
		for (var race : races) {
			// Prefer scores from RaceResults (individual race scores)
			if (!race.getResults().isEmpty()) {
				int homeScore = 0;
				int awayScore = 0;

				UUID homeTeamId = race.getHomeTeam() != null ? race.getHomeTeam().getId() : null;
				for (var result : race.getResults()) {
					if (homeTeamId != null && scoringService.isDriverInTeam(result, race.getId(), homeTeamId)) {
						homeScore += result.getPointsTotal();
					} else {
						awayScore += result.getPointsTotal();
					}
				}
				raceScores.put(race.getId(), new int[]{homeScore, awayScore});
			} else if (race.getHomeScore() != null && race.getAwayScore() != null) {
				// Fallback to match scores if no RaceResults exist
				raceScores.put(race.getId(), new int[]{race.getHomeScore(), race.getAwayScore()});
			}
		}

		return new RaceListData(races, raceScores, matchday, selectedSeasonId, seasonRepository.findAll());
	}

	public RaceDetailData getRaceDetailData(UUID raceId) {
		var race = raceRepository.findById(raceId).orElseThrow();

		int homeTotal = 0;
		int awayTotal = 0;
		Map<UUID, String> driverTeamMap = null;
		Map<UUID, Boolean> guestDriverMap = null;

		if (!race.getResults().isEmpty() && race.getHomeTeam() != null) {
			homeTotal = race.getResults().stream()
					.filter(r -> scoringService.isDriverInTeam(r, race.getId(), race.getHomeTeam().getId()))
					.mapToInt(RaceResult::getPointsTotal).sum();
			awayTotal = race.getResults().stream()
					.filter(r -> !scoringService.isDriverInTeam(r, race.getId(), race.getHomeTeam().getId()))
					.mapToInt(RaceResult::getPointsTotal).sum();

			var sid = race.getMatchday().getSeason().getId();
			driverTeamMap = new HashMap<>();
			guestDriverMap = new HashMap<>();
			for (var result : race.getResults()) {
				var lineup = raceLineupRepository.findByRaceIdAndDriverId(race.getId(), result.getDriver().getId());
				var teamName = lineup
						.map(rl -> rl.getTeam().getShortName())
						.orElseGet(() -> result.getDriver().getSeasonDrivers().stream()
								.filter(sd -> sd.getSeason().getId().equals(sid))
								.map(sd -> sd.getTeam().getShortName())
								.findFirst().orElse("?"));
				driverTeamMap.put(result.getDriver().getId(), teamName);
				guestDriverMap.put(result.getDriver().getId(), lineup.map(RaceLineup::isGuest).orElse(false));
			}
		}

		// Check if lineup graphic can be generated
		var lineups = raceLineupRepository.findByRaceId(race.getId());
		boolean hasLineup = !lineups.isEmpty();
		boolean hasHomeCard = false;
		boolean hasAwayCard = false;
		if (race.getMatch() != null && race.getHomeTeam() != null && race.getAwayTeam() != null) {
			var season = race.getMatchday().getSeason();
			hasHomeCard = seasonTeamRepository.findBySeasonIdAndTeamId(season.getId(), race.getHomeTeam().getId())
					.map(st -> teamCardService.cardExists(st)).orElse(false);
			hasAwayCard = seasonTeamRepository.findBySeasonIdAndTeamId(season.getId(), race.getAwayTeam().getId())
					.map(st -> teamCardService.cardExists(st)).orElse(false);
		}
		boolean lineupExists = race.getAttachments().stream()
				.anyMatch(a -> a.getType() == AttachmentType.FILE && a.getUrl().endsWith("/lineup.png"));
		boolean resultsGraphicExists = race.getAttachments().stream()
				.anyMatch(a -> a.getType() == AttachmentType.FILE && a.getUrl().endsWith("/results.png"));
		boolean provisionalGraphicExists = race.getAttachments().stream()
				.anyMatch(a -> a.getType() == AttachmentType.FILE && a.getUrl().endsWith("/provisional.png"));
		boolean hasResults = !race.getResults().isEmpty();

		boolean settingsGraphicExists = race.getAttachments().stream()
				.anyMatch(a -> a.getType() == AttachmentType.FILE && a.getUrl().endsWith("/settings.png"));
		boolean hasAllSettings = race.hasAllSettings() && race.getCar() != null && race.getTrack() != null;
		boolean lobbySettingsGraphicExists = race.getAttachments().stream()
				.anyMatch(a -> a.getType() == AttachmentType.FILE && a.getUrl().endsWith("/lobby-settings.png"));
		boolean lobbySettingsReady = race.hasAllSettings() && race.getTrack() != null;
		boolean lobbyTeamsPresent = race.getHomeTeam() != null && race.getAwayTeam() != null;

		boolean overlayExists = race.getAttachments().stream()
				.anyMatch(a -> a.getType() == AttachmentType.FILE && a.getUrl().endsWith("/overlay.png"));
		boolean hasMatch = race.getMatch() != null && race.getHomeTeam() != null && race.getAwayTeam() != null;

		boolean calendarAvailable = raceCalendarService.isCalendarAvailable();
		boolean hasCalendarEvent = race.hasCalendarEvent();
		boolean canCreateCalendarEvent = calendarAvailable
				&& race.getDateTime() != null
				&& race.getHomeTeam() != null
				&& race.getAwayTeam() != null;

		return new RaceDetailData(race, homeTotal, awayTotal, driverTeamMap, guestDriverMap,
				hasLineup && hasHomeCard && hasAwayCard && !lineupExists,
				!hasLineup, !hasHomeCard || !hasAwayCard, lineupExists,
				hasResults && hasHomeCard && hasAwayCard && !resultsGraphicExists,
				!hasResults, resultsGraphicExists,
				hasResults && hasHomeCard && hasAwayCard && !provisionalGraphicExists, provisionalGraphicExists,
				hasAllSettings && hasHomeCard && hasAwayCard && !settingsGraphicExists,
				!hasAllSettings, settingsGraphicExists,
				hasMatch && !overlayExists, overlayExists,
				calendarAvailable, hasCalendarEvent, canCreateCalendarEvent,
				lobbySettingsReady && lobbyTeamsPresent && !lobbySettingsGraphicExists,
				!lobbySettingsReady, lobbySettingsGraphicExists);
	}

	@Transactional
	public SaveResult saveRace(UUID id, UUID matchdayId, UUID homeTeamId, UUID awayTeamId,
	                           UUID trackId, UUID carId, LocalDateTime dateTime,
	                           Integer numberOfLaps, Integer tyreWearMultiplier,
	                           Integer fuelConsumptionMultiplier, Integer refuelingSpeed,
	                           String initialFuel, Integer numberOfRequiredPitStops,
	                           Integer timeProgressionMultiplier, String weather,
	                           String timeOfDay, String availableTyres, String mandatoryTyres) {
		var matchday = matchdayRepository.findById(matchdayId).orElseThrow();
		var homeTeam = teamRepository.findById(homeTeamId).orElseThrow();
		var awayTeam = teamRepository.findById(awayTeamId).orElseThrow();

		Race race = id != null ? raceRepository.findById(id).orElseThrow() : new Race();
		var track = trackId != null ? trackRepository.findById(trackId).orElse(null) : null;
		var car = carId != null ? carRepository.findById(carId).orElse(null) : null;

		var rejection = validatePairingChange(race, matchday, homeTeam, awayTeam);
		if (rejection == null) {
			rejection = validateUniquePairing(race, homeTeam, awayTeam);
		}
		if (rejection == null) {
			rejection = validateCarAndTrack(matchday.getSeason(), homeTeam, car, track, id);
		}
		if (rejection != null) {
			return new SaveResult(false, rejection, id, matchdayId);
		}

		race.setMatchday(matchday);
		if (race.getPlayoffMatchup() == null) {
			applyPairing(race, matchday, homeTeam, awayTeam);
		}

		race.setTrack(track);
		race.setCar(car);
		boolean dateTimeChanged = id != null && !Objects.equals(race.getDateTime(), dateTime);
		race.setDateTime(dateTime);

		var settings = race.getSettings();
		if (settings == null) {
			settings = new RaceSettings(race);
			race.setSettings(settings);
		}
		settings.setNumberOfLaps(numberOfLaps);
		settings.setTyreWearMultiplier(tyreWearMultiplier);
		settings.setFuelConsumptionMultiplier(fuelConsumptionMultiplier);
		settings.setRefuelingSpeed(refuelingSpeed);
		settings.setInitialFuel(initialFuel);
		settings.setNumberOfRequiredPitStops(numberOfRequiredPitStops);
		settings.setTimeProgressionMultiplier(timeProgressionMultiplier);
		settings.setWeather(weather);
		settings.setTimeOfDay(timeOfDay);
		settings.setAvailableTyres(availableTyres);
		settings.setMandatoryTyres(mandatoryTyres);

		raceRepository.save(race);
		if (dateTimeChanged && race.getMatch() != null) {
			eventPublisher.publishEvent(new MatchScheduleFieldsChangedEvent(race.getMatch().getId()));
		}
		log.info("Saved race: {} vs {} ({})", homeTeam.getShortName(), awayTeam.getShortName(), matchday.getLabel());
		return new SaveResult(true,
				"Race saved: " + homeTeam.getShortName() + " vs " + awayTeam.getShortName(),
				race.getId(), matchdayId);
	}

	@Transactional
	public String saveResults(UUID raceId, List<RaceResultData> results) {
		var race = raceRepository.findById(raceId).orElseThrow();
		PlayoffDecisionGuard.requireOpen(race);
		if (race.getMatchday().getPhase().getRaceScoring() == null) {
			throw new BusinessRuleException(NO_RACE_SCORING);
		}

		race.getResults().clear();
		raceRepository.saveAndFlush(race);

		for (var rd : results) {
			if (rd.driverId() == null) {
				continue;
			}

			var driver = driverRepository.findById(rd.driverId()).orElseThrow();
			var result = new RaceResult(race, driver, rd.position(), rd.qualiPosition(), rd.fastestLap());
			scoringService.calculatePoints(result, race.getMatchday().getPhase().getRaceScoring());
			race.getResults().add(result);
		}

		raceRepository.save(race);
		if (race.getResults().isEmpty()) {
			scoringService.recomputeMatchScoresFromAllLegs(race);
		} else {
			scoringService.aggregateMatchScores(race);
		}

		var homeScore = race.getHomeScore() != null ? race.getHomeScore() : 0;
		var awayScore = race.getAwayScore() != null ? race.getAwayScore() : 0;

		log.info("Saved results for {} vs {}: {} : {}",
				race.getHomeTeam().getShortName(), race.getAwayTeam().getShortName(), homeScore, awayScore);
		return "Results saved: " + race.getHomeTeam().getShortName() + " " + homeScore +
				" : " + awayScore + " " + race.getAwayTeam().getShortName();
	}

	@Transactional
	public String quickScore(UUID raceId, int homeScore, int awayScore) {
		var race = raceRepository.findById(raceId).orElseThrow();
		if (race.getMatch() != null && race.getMatch().getWalkoverTeam() != null) {
			throw new BusinessRuleException("A walkover match has no score");
		}
		if (race.getMatch() != null) {
			race.getMatch().setHomeScore(race.hasTeamOverrides() ? awayScore : homeScore);
			race.getMatch().setAwayScore(race.hasTeamOverrides() ? homeScore : awayScore);
			raceRepository.save(race);
			scoringService.aggregateMatchScores(race);
		}
		log.info("Quick score: {} {} : {} {}",
				race.getHomeTeam().getShortName(), homeScore, awayScore, race.getAwayTeam().getShortName());
		return race.getHomeTeam().getShortName() + " " + homeScore + " : " + awayScore + " " + race.getAwayTeam().getShortName();
	}

	@Transactional
	public UUID deleteRace(UUID raceId) {
		var race = raceRepository.findById(raceId).orElseThrow();
		PlayoffDecisionGuard.requireOpen(race);
		var matchdayId = race.getMatchday().getId();
		var match = race.getMatch();
		var matchup = race.getPlayoffMatchup();
		boolean hadResults = !race.getResults().isEmpty();
		log.info("Deleting race: {} vs {}", race.getHomeTeam().getShortName(), race.getAwayTeam().getShortName());
		raceLineupRepository.deleteAll(raceLineupRepository.findByRaceId(raceId));
		if (match != null) {
			match.getRaces().remove(race);
		}
		if (matchup != null) {
			matchup.getRaces().remove(race);
		}
		raceRepository.delete(race);
		raceRepository.flush();
		if (hadResults && match != null) {
			scoringService.recomputeMatchScores(match);
		}
		if (hadResults && matchup != null) {
			scoringService.recomputePlayoffMatchupScores(matchup);
		}
		return matchdayId;
	}

	/**
	 * Rejects a change of the race's effective teams or phase for playoff races, and a change of
	 * teams, matchday or phase once any leg of the pairing has results; changes nothing.
	 */
	private String validatePairingChange(Race race, Matchday matchday, Team homeTeam, Team awayTeam) {
		if (race.getId() == null) {
			return null;
		}
		boolean teamsChanged = !sameTeam(race.getHomeTeam(), homeTeam) || !sameTeam(race.getAwayTeam(), awayTeam);
		boolean phaseChanged = !race.getMatchday().getPhase().getId().equals(matchday.getPhase().getId());
		boolean matchdayChanged = !race.getMatchday().getId().equals(matchday.getId());
		if (!teamsChanged && !phaseChanged && !matchdayChanged) {
			return null;
		}
		if (race.getPlayoffMatchup() != null && (teamsChanged || phaseChanged)) {
			return "The teams and phase of a playoff race come from its playoff matchup";
		}
		var legs = race.getMatch() != null ? raceRepository.findByMatchId(race.getMatch().getId()) : List.of(race);
		if (legs.stream().anyMatch(leg -> !leg.getResults().isEmpty())) {
			return "Teams, matchday and phase cannot change after results were entered";
		}
		return null;
	}

	/**
	 * Rejects moving a race's match onto a pairing that another match of its matchday already holds,
	 * in either orientation; changes nothing. A new race joins such a match instead.
	 */
	private String validateUniquePairing(Race race, Team homeTeam, Team awayTeam) {
		Match own = race.getMatch();
		if (race.getPlayoffMatchup() != null || own == null) {
			return null;
		}
		for (Team[] pair : new Team[][] {{homeTeam, awayTeam}, {awayTeam, homeTeam}}) {
			var other = matchRepository.findFirstByMatchdayIdAndHomeTeamIdAndAwayTeamId(own.getMatchday().getId(),
					pair[0].getId(), pair[1].getId());
			if (other.isPresent() && !other.get().getId().equals(own.getId())) {
				return "Match already exists: " + pair[0].getShortName() + " vs " + pair[1].getShortName();
			}
		}
		return null;
	}

	/**
	 * Maps the submitted effective teams onto the shared Match through this leg's orientation and
	 * keeps every reversed leg of the match reversed.
	 */
	private void applyPairing(Race race, Matchday matchday, Team homeTeam, Team awayTeam) {
		Match match = race.getMatch();
		if (match == null) {
			joinOrCreateMatch(race, matchday, homeTeam, awayTeam);
			return;
		}
		if (sameTeam(race.getHomeTeam(), homeTeam) && sameTeam(race.getAwayTeam(), awayTeam)) {
			return;
		}
		boolean reversedLeg = race.hasTeamOverrides();
		match.setHomeTeam(reversedLeg ? awayTeam : homeTeam);
		match.setAwayTeam(reversedLeg ? homeTeam : awayTeam);
		for (var leg : raceRepository.findByMatchId(match.getId())) {
			if (leg.hasTeamOverrides()) {
				leg.setHomeTeamOverride(match.getAwayTeam());
				leg.setAwayTeamOverride(match.getHomeTeam());
			}
		}
	}

	/** A new race becomes a further leg of the matchday's match of the two teams, reversed when the teams are. */
	private void joinOrCreateMatch(Race race, Matchday matchday, Team homeTeam, Team awayTeam) {
		var same = matchRepository.findFirstByMatchdayIdAndHomeTeamIdAndAwayTeamId(matchday.getId(),
				homeTeam.getId(), awayTeam.getId());
		if (same.isPresent()) {
			race.setMatch(same.get());
			return;
		}
		var reversed = matchRepository.findFirstByMatchdayIdAndHomeTeamIdAndAwayTeamId(matchday.getId(),
				awayTeam.getId(), homeTeam.getId());
		if (reversed.isPresent()) {
			race.setMatch(reversed.get());
			race.setHomeTeamOverride(homeTeam);
			race.setAwayTeamOverride(awayTeam);
			return;
		}
		race.setMatch(matchRepository.save(new Match(matchday, homeTeam, awayTeam)));
	}

	private static boolean sameTeam(Team a, Team b) {
		return a != null && b != null && a.getId().equals(b.getId());
	}

	/** Returns the rejection message, or {@code null} when car and track may be used; changes nothing. */
	private String validateCarAndTrack(Season season, Team homeTeam, Car car, Track track, UUID raceId) {
		if (car != null && !season.getCars().contains(car)) {
			return "Car is not in this season's pool";
		}
		if (track != null && !season.getTracks().contains(track)) {
			return "Track is not in this season's pool";
		}
		if (car != null && getUsedCarIds(season.getId(), homeTeam.getId(), raceId).contains(car.getId())) {
			return homeTeam.getShortName() + " has already used " + car.getDisplayName() + " this season";
		}
		if (track != null && getUsedTrackIds(season.getId(), homeTeam.getId(), raceId).contains(track.getId())) {
			return homeTeam.getShortName() + " has already used " + track.getName() + " this season";
		}
		return null;
	}

	private Set<UUID> getUsedCarIds(UUID seasonId, UUID homeTeamId, UUID excludeRaceId) {
		return raceRepository.findByMatchdaySeasonId(seasonId).stream()
				.filter(r -> !r.isBye())
				.filter(r -> r.getHomeTeam().getId().equals(homeTeamId))
				.filter(r -> !r.getId().equals(excludeRaceId))
				.filter(r -> r.getCar() != null)
				.map(r -> r.getCar().getId())
				.collect(Collectors.toSet());
	}

	private Set<UUID> getUsedTrackIds(UUID seasonId, UUID homeTeamId, UUID excludeRaceId) {
		return raceRepository.findByMatchdaySeasonId(seasonId).stream()
				.filter(r -> !r.isBye())
				.filter(r -> r.getHomeTeam().getId().equals(homeTeamId))
				.filter(r -> !r.getId().equals(excludeRaceId))
				.filter(r -> r.getTrack() != null)
				.map(r -> r.getTrack().getId())
				.collect(Collectors.toSet());
	}

	public record RaceData(UUID id, UUID matchdayId, UUID homeTeamId, UUID awayTeamId,
	                       UUID trackId, UUID carId, LocalDateTime dateTime,
	                       List<RaceResultData> results,
	                       Integer numberOfLaps, Integer tyreWearMultiplier,
	                       Integer fuelConsumptionMultiplier, Integer refuelingSpeed,
	                       String initialFuel, Integer numberOfRequiredPitStops,
	                       Integer timeProgressionMultiplier, String weather,
	                       String timeOfDay, String availableTyres, String mandatoryTyres) {
	}

	public record RaceResultData(UUID driverId, String driverPsnId, String teamShortName,
	                             int position, int qualiPosition, boolean fastestLap) {
	}

	public record RaceListData(List<Race> races, Map<UUID, int[]> raceScores,
	                           Matchday matchday, UUID selectedSeasonId, List<Season> seasons) {
	}

	public record RaceDetailData(Race race, int homeTotal, int awayTotal,
	                             Map<UUID, String> driverTeamMap, Map<UUID, Boolean> guestDriverMap,
	                             boolean canGenerateLineup,
	                             boolean lineupMissing, boolean cardsMissing, boolean lineupExists,
	                             boolean canGenerateResults, boolean resultsMissing, boolean resultsExist,
	                             boolean canGenerateProvisional, boolean provisionalExists,
	                             boolean canGenerateSettings, boolean settingsMissing, boolean settingsExist,
	                             boolean canGenerateOverlay, boolean overlayExists,
	                             boolean calendarAvailable, boolean hasCalendarEvent,
	                             boolean canCreateCalendarEvent,
	                             boolean canGenerateLobbySettings, boolean lobbySettingsMissing,
	                             boolean lobbySettingsExist) {
	}

	public record ResultsFormData(RaceData data, Race race, RaceScoring raceScoring) {
	}

	public record RaceFormData(RaceData data, List<Matchday> matchdays, List<Team> teams,
	                           List<Car> seasonCars, List<Track> seasonTracks,
	                           Set<UUID> usedCarIds, Set<UUID> usedTrackIds) {
	}

	public record SaveResult(boolean success, String message, UUID raceId, UUID matchdayId) {
	}
}
