package de.ctc.admin.controller;

import de.ctc.domain.model.Driver;
import de.ctc.domain.model.SeasonDriver;
import de.ctc.domain.repository.DriverRepository;
import de.ctc.domain.repository.SeasonDriverRepository;
import de.ctc.domain.repository.SeasonRepository;
import de.ctc.domain.repository.TeamRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/admin/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverRepository driverRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("drivers", driverRepository.findAll());
        return "admin/drivers";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        var driver = driverRepository.findById(id).orElseThrow();
        model.addAttribute("driver", driver);
        return "admin/driver-detail";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("driver", new Driver());
        return "admin/driver-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        model.addAttribute("driver", driverRepository.findById(id).orElseThrow());
        model.addAttribute("seasonDrivers", seasonDriverRepository.findByDriverId(id));
        model.addAttribute("seasons", seasonRepository.findAll());
        model.addAttribute("teams", teamRepository.findAll());
        return "admin/driver-form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute Driver driver, BindingResult result,
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "admin/driver-form";
        }
        driverRepository.save(driver);
        log.info("Saved driver: {}", driver.getPsnId());
        redirectAttributes.addFlashAttribute("successMessage", "Driver saved: " + driver.getPsnId());
        return "redirect:/admin/drivers";
    }

    @PostMapping("/{id}/assign")
    public String assignToSeason(@PathVariable UUID id,
                                 @RequestParam UUID seasonId,
                                 @RequestParam UUID teamId,
                                 RedirectAttributes redirectAttributes) {
        var driver = driverRepository.findById(id).orElseThrow();
        var season = seasonRepository.findById(seasonId).orElseThrow();
        var team = teamRepository.findById(teamId).orElseThrow();

        var existing = seasonDriverRepository.findBySeasonIdAndDriverId(seasonId, id);
        if (existing.isPresent()) {
            var sd = existing.get();
            sd.setTeam(team);
            seasonDriverRepository.save(sd);
        } else {
            seasonDriverRepository.save(new SeasonDriver(season, driver, team));
        }

        log.info("Assigned driver {} to team {} in season {}", driver.getPsnId(), team.getShortName(), season.getName());
        redirectAttributes.addFlashAttribute("successMessage",
                driver.getPsnId() + " → " + team.getShortName() + " (" + season.getName() + ")");
        return "redirect:/admin/drivers/" + id + "/edit";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        var driver = driverRepository.findById(id).orElseThrow();
        driverRepository.delete(driver);
        log.info("Deleted driver: {}", driver.getPsnId());
        redirectAttributes.addFlashAttribute("successMessage", "Driver deleted: " + driver.getPsnId());
        return "redirect:/admin/drivers";
    }
}
