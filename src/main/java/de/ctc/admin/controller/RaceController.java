package de.ctc.admin.controller;

import de.ctc.admin.dto.RaceForm;
import de.ctc.admin.dto.RaceResultForm;
import de.ctc.domain.model.Race;
import de.ctc.domain.model.RaceResult;
import de.ctc.domain.repository.*;
import de.ctc.domain.service.ScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/admin/races")
@RequiredArgsConstructor
public class RaceController {

    private final RaceRepository raceRepository;
    private final MatchdayRepository matchdayRepository;
    private final TeamRepository teamRepository;
    private final DriverRepository driverRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final ScoringService scoringService;

    @GetMapping
    public String list(@RequestParam(required = false) UUID matchdayId, Model model) {
        if (matchdayId != null) {
            model.addAttribute("races", raceRepository.findByMatchdayId(matchdayId));
            model.addAttribute("matchday", matchdayRepository.findById(matchdayId).orElse(null));
        } else {
            model.addAttribute("races", raceRepository.findAll());
        }
        return "admin/races";
    }

    @GetMapping("/new")
    public String create(@RequestParam(required = false) UUID matchdayId, Model model) {
        var form = new RaceForm();
        if (matchdayId != null) {
            form.setMatchdayId(matchdayId);
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
        return "admin/race-form";
    }

    @GetMapping("/{id}/results")
    public String results(@PathVariable UUID id, Model model) {
        var race = raceRepository.findById(id).orElseThrow();
        var form = toForm(race);

        // If no results yet, pre-populate with drivers from both teams
        if (form.getResults().isEmpty()) {
            var seasonId = race.getMatchday().getSeason().getId();
            var homeDrivers = seasonDriverRepository.findBySeasonIdAndTeamId(seasonId, race.getHomeTeam().getId());
            var awayDrivers = seasonDriverRepository.findBySeasonIdAndTeamId(seasonId, race.getAwayTeam().getId());

            int pos = 1;
            for (var sd : homeDrivers) {
                var rf = new RaceResultForm();
                rf.setDriverId(sd.getDriver().getId());
                rf.setDriverPsnId(sd.getDriver().getPsnId());
                rf.setTeamShortName(sd.getTeam().getShortName());
                rf.setPosition(pos);
                rf.setQualiPosition(pos);
                form.getResults().add(rf);
                pos++;
            }
            for (var sd : awayDrivers) {
                var rf = new RaceResultForm();
                rf.setDriverId(sd.getDriver().getId());
                rf.setDriverPsnId(sd.getDriver().getPsnId());
                rf.setTeamShortName(sd.getTeam().getShortName());
                rf.setPosition(pos);
                rf.setQualiPosition(pos);
                form.getResults().add(rf);
                pos++;
            }
        }

        model.addAttribute("raceForm", form);
        model.addAttribute("race", race);
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
        race.setHomeTeam(homeTeam);
        race.setAwayTeam(awayTeam);
        race.setTrack(form.getTrack());
        race.setCar(form.getCar());

        raceRepository.save(race);
        log.info("Saved race: {} vs {} ({})", homeTeam.getShortName(), awayTeam.getShortName(), matchday.getLabel());
        redirectAttributes.addFlashAttribute("successMessage",
                "Rennen gespeichert: " + homeTeam.getShortName() + " vs " + awayTeam.getShortName());
        return "redirect:/admin/races?matchdayId=" + form.getMatchdayId();
    }

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
            scoringService.calculatePoints(result);
            race.getResults().add(result);
        }

        raceRepository.save(race);

        int homeTotal = race.getResults().stream()
                .filter(r -> isHomeTeamDriver(r, race))
                .mapToInt(RaceResult::getPointsTotal).sum();
        int awayTotal = race.getResults().stream()
                .filter(r -> !isHomeTeamDriver(r, race))
                .mapToInt(RaceResult::getPointsTotal).sum();

        log.info("Saved results for {} vs {}: {} : {}",
                race.getHomeTeam().getShortName(), race.getAwayTeam().getShortName(), homeTotal, awayTotal);
        redirectAttributes.addFlashAttribute("successMessage",
                "Ergebnisse gespeichert: " + race.getHomeTeam().getShortName() + " " + homeTotal +
                " : " + awayTotal + " " + race.getAwayTeam().getShortName());
        return "redirect:/admin/races/" + id + "/results";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        var race = raceRepository.findById(id).orElseThrow();
        var matchdayId = race.getMatchday().getId();
        raceRepository.delete(race);
        log.info("Deleted race: {} vs {}", race.getHomeTeam().getShortName(), race.getAwayTeam().getShortName());
        redirectAttributes.addFlashAttribute("successMessage", "Rennen gelöscht");
        return "redirect:/admin/races?matchdayId=" + matchdayId;
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
        form.setTrack(race.getTrack());
        form.setCar(race.getCar());

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
