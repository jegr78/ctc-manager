package de.ctc.admin.controller;

import de.ctc.admin.dto.PlayoffForm;
import de.ctc.admin.dto.SeedForm;
import de.ctc.domain.model.Race;
import de.ctc.domain.model.Season;
import de.ctc.domain.model.SeasonDriver;
import de.ctc.domain.repository.*;
import de.ctc.domain.service.PlayoffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/admin/playoffs")
@RequiredArgsConstructor
public class PlayoffController {

    private final PlayoffService playoffService;
    private final PlayoffRepository playoffRepository;
    private final PlayoffMatchupRepository playoffMatchupRepository;
    private final SeasonRepository seasonRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final MatchdayRepository matchdayRepository;
    private final RaceRepository raceRepository;

    @GetMapping
    public String list(@RequestParam(required = false) UUID seasonId, Model model) {
        model.addAttribute("seasons", seasonRepository.findAll());

        if (seasonId != null) {
            model.addAttribute("selectedSeasonId", seasonId);
            playoffRepository.findBySeasonId(seasonId).ifPresent(playoff -> {
                model.addAttribute("playoff", playoff);
                model.addAttribute("bracket", playoffService.getBracketView(playoff.getId()));
            });
        } else {
            seasonRepository.findAll().stream()
                    .filter(Season::isActive)
                    .findFirst()
                    .ifPresent(season -> {
                        model.addAttribute("selectedSeasonId", season.getId());
                        playoffRepository.findBySeasonId(season.getId()).ifPresent(playoff -> {
                            model.addAttribute("playoff", playoff);
                            model.addAttribute("bracket", playoffService.getBracketView(playoff.getId()));
                        });
                    });
        }

        return "admin/playoff-bracket";
    }

    @GetMapping("/new")
    public String create(@RequestParam(required = false) UUID seasonId, Model model) {
        var form = new PlayoffForm();
        if (seasonId != null) {
            form.setSeasonId(seasonId);
        }
        model.addAttribute("playoffForm", form);
        model.addAttribute("seasons", seasonRepository.findAll());
        return "admin/playoff-form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute PlayoffForm form, RedirectAttributes redirectAttributes) {
        try {
            var playoff = playoffService.createPlayoff(
                    form.getSeasonId(), form.getName(), form.getBestOfLegs(), form.getNumberOfTeams());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Playoff erstellt: " + playoff.getName());
            return "redirect:/admin/playoffs?seasonId=" + form.getSeasonId();
        } catch (Exception e) {
            log.error("Error creating playoff", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Fehler: " + e.getMessage());
            return "redirect:/admin/playoffs/new?seasonId=" + form.getSeasonId();
        }
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}/seed")
    public String seed(@PathVariable UUID id, Model model) {
        var playoff = playoffRepository.findById(id).orElseThrow();
        var bracket = playoffService.getBracketView(id);

        // Get first round matchups for seeding
        var firstRound = playoff.getRounds().stream()
                .filter(r -> r.getRoundIndex() == 0)
                .findFirst().orElseThrow();

        // Get teams participating in this season
        var seasonId = playoff.getSeason().getId();
        var teams = seasonDriverRepository.findBySeasonId(seasonId).stream()
                .map(SeasonDriver::getTeam)
                .distinct()
                .toList();

        // Find already-seeded team IDs
        Set<UUID> seededTeamIds = firstRound.getMatchups().stream()
                .flatMap(m -> {
                    var ids = new java.util.ArrayList<UUID>();
                    if (m.getTeam1() != null) ids.add(m.getTeam1().getId());
                    if (m.getTeam2() != null) ids.add(m.getTeam2().getId());
                    return ids.stream();
                })
                .collect(Collectors.toSet());

        var form = new SeedForm();
        form.setPlayoffId(id);

        model.addAttribute("seedForm", form);
        model.addAttribute("playoff", playoff);
        model.addAttribute("bracket", bracket);
        model.addAttribute("firstRound", firstRound);
        model.addAttribute("teams", teams);
        model.addAttribute("seededTeamIds", seededTeamIds);
        return "admin/playoff-seed";
    }

    @PostMapping("/{id}/seed")
    public String saveSeed(@PathVariable UUID id, @ModelAttribute SeedForm form,
                           RedirectAttributes redirectAttributes) {
        for (var entry : form.getSeeds()) {
            if (entry.getTeamId() != null) {
                playoffService.seedTeam(entry.getMatchupId(), entry.getTeamId(), entry.getSlot());
            }
        }
        redirectAttributes.addFlashAttribute("successMessage", "Setzliste gespeichert");
        return "redirect:/admin/playoffs?seasonId=" +
                playoffRepository.findById(id).orElseThrow().getSeason().getId();
    }

    @Transactional(readOnly = true)
    @GetMapping("/matchup/{matchupId}")
    public String matchupDetail(@PathVariable UUID matchupId, Model model) {
        var matchup = playoffMatchupRepository.findById(matchupId).orElseThrow();
        var legs = raceRepository.findByPlayoffMatchupId(matchupId);
        var playoff = matchup.getRound().getPlayoff();

        model.addAttribute("matchup", matchup);
        model.addAttribute("legs", legs);
        model.addAttribute("playoff", playoff);
        model.addAttribute("matchdays", matchdayRepository.findBySeasonIdOrderBySortIndexAsc(
                playoff.getSeason().getId()));
        return "admin/playoff-matchup";
    }

    @PostMapping("/matchup/{matchupId}/add-race")
    public String addRace(@PathVariable UUID matchupId,
                          @RequestParam UUID matchdayId,
                          @RequestParam(required = false) String track,
                          @RequestParam(required = false) String car,
                          RedirectAttributes redirectAttributes) {
        var matchup = playoffMatchupRepository.findById(matchupId).orElseThrow();
        if (!matchup.isReady()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Beide Teams muessen gesetzt sein");
            return "redirect:/admin/playoffs/matchup/" + matchupId;
        }

        var matchday = matchdayRepository.findById(matchdayId).orElseThrow();
        var race = new Race(matchday, matchup.getTeam1(), matchup.getTeam2());
        race.setTrack(track);
        race.setCar(car);
        race.setPlayoffMatchup(matchup);
        raceRepository.save(race);

        redirectAttributes.addFlashAttribute("successMessage", "Leg hinzugefuegt");
        return "redirect:/admin/playoffs/matchup/" + matchupId;
    }

    @PostMapping("/matchup/{matchupId}/determine-winner")
    public String determineWinner(@PathVariable UUID matchupId, RedirectAttributes redirectAttributes) {
        try {
            playoffService.determineWinner(matchupId);
            var matchup = playoffMatchupRepository.findById(matchupId).orElseThrow();
            redirectAttributes.addFlashAttribute("successMessage",
                    "Gewinner: " + matchup.getWinner().getShortName());
            return "redirect:/admin/playoffs?seasonId=" +
                    matchup.getRound().getPlayoff().getSeason().getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Fehler: " + e.getMessage());
            return "redirect:/admin/playoffs/matchup/" + matchupId;
        }
    }

    @PostMapping("/matchup/{matchupId}/set-winner")
    public String setWinnerManually(@PathVariable UUID matchupId,
                                    @RequestParam UUID winnerTeamId,
                                    RedirectAttributes redirectAttributes) {
        try {
            playoffService.setWinnerManually(matchupId, winnerTeamId);
            var matchup = playoffMatchupRepository.findById(matchupId).orElseThrow();
            redirectAttributes.addFlashAttribute("successMessage",
                    "Gewinner manuell festgelegt: " + matchup.getWinner().getShortName());
            return "redirect:/admin/playoffs?seasonId=" +
                    matchup.getRound().getPlayoff().getSeason().getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Fehler: " + e.getMessage());
            return "redirect:/admin/playoffs/matchup/" + matchupId;
        }
    }
}
