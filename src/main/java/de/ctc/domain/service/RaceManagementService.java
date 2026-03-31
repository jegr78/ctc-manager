package de.ctc.domain.service;

import de.ctc.admin.dto.RaceForm;
import de.ctc.admin.dto.RaceResultForm;
import de.ctc.admin.service.LineupGraphicService;
import de.ctc.admin.service.TeamCardService;
import de.ctc.domain.model.*;
import de.ctc.domain.model.Car;
import de.ctc.domain.model.Track;
import de.ctc.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaceManagementService {

    private final RaceRepository raceRepository;
    private final MatchRepository matchRepository;
    private final MatchdayRepository matchdayRepository;
    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final DriverRepository driverRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final RaceLineupRepository raceLineupRepository;
    private final RaceAttachmentRepository raceAttachmentRepository;
    private final CarRepository carRepository;
    private final TrackRepository trackRepository;
    private final SeasonTeamRepository seasonTeamRepository;
    private final ScoringService scoringService;
    private final FileStorageService fileStorageService;
    private final LineupGraphicService lineupGraphicService;
    private final TeamCardService teamCardService;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    // --- Return types ---

    public record RaceListData(List<Race> races, Map<UUID, int[]> raceScores,
                               Matchday matchday, UUID selectedSeasonId, List<Season> seasons) {}

    public record RaceDetailData(Race race, int homeTotal, int awayTotal,
                                 Map<UUID, String> driverTeamMap, boolean canGenerateLineup,
                                 boolean lineupMissing, boolean cardsMissing, boolean lineupExists) {}

    public record ResultsFormData(RaceForm form, Race race, RaceScoring raceScoring) {}

    public record RaceFormData(RaceForm form, List<Matchday> matchdays, List<Team> teams,
                               List<Car> seasonCars, List<Track> seasonTracks,
                               Set<UUID> usedCarIds, Set<UUID> usedTrackIds) {}

    public record SaveResult(boolean success, String message, UUID raceId, UUID matchdayId) {}

    // --- List ---

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
            if (race.getHomeScore() != null && race.getAwayScore() != null) {
                raceScores.put(race.getId(), new int[]{race.getHomeScore(), race.getAwayScore()});
            }
        }

        return new RaceListData(races, raceScores, matchday, selectedSeasonId, seasonRepository.findAll());
    }

    // --- Detail ---

    public RaceDetailData getRaceDetailData(UUID raceId) {
        var race = raceRepository.findById(raceId).orElseThrow();

        int homeTotal = 0;
        int awayTotal = 0;
        Map<UUID, String> driverTeamMap = null;

        if (!race.getResults().isEmpty() && race.getHomeTeam() != null) {
            homeTotal = race.getResults().stream()
                    .filter(r -> scoringService.isDriverInTeam(r, race.getId(), race.getHomeTeam().getId()))
                    .mapToInt(RaceResult::getPointsTotal).sum();
            awayTotal = race.getResults().stream()
                    .filter(r -> !scoringService.isDriverInTeam(r, race.getId(), race.getHomeTeam().getId()))
                    .mapToInt(RaceResult::getPointsTotal).sum();

            var seasonId = race.getMatchday().getSeason().getId();
            driverTeamMap = new HashMap<>();
            for (var result : race.getResults()) {
                var teamName = raceLineupRepository.findByRaceIdAndDriverId(race.getId(), result.getDriver().getId())
                        .map(rl -> rl.getTeam().getShortName())
                        .orElseGet(() -> result.getDriver().getSeasonDrivers().stream()
                                .filter(sd -> sd.getSeason().getId().equals(seasonId))
                                .map(sd -> sd.getTeam().getShortName())
                                .findFirst().orElse("?"));
                driverTeamMap.put(result.getDriver().getId(), teamName);
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

        return new RaceDetailData(race, homeTotal, awayTotal, driverTeamMap,
                hasLineup && hasHomeCard && hasAwayCard && !lineupExists,
                !hasLineup, !hasHomeCard || !hasAwayCard, lineupExists);
    }

    // --- Form data for new race ---

    public RaceFormData getNewRaceFormData(UUID matchdayId) {
        var form = new RaceForm();
        List<Car> seasonCars = List.of();
        List<Track> seasonTracks = List.of();

        if (matchdayId != null) {
            form.setMatchdayId(matchdayId);
            var md = matchdayRepository.findById(matchdayId).orElse(null);
            if (md != null) {
                var season = md.getSeason();
                seasonCars = season.getCars();
                seasonTracks = season.getTracks();
            }
        }

        return new RaceFormData(form, matchdayRepository.findAll(), teamRepository.findAll(),
                seasonCars, seasonTracks, Set.of(), Set.of());
    }

    // --- Form data for edit ---

    public RaceFormData getRaceFormData(UUID raceId) {
        var race = raceRepository.findById(raceId).orElseThrow();
        var form = toForm(race);
        var season = race.getMatchday().getSeason();

        return new RaceFormData(form, matchdayRepository.findAll(), teamRepository.findAll(),
                season.getCars(), season.getTracks(),
                getUsedCarIds(season.getId(), race.getHomeTeam().getId(), race.getId()),
                getUsedTrackIds(season.getId(), race.getHomeTeam().getId(), race.getId()));
    }

    // --- Results form data ---

    public ResultsFormData getResultsFormData(UUID raceId) {
        var race = raceRepository.findById(raceId).orElseThrow();
        var form = toForm(race);

        if (form.getResults().isEmpty()) {
            var seasonId = race.getMatchday().getSeason().getId();
            populateDrivers(form, raceId, seasonId, race.getHomeTeam());
            populateDrivers(form, raceId, seasonId, race.getAwayTeam());
        }

        return new ResultsFormData(form, race, race.getMatchday().getSeason().getRaceScoring());
    }

    // --- Save race ---

    @Transactional
    public SaveResult saveRace(RaceForm form) {
        var matchday = matchdayRepository.findById(form.getMatchdayId()).orElseThrow();
        var homeTeam = teamRepository.findById(form.getHomeTeamId()).orElseThrow();
        var awayTeam = teamRepository.findById(form.getAwayTeamId()).orElseThrow();

        Race race;
        if (form.getId() != null) {
            race = raceRepository.findById(form.getId()).orElseThrow();
        } else {
            race = new Race();
        }

        race.setMatchday(matchday);

        Match match = race.getMatch();
        if (match == null) {
            match = new Match(matchday, homeTeam, awayTeam);
            match = matchRepository.save(match);
            race.setMatch(match);
        } else {
            match.setHomeTeam(homeTeam);
            match.setAwayTeam(awayTeam);
        }

        if (form.getTrackId() != null) {
            race.setTrack(trackRepository.findById(form.getTrackId()).orElse(null));
        } else {
            race.setTrack(null);
        }
        if (form.getCarId() != null) {
            race.setCar(carRepository.findById(form.getCarId()).orElse(null));
        } else {
            race.setCar(null);
        }
        race.setDateTime(form.getDateTime());

        // Pool validation
        var season = matchday.getSeason();
        if (race.getCar() != null && !season.getCars().contains(race.getCar())) {
            return new SaveResult(false, "Car is not in this season's pool", form.getId(), form.getMatchdayId());
        }
        if (race.getTrack() != null && !season.getTracks().contains(race.getTrack())) {
            return new SaveResult(false, "Track is not in this season's pool", form.getId(), form.getMatchdayId());
        }

        // Uniqueness validation
        if (race.getCar() != null) {
            var usedCarIds = getUsedCarIds(season.getId(), homeTeam.getId(), form.getId());
            if (usedCarIds.contains(race.getCar().getId())) {
                return new SaveResult(false,
                        homeTeam.getShortName() + " has already used " + race.getCar().getDisplayName() + " this season",
                        form.getId(), form.getMatchdayId());
            }
        }
        if (race.getTrack() != null) {
            var usedTrackIds = getUsedTrackIds(season.getId(), homeTeam.getId(), form.getId());
            if (usedTrackIds.contains(race.getTrack().getId())) {
                return new SaveResult(false,
                        homeTeam.getShortName() + " has already used " + race.getTrack().getName() + " this season",
                        form.getId(), form.getMatchdayId());
            }
        }

        raceRepository.save(race);
        log.info("Saved race: {} vs {} ({})", homeTeam.getShortName(), awayTeam.getShortName(), matchday.getLabel());
        return new SaveResult(true,
                "Race saved: " + homeTeam.getShortName() + " vs " + awayTeam.getShortName(),
                race.getId(), form.getMatchdayId());
    }

    // --- Save results ---

    @Transactional
    public String saveResults(UUID raceId, List<RaceResultForm> results) {
        var race = raceRepository.findById(raceId).orElseThrow();

        race.getResults().clear();

        for (var rf : results) {
            if (rf.getDriverId() == null) continue;

            var driver = driverRepository.findById(rf.getDriverId()).orElseThrow();
            var result = new RaceResult(race, driver, rf.getPosition(), rf.getQualiPosition(), rf.isFastestLap());
            scoringService.calculatePoints(result, race.getMatchday().getSeason().getRaceScoring());
            race.getResults().add(result);
        }

        raceRepository.save(race);
        scoringService.aggregateMatchScores(race);

        var homeScore = race.getHomeScore() != null ? race.getHomeScore() : 0;
        var awayScore = race.getAwayScore() != null ? race.getAwayScore() : 0;

        log.info("Saved results for {} vs {}: {} : {}",
                race.getHomeTeam().getShortName(), race.getAwayTeam().getShortName(), homeScore, awayScore);
        return "Results saved: " + race.getHomeTeam().getShortName() + " " + homeScore +
                " : " + awayScore + " " + race.getAwayTeam().getShortName();
    }

    // --- Quick score ---

    @Transactional
    public String quickScore(UUID raceId, int homeScore, int awayScore) {
        var race = raceRepository.findById(raceId).orElseThrow();
        if (race.getMatch() != null) {
            race.getMatch().setHomeScore(homeScore);
            race.getMatch().setAwayScore(awayScore);
        }
        raceRepository.save(race);
        log.info("Quick score: {} {} : {} {}",
                race.getHomeTeam().getShortName(), homeScore, awayScore, race.getAwayTeam().getShortName());
        return race.getHomeTeam().getShortName() + " " + homeScore + " : " + awayScore + " " + race.getAwayTeam().getShortName();
    }

    // --- Upload attachment ---

    @Transactional
    public String uploadAttachment(UUID raceId, MultipartFile file) {
        var race = raceRepository.findById(raceId).orElseThrow();
        try {
            String url = fileStorageService.store(raceId, file);
            var attachment = new RaceAttachment(race, AttachmentType.FILE, file.getOriginalFilename(), url);
            raceAttachmentRepository.save(attachment);
            return file.getOriginalFilename();
        } catch (Exception e) {
            log.error("Upload failed for race {}", raceId, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    // --- Add link ---

    @Transactional
    public String addLink(UUID raceId, String name, String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("Link must start with http:// or https://");
        }
        var race = raceRepository.findById(raceId).orElseThrow();
        var attachment = new RaceAttachment(race, AttachmentType.LINK, name, url);
        raceAttachmentRepository.save(attachment);
        return name;
    }

    // --- Delete attachment ---

    @Transactional
    public UUID deleteAttachment(UUID attachmentId) {
        var attachment = raceAttachmentRepository.findById(attachmentId).orElseThrow();
        UUID raceId = attachment.getRace().getId();
        if (attachment.getType() == AttachmentType.FILE) {
            fileStorageService.delete(attachment.getUrl());
        }
        raceAttachmentRepository.delete(attachment);
        return raceId;
    }

    // --- Generate lineup ---

    @Transactional
    public void generateLineup(UUID raceId) {
        var race = raceRepository.findById(raceId).orElseThrow();
        try {
            String url = lineupGraphicService.generateLineup(race);
            String attachmentName = race.getMatchday().getLabel() + "-"
                    + race.getHomeTeam().getShortName() + "-" + race.getAwayTeam().getShortName() + "-Lineups";
            var attachment = new RaceAttachment(race, AttachmentType.FILE, attachmentName, url);
            raceAttachmentRepository.save(attachment);
        } catch (Exception e) {
            log.error("Lineup generation failed for race {}", raceId, e);
            throw new RuntimeException("Generation failed: " + e.getMessage(), e);
        }
    }

    // --- Download attachment ---

    public ResponseEntity<Resource> downloadAttachment(UUID attachmentId) {
        var attachment = raceAttachmentRepository.findById(attachmentId).orElseThrow();
        if (attachment.getType() != AttachmentType.FILE) {
            return ResponseEntity.badRequest().build();
        }
        String url = attachment.getUrl();
        Path file = Paths.get(uploadDir).toAbsolutePath().normalize()
                .resolve(url.substring("/uploads/".length()));
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = "application/octet-stream";
        try { contentType = Files.probeContentType(file); } catch (IOException ignored) {}
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getName() + getExtension(file) + "\"")
                .body(new FileSystemResource(file));
    }

    // --- Delete race ---

    @Transactional
    public UUID deleteRace(UUID raceId) {
        var race = raceRepository.findById(raceId).orElseThrow();
        var matchdayId = race.getMatchday().getId();
        raceRepository.delete(race);
        log.info("Deleted race: {} vs {}", race.getHomeTeam().getShortName(), race.getAwayTeam().getShortName());
        return matchdayId;
    }

    // --- Used selections ---

    public Map<String, Set<UUID>> getUsedSelections(UUID seasonId, UUID homeTeamId, UUID excludeRaceId) {
        return Map.of(
                "usedCarIds", getUsedCarIds(seasonId, homeTeamId, excludeRaceId),
                "usedTrackIds", getUsedTrackIds(seasonId, homeTeamId, excludeRaceId));
    }

    // --- Private helpers ---

    private Set<UUID> getUsedCarIds(UUID seasonId, UUID homeTeamId, UUID excludeRaceId) {
        return raceRepository.findByMatchdaySeasonId(seasonId).stream()
                .filter(r -> !r.isBye())
                .filter(r -> r.getHomeTeam().getId().equals(homeTeamId))
                .filter(r -> excludeRaceId == null || !r.getId().equals(excludeRaceId))
                .filter(r -> r.getCar() != null)
                .map(r -> r.getCar().getId())
                .collect(Collectors.toSet());
    }

    private Set<UUID> getUsedTrackIds(UUID seasonId, UUID homeTeamId, UUID excludeRaceId) {
        return raceRepository.findByMatchdaySeasonId(seasonId).stream()
                .filter(r -> !r.isBye())
                .filter(r -> r.getHomeTeam().getId().equals(homeTeamId))
                .filter(r -> excludeRaceId == null || !r.getId().equals(excludeRaceId))
                .filter(r -> r.getTrack() != null)
                .map(r -> r.getTrack().getId())
                .collect(Collectors.toSet());
    }

    private void populateDrivers(RaceForm form, UUID raceId, UUID seasonId, Team team) {
        var allLineups = raceLineupRepository.findByRaceId(raceId);
        var lineupDrivers = allLineups.stream()
                .filter(lu -> lu.getTeam().getId().equals(team.getId())
                        || lu.getTeam().getParentOrSelf().getId().equals(team.getId()))
                .toList();

        if (!lineupDrivers.isEmpty()) {
            int pos = form.getResults().size() + 1;
            for (var lineup : lineupDrivers) {
                var rf = new RaceResultForm();
                rf.setDriverId(lineup.getDriver().getId());
                rf.setDriverPsnId(lineup.getDriver().getPsnId());
                rf.setTeamShortName(lineup.getTeam().getShortName());
                rf.setPosition(pos);
                rf.setQualiPosition(pos);
                form.getResults().add(rf);
                pos++;
            }
        } else {
            var seasonDrivers = seasonDriverRepository.findBySeasonIdAndTeamId(seasonId, team.getId());
            int pos = form.getResults().size() + 1;
            for (var sd : seasonDrivers) {
                var rf = new RaceResultForm();
                rf.setDriverId(sd.getDriver().getId());
                rf.setDriverPsnId(sd.getDriver().getPsnId());
                rf.setTeamShortName(team.getShortName());
                rf.setPosition(pos);
                rf.setQualiPosition(pos);
                form.getResults().add(rf);
                pos++;
            }
        }
    }

    private RaceForm toForm(Race race) {
        var form = new RaceForm();
        form.setId(race.getId());
        form.setMatchdayId(race.getMatchday().getId());
        form.setHomeTeamId(race.getHomeTeam().getId());
        form.setAwayTeamId(race.getAwayTeam().getId());
        form.setTrackId(race.getTrack() != null ? race.getTrack().getId() : null);
        form.setCarId(race.getCar() != null ? race.getCar().getId() : null);
        form.setDateTime(race.getDateTime());

        for (var result : race.getResults()) {
            var rf = new RaceResultForm();
            rf.setDriverId(result.getDriver().getId());
            rf.setDriverPsnId(result.getDriver().getPsnId());
            // Use RaceLineup for team name (Source of Truth), fallback to SeasonDriver
            rf.setTeamShortName(
                    raceLineupRepository.findByRaceIdAndDriverId(race.getId(), result.getDriver().getId())
                            .map(rl -> rl.getTeam().getShortName())
                            .orElseGet(() -> result.getDriver().getSeasonDrivers().stream()
                                    .filter(sd -> sd.getSeason().getId().equals(race.getMatchday().getSeason().getId()))
                                    .map(sd -> sd.getTeam().getShortName())
                                    .findFirst().orElse("?")));
            rf.setPosition(result.getPosition());
            rf.setQualiPosition(result.getQualiPosition());
            rf.setFastestLap(result.isFastestLap());
            form.getResults().add(rf);
        }
        return form;
    }

    private String getExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }
}
