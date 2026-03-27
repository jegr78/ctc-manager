package de.ctc.admin.controller;

import de.ctc.admin.dto.PlayoffForm;
import de.ctc.admin.dto.SeedForm;
import de.ctc.domain.model.Matchday;
import de.ctc.domain.model.Race;
import de.ctc.domain.model.Season;
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
    private final PlayoffRoundRepository playoffRoundRepository;
    private final PlayoffMatchupRepository playoffMatchupRepository;
    private final SeasonRepository seasonRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final MatchdayRepository matchdayRepository;
    private final RaceRepository raceRepository;

    @GetMapping
    public String list(@RequestParam(required = false) UUID seasonId, Model model) {
        var allSeasons = seasonRepository.findAll();
        model.addAttribute("seasons", allSeasons);

        UUID effectiveSeasonId = seasonId;
        if (effectiveSeasonId == null) {
            effectiveSeasonId = allSeasons.stream()
                    .filter(Season::isActive)
                    .map(Season::getId)
                    .findFirst().orElse(null);
        }

        if (effectiveSeasonId != null) {
            model.addAttribute("selectedSeasonId", effectiveSeasonId);
            playoffRepository.findBySeasonId(effectiveSeasonId).ifPresent(playoff -> {
                model.addAttribute("playoff", playoff);
                model.addAttribute("bracket", playoffService.getBracketView(playoff.getId()));
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
                    form.getSeasonId(), form.getName(), form.getNumberOfTeams());
            playoff.setStartDate(form.getStartDate());
            playoff.setEndDate(form.getEndDate());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Playoff created: " + playoff.getName());
            return "redirect:/admin/playoffs?seasonId=" + form.getSeasonId();
        } catch (Exception e) {
            log.error("Error creating playoff", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
            return "redirect:/admin/playoffs/new?seasonId=" + form.getSeasonId();
        }
    }

    @Transactional
    @PostMapping("/round/{roundId}/set-legs")
    public String setRoundLegs(@PathVariable UUID roundId, @RequestParam int bestOfLegs,
                               RedirectAttributes redirectAttributes) {
        var round = playoffRoundRepository.findById(roundId).orElseThrow();
        round.setBestOfLegs(bestOfLegs);
        playoffRoundRepository.save(round);
        redirectAttributes.addFlashAttribute("successMessage",
                round.getLabel() + ": " + bestOfLegs + " Leg(s)");
        return "redirect:/admin/playoffs?seasonId=" +
                round.getPlayoff().getSeason().getId();
    }

    @PostMapping("/{id}/add-season")
    public String addSeason(@PathVariable UUID id, @RequestParam UUID seasonId,
                            RedirectAttributes redirectAttributes) {
        playoffService.addSeasonToPlayoff(id, seasonId);
        redirectAttributes.addFlashAttribute("successMessage", "Season linked");
        return "redirect:/admin/playoffs?seasonId=" +
                playoffRepository.findById(id).orElseThrow().getSeason().getId();
    }

    @PostMapping("/{id}/remove-season")
    public String removeSeason(@PathVariable UUID id, @RequestParam UUID seasonId,
                               RedirectAttributes redirectAttributes) {
        playoffService.removeSeasonFromPlayoff(id, seasonId);
        redirectAttributes.addFlashAttribute("successMessage", "Season removed");
        return "redirect:/admin/playoffs?seasonId=" +
                playoffRepository.findById(id).orElseThrow().getSeason().getId();
    }

    @GetMapping("/{id}/seed")
    public String seed(@PathVariable UUID id, Model model) {
        var playoff = playoffRepository.findById(id).orElseThrow();
        var bracket = playoffService.getBracketView(id);

        // Get first round matchups for seeding
        var firstRound = playoff.getRounds().stream()
                .filter(r -> r.getRoundIndex() == 0)
                .findFirst().orElseThrow();

        // Get teams from all linked seasons (+ main season)
        var teams = playoffService.getPlayoffTeams(id);

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
        redirectAttributes.addFlashAttribute("successMessage", "Seeding saved");
        return "redirect:/admin/playoffs?seasonId=" +
                playoffRepository.findById(id).orElseThrow().getSeason().getId();
    }

    @GetMapping("/matchup/{matchupId}")
    public String matchupDetail(@PathVariable UUID matchupId, Model model) {
        var matchup = playoffMatchupRepository.findById(matchupId).orElseThrow();
        var legs = raceRepository.findByPlayoffMatchupId(matchupId);
        var playoff = matchup.getRound().getPlayoff();

        model.addAttribute("matchup", matchup);
        model.addAttribute("legs", legs);
        model.addAttribute("playoff", playoff);
        return "admin/playoff-matchup";
    }

    @Transactional
    @PostMapping("/matchup/{matchupId}/add-race")
    public String addRace(@PathVariable UUID matchupId,
                          @RequestParam(required = false) String track,
                          @RequestParam(required = false) String car,
                          RedirectAttributes redirectAttributes) {
        var matchup = playoffMatchupRepository.findById(matchupId).orElseThrow();
        if (!matchup.isReady()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Both teams must be set");
            return "redirect:/admin/playoffs/matchup/" + matchupId;
        }

        int existingLegs = raceRepository.findByPlayoffMatchupId(matchupId).size();
        int maxLegs = matchup.getRound().getBestOfLegs();
        if (existingLegs >= maxLegs) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Maximum number of legs reached (" + maxLegs + ")");
            return "redirect:/admin/playoffs/matchup/" + matchupId;
        }

        // Auto-create matchday for this playoff leg
        var season = matchup.getRound().getPlayoff().getSeason();
        int legNumber = existingLegs + 1;
        String label = matchup.getRound().getLabel() + " - Leg " + legNumber;
        var matchday = new Matchday(season, label, 100 + matchup.getRound().getRoundIndex() * 10 + legNumber);
        matchday = matchdayRepository.save(matchday);

        var race = new Race(matchday, matchup.getTeam1(), matchup.getTeam2());
        race.setTrack(track);
        race.setCar(car);
        race.setPlayoffMatchup(matchup);
        raceRepository.save(race);

        redirectAttributes.addFlashAttribute("successMessage", "Leg added");
        return "redirect:/admin/playoffs/matchup/" + matchupId;
    }

    @PostMapping("/matchup/{matchupId}/determine-winner")
    public String determineWinner(@PathVariable UUID matchupId, RedirectAttributes redirectAttributes) {
        try {
            playoffService.determineWinner(matchupId);
            var matchup = playoffMatchupRepository.findById(matchupId).orElseThrow();
            redirectAttributes.addFlashAttribute("successMessage",
                    "Winner: " + matchup.getWinner().getShortName());
            return "redirect:/admin/playoffs?seasonId=" +
                    matchup.getRound().getPlayoff().getSeason().getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
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
                    "Winner set manually: " + matchup.getWinner().getShortName());
            return "redirect:/admin/playoffs?seasonId=" +
                    matchup.getRound().getPlayoff().getSeason().getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
            return "redirect:/admin/playoffs/matchup/" + matchupId;
        }
    }
}
