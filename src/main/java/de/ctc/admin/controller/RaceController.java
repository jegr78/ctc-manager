package de.ctc.admin.controller;

import de.ctc.admin.dto.RaceForm;
import de.ctc.admin.dto.RaceResultForm;
import de.ctc.domain.model.AttachmentType;
import de.ctc.domain.model.Race;
import de.ctc.domain.model.RaceAttachment;
import de.ctc.domain.model.RaceResult;
import de.ctc.domain.model.Match;
import de.ctc.domain.repository.*;
import de.ctc.domain.service.FileStorageService;
import de.ctc.domain.service.ScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/admin/races")
@RequiredArgsConstructor
public class RaceController {

    private final RaceRepository raceRepository;
    private final MatchRepository matchRepository;
    private final MatchdayRepository matchdayRepository;
    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final DriverRepository driverRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final MatchdayLineupRepository matchdayLineupRepository;
    private final RaceAttachmentRepository raceAttachmentRepository;
    private final CarRepository carRepository;
    private final TrackRepository trackRepository;
    private final ScoringService scoringService;
    private final FileStorageService fileStorageService;

    @GetMapping
    public String list(@RequestParam(required = false) UUID matchdayId,
                       @RequestParam(required = false) UUID seasonId,
                       Model model) {
        if (matchdayId != null) {
            model.addAttribute("races", raceRepository.findByMatchdayId(matchdayId));
            model.addAttribute("matchday", matchdayRepository.findById(matchdayId).orElse(null));
        } else if (seasonId != null) {
            model.addAttribute("races", raceRepository.findByMatchdaySeasonId(seasonId));
            model.addAttribute("selectedSeasonId", seasonId);
        } else {
            model.addAttribute("races", raceRepository.findAll());
        }
        // Build score map from match/playoff scores
        var raceScores = new java.util.HashMap<java.util.UUID, int[]>();
        var races = (java.util.List<de.ctc.domain.model.Race>) model.getAttribute("races");
        if (races != null) {
            for (var race : races) {
                if (race.getHomeScore() != null && race.getAwayScore() != null) {
                    raceScores.put(race.getId(), new int[]{race.getHomeScore(), race.getAwayScore()});
                }
            }
        }
        model.addAttribute("raceScores", raceScores);
        model.addAttribute("seasons", seasonRepository.findAll());
        return "admin/races";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        var race = raceRepository.findById(id).orElseThrow();
        model.addAttribute("race", race);

        if (!race.getResults().isEmpty() && race.getHomeTeam() != null) {
            int homeTotal = race.getResults().stream()
                    .filter(r -> isHomeTeamDriver(r, race))
                    .mapToInt(de.ctc.domain.model.RaceResult::getPointsTotal).sum();
            int awayTotal = race.getResults().stream()
                    .filter(r -> !isHomeTeamDriver(r, race))
                    .mapToInt(de.ctc.domain.model.RaceResult::getPointsTotal).sum();
            model.addAttribute("homeTotal", homeTotal);
            model.addAttribute("awayTotal", awayTotal);

            // Build driver→team map for template
            var seasonId = race.getMatchday().getSeason().getId();
            var driverTeamMap = new java.util.HashMap<java.util.UUID, String>();
            for (var result : race.getResults()) {
                var teamName = result.getDriver().getSeasonDrivers().stream()
                        .filter(sd -> sd.getSeason().getId().equals(seasonId))
                        .map(sd -> sd.getTeam().getShortName())
                        .findFirst().orElse("?");
                driverTeamMap.put(result.getDriver().getId(), teamName);
            }
            model.addAttribute("driverTeamMap", driverTeamMap);
        }
        return "admin/race-detail";
    }

    @GetMapping("/new")
    public String create(@RequestParam(required = false) UUID matchdayId, Model model) {
        var form = new RaceForm();
        if (matchdayId != null) {
            form.setMatchdayId(matchdayId);
            var md = matchdayRepository.findById(matchdayId).orElse(null);
            if (md != null) {
                var season = md.getSeason();
                model.addAttribute("seasonCars", season.getCars());
                model.addAttribute("seasonTracks", season.getTracks());
                model.addAttribute("usedCarIds", Set.of());
                model.addAttribute("usedTrackIds", Set.of());
            }
        }
        model.addAttribute("raceForm", form);
        model.addAttribute("matchdays", matchdayRepository.findAll());
        model.addAttribute("teams", teamRepository.findAll());
        return "admin/race-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        var race = raceRepository.findById(id).orElseThrow();
        var form = toForm(race);
        model.addAttribute("raceForm", form);
        model.addAttribute("matchdays", matchdayRepository.findAll());
        model.addAttribute("teams", teamRepository.findAll());
        var season = race.getMatchday().getSeason();
        model.addAttribute("seasonCars", season.getCars());
        model.addAttribute("seasonTracks", season.getTracks());
        model.addAttribute("usedCarIds", getUsedCarIds(season.getId(), race.getHomeTeam().getId(), race.getId()));
        model.addAttribute("usedTrackIds", getUsedTrackIds(season.getId(), race.getHomeTeam().getId(), race.getId()));
        return "admin/race-form";
    }

    @GetMapping("/{id}/results")
    public String results(@PathVariable UUID id, Model model) {
        var race = raceRepository.findById(id).orElseThrow();
        var form = toForm(race);

        // If no results yet, pre-populate with drivers from both teams
        if (form.getResults().isEmpty()) {
            var matchdayId = race.getMatchday().getId();
            var seasonId = race.getMatchday().getSeason().getId();

            // Try MatchdayLineup first (for sub-teams), fall back to SeasonDriver
            populateDrivers(form, matchdayId, seasonId, race.getHomeTeam());
            populateDrivers(form, matchdayId, seasonId, race.getAwayTeam());
        }

        model.addAttribute("raceForm", form);
        model.addAttribute("race", race);
        model.addAttribute("raceScoring", race.getMatchday().getSeason().getRaceScoring());
        return "admin/race-results";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute RaceForm form, RedirectAttributes redirectAttributes) {
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

        // Ensure race has a Match for team assignment
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
            redirectAttributes.addFlashAttribute("errorMessage", "Car is not in this season's pool");
            return "redirect:/admin/races/" + (form.getId() != null ? form.getId() + "/edit" : "new?matchdayId=" + form.getMatchdayId());
        }
        if (race.getTrack() != null && !season.getTracks().contains(race.getTrack())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Track is not in this season's pool");
            return "redirect:/admin/races/" + (form.getId() != null ? form.getId() + "/edit" : "new?matchdayId=" + form.getMatchdayId());
        }

        // Uniqueness validation
        if (race.getCar() != null) {
            var usedCarIds = getUsedCarIds(season.getId(), homeTeam.getId(), form.getId());
            if (usedCarIds.contains(race.getCar().getId())) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        homeTeam.getShortName() + " has already used " + race.getCar().getDisplayName() + " this season");
                return "redirect:/admin/races/" + (form.getId() != null ? form.getId() + "/edit" : "new?matchdayId=" + form.getMatchdayId());
            }
        }
        if (race.getTrack() != null) {
            var usedTrackIds = getUsedTrackIds(season.getId(), homeTeam.getId(), form.getId());
            if (usedTrackIds.contains(race.getTrack().getId())) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        homeTeam.getShortName() + " has already used " + race.getTrack().getName() + " this season");
                return "redirect:/admin/races/" + (form.getId() != null ? form.getId() + "/edit" : "new?matchdayId=" + form.getMatchdayId());
            }
        }

        raceRepository.save(race);
        log.info("Saved race: {} vs {} ({})", homeTeam.getShortName(), awayTeam.getShortName(), matchday.getLabel());
        redirectAttributes.addFlashAttribute("successMessage",
                "Race saved: " + homeTeam.getShortName() + " vs " + awayTeam.getShortName());
        return "redirect:/admin/races?matchdayId=" + form.getMatchdayId();
    }

    @Transactional
    @PostMapping("/{id}/results")
    public String saveResults(@PathVariable UUID id, @ModelAttribute RaceForm form,
                              RedirectAttributes redirectAttributes) {
        var race = raceRepository.findById(id).orElseThrow();

        // Clear existing results and create new ones
        race.getResults().clear();

        for (var rf : form.getResults()) {
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
        redirectAttributes.addFlashAttribute("successMessage",
                "Results saved: " + race.getHomeTeam().getShortName() + " " + homeScore +
                " : " + awayScore + " " + race.getAwayTeam().getShortName());
        return "redirect:/admin/races/" + id + "/results";
    }

    @PostMapping("/{id}/quick-score")
    public String quickScore(@PathVariable UUID id,
                              @RequestParam int homeScore,
                              @RequestParam int awayScore,
                              @RequestParam(required = false) String returnUrl,
                              RedirectAttributes redirectAttributes) {
        var race = raceRepository.findById(id).orElseThrow();
        if (race.getMatch() != null) {
            race.getMatch().setHomeScore(homeScore);
            race.getMatch().setAwayScore(awayScore);
        }
        raceRepository.save(race);
        log.info("Quick score: {} {} : {} {}",
                race.getHomeTeam().getShortName(), homeScore, awayScore, race.getAwayTeam().getShortName());
        redirectAttributes.addFlashAttribute("successMessage",
                race.getHomeTeam().getShortName() + " " + homeScore + " : " + awayScore + " " + race.getAwayTeam().getShortName());
        String safeUrl = (returnUrl != null && returnUrl.startsWith("/") && !returnUrl.startsWith("//"))
                ? returnUrl : "/admin/races";
        return "redirect:" + safeUrl;
    }

    @PostMapping("/{id}/attachments/upload")
    public String uploadAttachment(@PathVariable UUID id,
                                    @RequestParam("file") MultipartFile file,
                                    RedirectAttributes redirectAttributes) {
        var race = raceRepository.findById(id).orElseThrow();
        try {
            String url = fileStorageService.store(id, file);
            var attachment = new RaceAttachment(race, AttachmentType.FILE, file.getOriginalFilename(), url);
            raceAttachmentRepository.save(attachment);
            redirectAttributes.addFlashAttribute("successMessage", "File uploaded: " + file.getOriginalFilename());
        } catch (Exception e) {
            log.error("Upload failed for race {}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/races/" + id;
    }

    @PostMapping("/{id}/attachments/link")
    public String addLink(@PathVariable UUID id,
                           @RequestParam String name,
                           @RequestParam String url,
                           RedirectAttributes redirectAttributes) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            redirectAttributes.addFlashAttribute("errorMessage", "Link must start with http:// or https://");
            return "redirect:/admin/races/" + id;
        }
        var race = raceRepository.findById(id).orElseThrow();
        var attachment = new RaceAttachment(race, AttachmentType.LINK, name, url);
        raceAttachmentRepository.save(attachment);
        redirectAttributes.addFlashAttribute("successMessage", "Link added: " + name);
        return "redirect:/admin/races/" + id;
    }

    @PostMapping("/attachments/{attachmentId}/delete")
    public String deleteAttachment(@PathVariable UUID attachmentId, RedirectAttributes redirectAttributes) {
        var attachment = raceAttachmentRepository.findById(attachmentId).orElseThrow();
        UUID raceId = attachment.getRace().getId();
        if (attachment.getType() == AttachmentType.FILE) {
            fileStorageService.delete(attachment.getUrl());
        }
        raceAttachmentRepository.delete(attachment);
        redirectAttributes.addFlashAttribute("successMessage", "Attachment deleted");
        return "redirect:/admin/races/" + raceId;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        var race = raceRepository.findById(id).orElseThrow();
        var matchdayId = race.getMatchday().getId();
        raceRepository.delete(race);
        log.info("Deleted race: {} vs {}", race.getHomeTeam().getShortName(), race.getAwayTeam().getShortName());
        redirectAttributes.addFlashAttribute("successMessage", "Race deleted");
        return "redirect:/admin/races?matchdayId=" + matchdayId;
    }

    @GetMapping("/used-selections")
    @ResponseBody
    public Map<String, Set<UUID>> usedSelections(
            @RequestParam UUID seasonId,
            @RequestParam UUID homeTeamId,
            @RequestParam(required = false) UUID excludeRaceId) {
        return Map.of(
                "usedCarIds", getUsedCarIds(seasonId, homeTeamId, excludeRaceId),
                "usedTrackIds", getUsedTrackIds(seasonId, homeTeamId, excludeRaceId));
    }

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

    private void populateDrivers(RaceForm form, UUID matchdayId, UUID seasonId, de.ctc.domain.model.Team team) {
        var lineupDrivers = matchdayLineupRepository.findByMatchdayIdAndTeamId(matchdayId, team.getId());

        if (!lineupDrivers.isEmpty()) {
            // Sub-team: use matchday lineup
            int pos = form.getResults().size() + 1;
            for (var lineup : lineupDrivers) {
                var rf = new RaceResultForm();
                rf.setDriverId(lineup.getDriver().getId());
                rf.setDriverPsnId(lineup.getDriver().getPsnId());
                rf.setTeamShortName(team.getShortName());
                rf.setPosition(pos);
                rf.setQualiPosition(pos);
                form.getResults().add(rf);
                pos++;
            }
        } else {
            // Standalone team: use season driver assignment
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

    private boolean isHomeTeamDriver(RaceResult result, Race race) {
        var seasonId = race.getMatchday().getSeason().getId();
        return result.getDriver().getSeasonDrivers().stream()
                .anyMatch(sd -> sd.getSeason().getId().equals(seasonId)
                        && sd.getTeam().getId().equals(race.getHomeTeam().getId()));
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
            rf.setTeamShortName(result.getDriver().getSeasonDrivers().stream()
                    .filter(sd -> sd.getSeason().getId().equals(race.getMatchday().getSeason().getId()))
                    .map(sd -> sd.getTeam().getShortName())
                    .findFirst().orElse("?"));
            rf.setPosition(result.getPosition());
            rf.setQualiPosition(result.getQualiPosition());
            rf.setFastestLap(result.isFastestLap());
            form.getResults().add(rf);
        }
        return form;
    }
}
