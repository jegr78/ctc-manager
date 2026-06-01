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
		int malformed = 0;
		int skippedGuests = 0;
		for (var entry : params.entrySet()) {
			var key = entry.getKey();
			boolean isRoster = key.startsWith("driver_");
			boolean isGuest = key.startsWith("guest_");
			if (!isRoster && !isGuest) {
				continue;
			}
			if (!hasText(entry.getValue())) {
				if (isGuest) {
					skippedGuests++;
				}
				continue;
			}
			try {
				var prefix = isRoster ? "driver_" : "guest_";
				UUID driverId = UUID.fromString(key.substring(prefix.length()));
				UUID teamId = UUID.fromString(entry.getValue());
				(isRoster ? rosterAssignments : guestAssignments).put(driverId, teamId);
			} catch (IllegalArgumentException ex) {
				malformed++;
			}
		}

		int count = raceLineupService.saveLineup(raceId, rosterAssignments, guestAssignments);
		redirectAttributes.addFlashAttribute("successMessage", "Lineup saved: " + count + " drivers assigned");
		if (skippedGuests > 0) {
			redirectAttributes.addFlashAttribute("errorMessage",
					skippedGuests + " guest row(s) skipped — no team selected");
		} else if (malformed > 0) {
			redirectAttributes.addFlashAttribute("errorMessage",
					malformed + " entr(y/ies) skipped — invalid id");
		}
		return "redirect:/admin/races/" + raceId + "/lineup";
	}
}
