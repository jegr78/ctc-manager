package org.ctc.admin.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
		return "admin/race-lineup";
	}

	@PostMapping("/{raceId}/lineup")
	public String saveLineup(@PathVariable UUID raceId,
	                         @RequestParam Map<String, String> params,
	                         RedirectAttributes redirectAttributes) {
		var driverTeamAssignments = new HashMap<UUID, UUID>();
		for (var entry : params.entrySet()) {
			if (!entry.getKey().startsWith("driver_") || entry.getValue().isBlank()) {
				continue;
			}
			UUID driverId = UUID.fromString(entry.getKey().substring("driver_".length()));
			UUID teamId = UUID.fromString(entry.getValue());
			driverTeamAssignments.put(driverId, teamId);
		}

		int count = raceLineupService.saveLineup(raceId, driverTeamAssignments);
		redirectAttributes.addFlashAttribute("successMessage", "Lineup saved: " + count + " drivers assigned");
		return "redirect:/admin/races/" + raceId + "/lineup";
	}
}
