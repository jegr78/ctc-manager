package org.ctc.admin.controller;

import org.ctc.admin.dto.RaceForm;
import org.ctc.domain.service.RaceAttachmentService;
import org.ctc.domain.service.RaceManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/admin/races")
@RequiredArgsConstructor
public class RaceController {

    private final RaceManagementService raceManagementService;
    private final RaceAttachmentService raceAttachmentService;

    @GetMapping
    public String list(@RequestParam(required = false) UUID matchdayId,
                       @RequestParam(required = false) UUID seasonId,
                       Model model) {
        var data = raceManagementService.getRaceListData(matchdayId, seasonId);
        model.addAttribute("races", data.races());
        model.addAttribute("raceScores", data.raceScores());
        model.addAttribute("matchday", data.matchday());
        model.addAttribute("selectedSeasonId", data.selectedSeasonId());
        model.addAttribute("seasons", data.seasons());
        return "admin/races";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        var data = raceManagementService.getRaceDetailData(id);
        model.addAttribute("race", data.race());
        model.addAttribute("homeTotal", data.homeTotal());
        model.addAttribute("awayTotal", data.awayTotal());
        model.addAttribute("driverTeamMap", data.driverTeamMap());
        model.addAttribute("canGenerateLineup", data.canGenerateLineup());
        model.addAttribute("lineupMissing", data.lineupMissing());
        model.addAttribute("cardsMissing", data.cardsMissing());
        model.addAttribute("lineupExists", data.lineupExists());
        model.addAttribute("canGenerateResults", data.canGenerateResults());
        model.addAttribute("resultsMissing", data.resultsMissing());
        model.addAttribute("resultsExist", data.resultsExist());
        model.addAttribute("canGenerateSettings", data.canGenerateSettings());
        model.addAttribute("settingsMissing", data.settingsMissing());
        model.addAttribute("settingsExist", data.settingsExist());
        model.addAttribute("canGenerateOverlay", data.canGenerateOverlay());
        model.addAttribute("overlayExists", data.overlayExists());
        model.addAttribute("calendarAvailable", data.calendarAvailable());
        model.addAttribute("hasCalendarEvent", data.hasCalendarEvent());
        model.addAttribute("canCreateCalendarEvent", data.canCreateCalendarEvent());
        return "admin/race-detail";
    }

    @GetMapping("/new")
    public String create(@RequestParam(required = false) UUID matchdayId, Model model) {
        var data = raceManagementService.getNewRaceFormData(matchdayId);
        model.addAttribute("raceForm", data.form());
        model.addAttribute("matchdays", data.matchdays());
        model.addAttribute("teams", data.teams());
        model.addAttribute("seasonCars", data.seasonCars());
        model.addAttribute("seasonTracks", data.seasonTracks());
        model.addAttribute("usedCarIds", data.usedCarIds());
        model.addAttribute("usedTrackIds", data.usedTrackIds());
        return "admin/race-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        var data = raceManagementService.getRaceFormData(id);
        model.addAttribute("raceForm", data.form());
        model.addAttribute("matchdays", data.matchdays());
        model.addAttribute("teams", data.teams());
        model.addAttribute("seasonCars", data.seasonCars());
        model.addAttribute("seasonTracks", data.seasonTracks());
        model.addAttribute("usedCarIds", data.usedCarIds());
        model.addAttribute("usedTrackIds", data.usedTrackIds());
        return "admin/race-form";
    }

    @GetMapping("/{id}/results")
    public String results(@PathVariable UUID id, Model model) {
        var data = raceManagementService.getResultsFormData(id);
        model.addAttribute("raceForm", data.form());
        model.addAttribute("race", data.race());
        model.addAttribute("raceScoring", data.raceScoring());
        return "admin/race-results";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute RaceForm form, RedirectAttributes redirectAttributes) {
        var result = raceManagementService.saveRace(form);
        if (!result.success()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.message());
            return "redirect:/admin/races/" + (result.raceId() != null
                    ? result.raceId() + "/edit"
                    : "new?matchdayId=" + result.matchdayId());
        }
        redirectAttributes.addFlashAttribute("successMessage", result.message());
        return "redirect:/admin/races?matchdayId=" + result.matchdayId();
    }

    @PostMapping("/{id}/results")
    public String saveResults(@PathVariable UUID id, @ModelAttribute RaceForm form,
                              RedirectAttributes redirectAttributes) {
        String message = raceManagementService.saveResults(id, form.getResults());
        redirectAttributes.addFlashAttribute("successMessage", message);
        return "redirect:/admin/races/" + id + "/results";
    }

    @PostMapping("/{id}/quick-score")
    public String quickScore(@PathVariable UUID id,
                              @RequestParam int homeScore,
                              @RequestParam int awayScore,
                              @RequestParam(required = false) String returnUrl,
                              RedirectAttributes redirectAttributes) {
        String message = raceManagementService.quickScore(id, homeScore, awayScore);
        redirectAttributes.addFlashAttribute("successMessage", message);
        String safeUrl = (returnUrl != null && returnUrl.startsWith("/") && !returnUrl.startsWith("//"))
                ? returnUrl : "/admin/races";
        return "redirect:" + safeUrl;
    }

    @PostMapping("/{id}/attachments/upload")
    public String uploadAttachment(@PathVariable UUID id,
                                    @RequestParam("file") MultipartFile file,
                                    RedirectAttributes redirectAttributes) {
        try {
            String filename = raceAttachmentService.uploadAttachment(id, file);
            redirectAttributes.addFlashAttribute("successMessage", "File uploaded: " + filename);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/races/" + id;
    }

    @PostMapping("/{id}/attachments/link")
    public String addLink(@PathVariable UUID id,
                           @RequestParam String name,
                           @RequestParam String url,
                           RedirectAttributes redirectAttributes) {
        try {
            String linkName = raceAttachmentService.addLink(id, name, url);
            redirectAttributes.addFlashAttribute("successMessage", "Link added: " + linkName);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/races/" + id;
    }

    @PostMapping("/attachments/{attachmentId}/delete")
    public String deleteAttachment(@PathVariable UUID attachmentId, RedirectAttributes redirectAttributes) {
        UUID raceId = raceAttachmentService.deleteAttachment(attachmentId);
        redirectAttributes.addFlashAttribute("successMessage", "Attachment deleted");
        return "redirect:/admin/races/" + raceId;
    }

    @PostMapping("/{id}/create-calendar-event")
    public String createCalendarEvent(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            raceManagementService.createOrUpdateCalendarEvent(id);
            redirectAttributes.addFlashAttribute("successMessage", "Calendar event saved");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Calendar: " + e.getMessage());
        }
        return "redirect:/admin/races/" + id;
    }

    @PostMapping("/{id}/generate-lineup")
    public String generateLineup(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            raceManagementService.generateLineup(id);
            redirectAttributes.addFlashAttribute("successMessage", "Lineup graphic generated");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/races/" + id;
    }

    @PostMapping("/{id}/generate-results")
    public String generateResults(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            raceManagementService.generateResults(id);
            redirectAttributes.addFlashAttribute("successMessage", "Results graphic generated");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/races/" + id;
    }

    @PostMapping("/{id}/generate-settings")
    public String generateSettings(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            raceManagementService.generateSettings(id);
            redirectAttributes.addFlashAttribute("successMessage", "Settings graphic generated");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/races/" + id;
    }

    @PostMapping("/{id}/generate-overlay")
    public String generateOverlay(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            raceManagementService.generateOverlay(id);
            redirectAttributes.addFlashAttribute("successMessage", "Overlay graphic generated");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/races/" + id;
    }

    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable UUID attachmentId) {
        return raceAttachmentService.downloadAttachment(attachmentId);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        UUID matchdayId = raceManagementService.deleteRace(id);
        redirectAttributes.addFlashAttribute("successMessage", "Race deleted");
        return "redirect:/admin/races?matchdayId=" + matchdayId;
    }

    @GetMapping("/used-selections")
    @ResponseBody
    public Map<String, Set<UUID>> usedSelections(
            @RequestParam UUID seasonId,
            @RequestParam UUID homeTeamId,
            @RequestParam(required = false) UUID excludeRaceId) {
        return raceManagementService.getUsedSelections(seasonId, homeTeamId, excludeRaceId);
    }
}
