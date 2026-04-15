package org.ctc.admin.controller;

import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.RaceForm;
import org.ctc.admin.dto.RaceResultForm;
import org.ctc.admin.service.RaceGraphicService;
import org.ctc.domain.service.*;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/admin/races")
@RequiredArgsConstructor
public class RaceController {

	private final RaceService raceService;
	private final RaceFormDataService raceFormDataService;
	private final RaceCalendarService raceCalendarService;
	private final RaceAttachmentService raceAttachmentService;
	private final RaceGraphicService raceGraphicService;

	@GetMapping
	public String list(@RequestParam(required = false) UUID matchdayId,
	                   @RequestParam(required = false) UUID seasonId,
	                   Model model) {
		var data = raceService.getRaceListData(matchdayId, seasonId);
		model.addAttribute("races", data.races());
		model.addAttribute("raceScores", data.raceScores());
		model.addAttribute("matchday", data.matchday());
		model.addAttribute("selectedSeasonId", data.selectedSeasonId());
		model.addAttribute("seasons", data.seasons());
		return "admin/races";
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable UUID id, Model model) {
		var data = raceService.getRaceDetailData(id);
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
		var data = raceFormDataService.getNewRaceFormData(matchdayId);
		model.addAttribute("raceForm", toRaceForm(data.data()));
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
		var data = raceFormDataService.getRaceFormData(id);
		model.addAttribute("raceForm", toRaceForm(data.data()));
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
		var data = raceFormDataService.getResultsFormData(id);
		model.addAttribute("raceForm", toRaceForm(data.data()));
		model.addAttribute("race", data.race());
		model.addAttribute("raceScoring", data.raceScoring());
		return "admin/race-results";
	}

	@PostMapping("/save")
	public String save(@ModelAttribute RaceForm form, RedirectAttributes redirectAttributes) {
		var result = raceService.saveRace(
				form.getId(), form.getMatchdayId(), form.getHomeTeamId(), form.getAwayTeamId(),
				form.getTrackId(), form.getCarId(), form.getDateTime(),
				form.getNumberOfLaps(), form.getTyreWearMultiplier(),
				form.getFuelConsumptionMultiplier(), form.getRefuelingSpeed(),
				form.getInitialFuel(), form.getNumberOfRequiredPitStops(),
				form.getTimeProgressionMultiplier(), form.getWeather(),
				form.getTimeOfDay(), form.getAvailableTyres(), form.getMandatoryTyres());
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
		var resultData = form.getResults().stream()
				.map(rf -> new RaceService.RaceResultData(
						rf.getDriverId(), rf.getDriverPsnId(), rf.getTeamShortName(),
						rf.getPosition(), rf.getQualiPosition(), rf.isFastestLap()))
				.toList();
		String message = raceService.saveResults(id, resultData);
		redirectAttributes.addFlashAttribute("successMessage", message);
		return "redirect:/admin/races/" + id + "/results";
	}

	@PostMapping("/{id}/quick-score")
	public String quickScore(@PathVariable UUID id,
	                         @RequestParam int homeScore,
	                         @RequestParam int awayScore,
	                         @RequestParam(required = false) String returnUrl,
	                         RedirectAttributes redirectAttributes) {
		String message = raceService.quickScore(id, homeScore, awayScore);
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
		} catch (RuntimeException e) {
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
			raceCalendarService.createOrUpdateCalendarEvent(id);
			redirectAttributes.addFlashAttribute("successMessage", "Calendar event saved");
		} catch (IOException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("errorMessage", "Calendar: " + e.getMessage());
		}
		return "redirect:/admin/races/" + id;
	}

	@PostMapping("/{id}/generate-lineup")
	public String generateLineup(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			raceGraphicService.generateLineup(id);
			redirectAttributes.addFlashAttribute("successMessage", "Lineup graphic generated");
		} catch (RuntimeException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/races/" + id;
	}

	@PostMapping("/{id}/generate-results")
	public String generateResults(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			raceGraphicService.generateResults(id);
			redirectAttributes.addFlashAttribute("successMessage", "Results graphic generated");
		} catch (RuntimeException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/races/" + id;
	}

	@PostMapping("/{id}/generate-settings")
	public String generateSettings(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			raceGraphicService.generateSettings(id);
			redirectAttributes.addFlashAttribute("successMessage", "Settings graphic generated");
		} catch (RuntimeException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/races/" + id;
	}

	@PostMapping("/{id}/generate-overlay")
	public String generateOverlay(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			raceGraphicService.generateOverlay(id);
			redirectAttributes.addFlashAttribute("successMessage", "Overlay graphic generated");
		} catch (RuntimeException e) {
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
		UUID matchdayId = raceService.deleteRace(id);
		redirectAttributes.addFlashAttribute("successMessage", "Race deleted");
		return "redirect:/admin/races?matchdayId=" + matchdayId;
	}

	@GetMapping("/used-selections")
	@ResponseBody
	public Map<String, Set<UUID>> usedSelections(
			@RequestParam UUID seasonId,
			@RequestParam UUID homeTeamId,
			@RequestParam(required = false) UUID excludeRaceId) {
		return raceFormDataService.getUsedSelections(seasonId, homeTeamId, excludeRaceId);
	}

	// --- Private helper: Map domain RaceData to admin RaceForm for Thymeleaf templates ---

	private RaceForm toRaceForm(RaceService.RaceData data) {
		var form = new RaceForm();
		form.setId(data.id());
		form.setMatchdayId(data.matchdayId());
		form.setHomeTeamId(data.homeTeamId());
		form.setAwayTeamId(data.awayTeamId());
		form.setTrackId(data.trackId());
		form.setCarId(data.carId());
		form.setDateTime(data.dateTime());
		form.setNumberOfLaps(data.numberOfLaps());
		form.setTyreWearMultiplier(data.tyreWearMultiplier());
		form.setFuelConsumptionMultiplier(data.fuelConsumptionMultiplier());
		form.setRefuelingSpeed(data.refuelingSpeed());
		form.setInitialFuel(data.initialFuel());
		form.setNumberOfRequiredPitStops(data.numberOfRequiredPitStops());
		form.setTimeProgressionMultiplier(data.timeProgressionMultiplier());
		form.setWeather(data.weather());
		form.setTimeOfDay(data.timeOfDay());
		form.setAvailableTyres(data.availableTyres());
		form.setMandatoryTyres(data.mandatoryTyres());
		for (var rd : data.results()) {
			var rf = new RaceResultForm();
			rf.setDriverId(rd.driverId());
			rf.setDriverPsnId(rd.driverPsnId());
			rf.setTeamShortName(rd.teamShortName());
			rf.setPosition(rd.position());
			rf.setQualiPosition(rd.qualiPosition());
			rf.setFastestLap(rd.fastestLap());
			form.getResults().add(rf);
		}
		return form;
	}
}
