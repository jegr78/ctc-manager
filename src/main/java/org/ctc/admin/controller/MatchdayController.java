package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.CreateMatchdayRequest;
import org.ctc.admin.dto.MatchdayForm;
import org.ctc.admin.dto.MatchdayPairingsForm;
import org.ctc.admin.dto.MatchPreviewPreFlightResult;
import org.ctc.admin.service.MatchResultsGraphicService;
import org.ctc.admin.service.MatchdayOverviewGraphicService;
import org.ctc.admin.service.MatchdayResultsGraphicService;
import org.ctc.admin.service.MatchdayScheduleGraphicService;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordApiExceptionMapper;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.ctc.discord.service.DiscordPostService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.service.MatchService;
import org.ctc.domain.service.MatchdayService;
import org.ctc.domain.service.StandingsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/matchdays")
@RequiredArgsConstructor
public class MatchdayController {

    private final MatchdayService matchdayService;
    private final MatchdayOverviewGraphicService overviewGraphicService;
    private final MatchdayScheduleGraphicService scheduleGraphicService;
    private final MatchdayResultsGraphicService resultsGraphicService;
    private final MatchResultsGraphicService matchResultsGraphicService;
    private final MatchService matchService;
    private final DiscordPostService discordPostService;
    private final DiscordPostRepository discordPostRepository;
    private final DiscordGlobalConfigService discordGlobalConfigService;
    private final StandingsService standingsService;

    @GetMapping
    public String list(@RequestParam(required = false) UUID seasonId, Model model) {
        var data = matchdayService.getMatchdayList(seasonId);
        model.addAttribute("matchdays", data.matchdays());
        if (data.selectedSeasonId() != null) {
            model.addAttribute("selectedSeasonId", data.selectedSeasonId());
        }
        model.addAttribute("seasons", data.seasons());
        return "admin/matchdays";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        var data = matchdayService.getMatchdayDetail(id);
        var matchday = data.matchday();
        model.addAttribute("matchday", matchday);
        model.addAttribute("lineupsByTeam", data.lineupsByTeam());
        model.addAttribute("hasMatches", data.hasMatches());
        model.addAttribute("hasSchedule", data.hasSchedule());
        model.addAttribute("scheduleMissingCount", data.scheduleMissingCount());
        model.addAttribute("hasResults", data.hasResults());
        model.addAttribute("pageTitle", "Matchday: " + matchday.getLabel());
        populateMatchdayDiscordModel(model, matchday);
        return "admin/matchday-detail";
    }

    private void populateMatchdayDiscordModel(Model model, Matchday matchday) {
        DiscordGlobalConfig config = discordGlobalConfigService.getOrInitialize();
        String webhookUrl = config.getRaceResultsForumWebhookUrl();
        boolean threadLinked = matchday.getSeason().getDiscordRaceResultsThreadId() != null
                && !matchday.getSeason().getDiscordRaceResultsThreadId().isBlank();
        boolean webhookConfigured = webhookUrl != null && !webhookUrl.isBlank();
        boolean matchdayDiscordActive = threadLinked && webhookConfigured;

        MatchPreviewPreFlightResult resultsPreFlight = discordPostService.canPostMatchdayResults(matchday, config);
        MatchPreviewPreFlightResult rankingsPreFlight = discordPostService.canPostPowerRankings(matchday, config);

        DiscordPost matchdayOverviewPost = null;
        DiscordPost powerRankingsPost = null;
        if (matchdayDiscordActive) {
            String channelId = discordPostService.resolveAnnouncementChannelId(webhookUrl);
            matchdayOverviewPost = discordPostRepository.findByChannelIdAndPostTypeAndMatchdayId(
                    channelId, DiscordPostType.MATCHDAY_OVERVIEW, matchday.getId()).orElse(null);
            powerRankingsPost = discordPostRepository.findByChannelIdAndPostTypeAndMatchdayId(
                    channelId, DiscordPostType.POWER_RANKINGS, matchday.getId()).orElse(null);
        }

        boolean matchdayResultsStale = matchdayOverviewPost != null
                && standingsService.isMatchdayResultsStale(matchday, matchdayOverviewPost);
        boolean powerRankingsStale = powerRankingsPost != null
                && standingsService.isPowerRankingsStale(matchday.getSeason(), powerRankingsPost);

        model.addAttribute("matchdayDiscordActive", matchdayDiscordActive);
        model.addAttribute("canPostMatchdayResults", resultsPreFlight.canPost());
        model.addAttribute("canPostPowerRankings", rankingsPreFlight.canPost());
        model.addAttribute("matchdayResultsDisabledReason", resultsPreFlight.disabledReason());
        model.addAttribute("powerRankingsDisabledReason", rankingsPreFlight.disabledReason());
        model.addAttribute("matchdayOverviewPost", matchdayOverviewPost);
        model.addAttribute("powerRankingsPost", powerRankingsPost);
        model.addAttribute("matchdayResultsStale", matchdayResultsStale);
        model.addAttribute("powerRankingsStale", powerRankingsStale);

        String announcementWebhookUrl = config.getAnnouncementWebhookUrl();
        boolean matchdayAnnouncementActive = announcementWebhookUrl != null
                && !announcementWebhookUrl.isBlank();

        MatchPreviewPreFlightResult pairingsPreFlight =
                discordPostService.canPostMatchdayPairings(matchday, config);
        MatchPreviewPreFlightResult schedulePreFlight =
                discordPostService.canPostMatchdaySchedule(matchday, config);

        String announcementChannelId = matchdayAnnouncementActive
                ? discordPostService.resolveAnnouncementChannelId(announcementWebhookUrl)
                : null;

        DiscordPost matchdayPairingsPost = matchdayAnnouncementActive
                ? discordPostRepository.findByChannelIdAndPostTypeAndMatchdayId(
                        announcementChannelId, DiscordPostType.MATCHDAY_PAIRINGS, matchday.getId())
                        .orElse(null)
                : null;
        boolean matchdayPairingsStale = matchdayPairingsPost != null
                && standingsService.isMatchdayPairingsStale(matchday, matchdayPairingsPost);

        DiscordPost matchdaySchedulePost = matchdayAnnouncementActive
                ? discordPostRepository.findByChannelIdAndPostTypeAndMatchdayId(
                        announcementChannelId, DiscordPostType.MATCHDAY_SCHEDULE, matchday.getId())
                        .orElse(null)
                : null;
        boolean matchdayScheduleStale = matchdaySchedulePost != null
                && standingsService.isMatchdayScheduleStale(matchday, matchdaySchedulePost);

        model.addAttribute("matchdayAnnouncementActive", matchdayAnnouncementActive);
        model.addAttribute("canPostMatchdayPairings", pairingsPreFlight.canPost());
        model.addAttribute("matchdayPairingsDisabledReason", pairingsPreFlight.disabledReason());
        model.addAttribute("matchdayPairingsPost", matchdayPairingsPost);
        model.addAttribute("matchdayPairingsStale", matchdayPairingsStale);
        model.addAttribute("canPostMatchdaySchedule", schedulePreFlight.canPost());
        model.addAttribute("matchdayScheduleDisabledReason", schedulePreFlight.disabledReason());
        model.addAttribute("matchdaySchedulePost", matchdaySchedulePost);
        model.addAttribute("matchdayScheduleStale", matchdayScheduleStale);
    }

    @PostMapping("/{id}/post-matchday-results")
    public String postMatchdayResults(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            Matchday matchday = matchdayService.getMatchdayDetail(id).matchday();
            discordPostService.postMatchdayResults(matchday);
            redirectAttributes.addFlashAttribute("successMessage", "Match day results posted.");
        } catch (BusinessRuleException e) {
            applyErrorFlash(redirectAttributes, e, "Post match day results");
        } catch (DiscordApiException e) {
            applyErrorFlash(redirectAttributes, e, "Post match day results");
        }
        return "redirect:/admin/matchdays/" + id;
    }

    @PostMapping("/{id}/post-power-rankings")
    public String postPowerRankings(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            Matchday matchday = matchdayService.getMatchdayDetail(id).matchday();
            discordPostService.postPowerRankings(matchday);
            redirectAttributes.addFlashAttribute("successMessage", "Power rankings posted.");
        } catch (BusinessRuleException e) {
            applyErrorFlash(redirectAttributes, e, "Post power rankings");
        } catch (DiscordApiException e) {
            applyErrorFlash(redirectAttributes, e, "Post power rankings");
        }
        return "redirect:/admin/matchdays/" + id;
    }

    @GetMapping("/{id}/edit-pairings")
    public String editPairings(@PathVariable UUID id, Model model) {
        var matchday = matchdayService.getMatchdayDetail(id).matchday();
        var form = new MatchdayPairingsForm();
        form.setId(matchday.getId());
        form.setPickDeadline(matchday.getPickDeadline());
        form.setScheduledWeekend(matchday.getScheduledWeekend());
        model.addAttribute("form", form);
        model.addAttribute("matchday", matchday);
        return "admin/matchday-pairings-form";
    }

    @PostMapping("/{id}/save-pairings")
    public String savePairings(@PathVariable UUID id,
                                @Valid @ModelAttribute("form") MatchdayPairingsForm form,
                                BindingResult result,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        if (result.hasErrors()) {
            model.addAttribute("matchday", matchdayService.getMatchdayDetail(id).matchday());
            return "admin/matchday-pairings-form";
        }
        matchdayService.savePairings(id, form.getPickDeadline(), form.getScheduledWeekend());
        redirectAttributes.addFlashAttribute("successMessage", "Pairings saved.");
        return "redirect:/admin/matchdays/" + id;
    }

    @PostMapping("/{id}/post-matchday-pairings")
    public String postMatchdayPairings(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            Matchday matchday = matchdayService.getMatchdayDetail(id).matchday();
            discordPostService.postMatchdayPairings(matchday);
            redirectAttributes.addFlashAttribute("successMessage", "Matchday Pairings posted.");
        } catch (BusinessRuleException e) {
            applyErrorFlash(redirectAttributes, e, "Post Matchday Pairings");
        } catch (DiscordApiException e) {
            applyErrorFlash(redirectAttributes, e, "Post Matchday Pairings");
        }
        return "redirect:/admin/matchdays/" + id;
    }

    @PostMapping("/{id}/post-matchday-schedule")
    public String postMatchdaySchedule(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            Matchday matchday = matchdayService.getMatchdayDetail(id).matchday();
            discordPostService.postMatchdaySchedule(matchday);
            redirectAttributes.addFlashAttribute("successMessage", "Matchday Schedule posted.");
        } catch (BusinessRuleException e) {
            applyErrorFlash(redirectAttributes, e, "Post Matchday Schedule");
        } catch (DiscordApiException e) {
            applyErrorFlash(redirectAttributes, e, "Post Matchday Schedule");
        }
        return "redirect:/admin/matchdays/" + id;
    }

    private void applyErrorFlash(RedirectAttributes ra, DiscordApiException e, String action) {
        String message = switch (e.category()) {
            case TRANSIENT -> DiscordApiExceptionMapper.TRANSIENT_MESSAGE;
            case AUTH -> DiscordApiExceptionMapper.AUTH_MESSAGE;
            case MISSING_PERMISSIONS -> DiscordApiExceptionMapper.MISSING_PERMISSIONS_MESSAGE;
            case NOT_FOUND -> DiscordApiExceptionMapper.NOT_FOUND_MESSAGE;
            case CATEGORY_FULL -> DiscordApiExceptionMapper.CATEGORY_FULL_MESSAGE;
        };
        String category = e.category().name().toLowerCase().replace('_', '-');
        log.warn("{} failed: category={}, exception={}", action, category, e.getClass().getSimpleName());
        ra.addFlashAttribute("errorMessage", message);
        ra.addFlashAttribute("errorCategory", category);
    }

    private void applyErrorFlash(RedirectAttributes ra, BusinessRuleException e, String action) {
        log.warn("{} failed: category=data-incomplete, message={}", action, e.getMessage());
        ra.addFlashAttribute("errorMessage", e.getMessage());
        ra.addFlashAttribute("errorCategory", "data-incomplete");
    }

    @GetMapping("/new")
    public String create(@RequestParam(required = false) UUID seasonId, Model model) {
        var form = new MatchdayForm();
        if (seasonId != null) {
            form.setSeasonId(seasonId);
            model.addAttribute("season", matchdayService.findSeasonById(seasonId));
        }
        model.addAttribute("form", form);
        model.addAttribute("seasons", matchdayService.getAllSeasons());
        return "admin/matchday-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        var matchday = matchdayService.getMatchdayDetail(id).matchday();
        var form = new MatchdayForm();
        form.setId(matchday.getId());
        form.setLabel(matchday.getLabel());
        form.setSortIndex(matchday.getSortIndex());
        form.setSeasonId(matchday.getSeason().getId());
        model.addAttribute("form", form);
        model.addAttribute("season", matchday.getSeason());
        model.addAttribute("seasons", matchdayService.getAllSeasons());
        return "admin/matchday-form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("form") MatchdayForm form,
                       BindingResult result,
                       RedirectAttributes redirectAttributes,
                       Model model) {
        if (result.hasErrors()) {
            model.addAttribute("seasons", matchdayService.getAllSeasons());
            if (form.getSeasonId() != null) {
                model.addAttribute("season", matchdayService.findSeasonById(form.getSeasonId()));
            }
            return "admin/matchday-form";
        }
        var saved = matchdayService.saveMatchday(
                form.getLabel(), form.getSortIndex(), form.getSeasonId(), form.getId());
        redirectAttributes.addFlashAttribute("successMessage", "Matchday saved: " + saved.getLabel());
        String redirectUrl = "/admin/matchdays";
        if (form.getSeasonId() != null) {
            redirectUrl += "?seasonId=" + form.getSeasonId();
        }
        return "redirect:" + redirectUrl;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        var seasonId = matchdayService.deleteMatchday(id);
        redirectAttributes.addFlashAttribute("successMessage", "Matchday deleted");
        return "redirect:/admin/matchdays?seasonId=" + seasonId;
    }

    @PostMapping("/{id}/download-overview")
    public ResponseEntity<byte[]> downloadOverview(@PathVariable UUID id) {
        try {
            var matchday = matchdayService.getMatchdayDetail(id).matchday();
            byte[] png = overviewGraphicService.generateOverview(matchday);
            return buildPngResponse(png, matchday.getLabel(), "overview");
        } catch (IOException | RuntimeException e) {
            log.error("Failed to generate overview graphic for matchday {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/download-schedule")
    public ResponseEntity<byte[]> downloadSchedule(@PathVariable UUID id) {
        try {
            var matchday = matchdayService.getMatchdayDetail(id).matchday();
            byte[] png = scheduleGraphicService.generateSchedule(matchday);
            return buildPngResponse(png, matchday.getLabel(), "schedule");
        } catch (IOException | RuntimeException e) {
            log.error("Failed to generate schedule graphic for matchday {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/download-results")
    public ResponseEntity<byte[]> downloadResults(@PathVariable UUID id) {
        try {
            var matchday = matchdayService.getMatchdayDetail(id).matchday();
            byte[] png = resultsGraphicService.generateResults(matchday);
            return buildPngResponse(png, matchday.getLabel(), "results");
        } catch (IOException | RuntimeException e) {
            log.error("Failed to generate results graphic for matchday {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{matchdayId}/matches/{matchId}/download-match-results")
    public ResponseEntity<byte[]> downloadMatchResults(@PathVariable UUID matchdayId, @PathVariable UUID matchId) {
        try {
            var match = matchService.getMatch(matchId);
            byte[] png = matchResultsGraphicService.generateMatchResults(match);
            String label = match.getHomeTeam().getShortName() + "-vs-" + match.getAwayTeam().getShortName();
            return buildPngResponse(png, label, "match-results");
        } catch (IOException | RuntimeException e) {
            log.error("Failed to generate match results graphic for match {}", matchId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private ResponseEntity<byte[]> buildPngResponse(byte[] png, String matchdayLabel, String type) {
        String filename = matchdayLabel.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + type + ".png";
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(png);
    }

    @GetMapping("/by-season")
    @ResponseBody
    public List<MatchdayService.MatchdayData> matchdaysBySeason(@RequestParam UUID seasonId) {
        return matchdayService.getMatchdaysBySeason(seasonId);
    }

    @PostMapping("/create-inline")
    @ResponseBody
    public ResponseEntity<MatchdayService.MatchdayData> createInline(@Valid @RequestBody CreateMatchdayRequest request) {
        var dto = matchdayService.createInline(request.seasonId(), request.label());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}
