package org.ctc.admin.controller;

import static org.springframework.util.StringUtils.hasText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.service.DriverService;
import org.ctc.domain.service.RaceLineupService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/races")
@RequiredArgsConstructor
public class RaceLineupController {

	private final RaceLineupService raceLineupService;
	private final DriverService driverService;

	@GetMapping("/{raceId}/lineup")
	public String lineup(@PathVariable UUID raceId, Model model) {
		var data = raceLineupService.getLineupData(raceId);
		var teamEntries = new ArrayList<RaceLineupService.LineupTeamEntry>();
		if (data.homeEntry() != null) {
			teamEntries.add(data.homeEntry());
		}
		if (data.awayEntry() != null) {
			teamEntries.add(data.awayEntry());
		}

		model.addAttribute("race", data.race());
		model.addAttribute("teamEntries", teamEntries);
		model.addAttribute("driverAssignments", raceLineupService.getDriverAssignments(raceId));
		model.addAttribute("guestLineups", raceLineupService.getGuestLineups(raceId));
		model.addAttribute("allDrivers", driverService.findAll());
		return "admin/race-lineup";
	}

	@PostMapping("/{raceId}/lineup")
	public String saveLineup(@PathVariable UUID raceId,
	                         @RequestParam Map<String, String> params,
	                         RedirectAttributes redirectAttributes) {
		var rosterAssignments = new HashMap<UUID, UUID>();
		var guestAssignments = new HashMap<UUID, UUID>();
		for (var entry : params.entrySet()) {
			var key = entry.getKey();
			if (!hasText(entry.getValue())) {
				continue;
			}
			if (key.startsWith("driver_")) {
				UUID driverId = UUID.fromString(key.substring("driver_".length()));
				rosterAssignments.put(driverId, UUID.fromString(entry.getValue()));
			} else if (key.startsWith("guest_")) {
				UUID driverId = UUID.fromString(key.substring("guest_".length()));
				guestAssignments.put(driverId, UUID.fromString(entry.getValue()));
			}
		}

		int count = raceLineupService.saveLineup(raceId, rosterAssignments, guestAssignments);
		redirectAttributes.addFlashAttribute("successMessage", "Lineup saved: " + count + " drivers assigned");
		return "redirect:/admin/races/" + raceId + "/lineup";
	}
}
