package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.TrackForm;
import org.ctc.admin.dto.TrackEnvironmentForm;
import org.ctc.domain.service.TrackEnvironmentService;
import org.ctc.domain.model.TrackRainCapability;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.service.TrackService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/tracks")
@RequiredArgsConstructor
public class TrackController {

	private final TrackService trackService;
    private final TrackEnvironmentService environments;

	@GetMapping
	public String list(Model model) {
		model.addAttribute("tracks", trackService.findAllSorted());
		return "admin/tracks";
	}

	@GetMapping("/new")
	public String create(Model model) {
		model.addAttribute("trackForm", new TrackForm());
        model.addAttribute("rainCapabilities", TrackRainCapability.values());
        model.addAttribute("pageTitle", "New Track");
		return "admin/track-form";
	}

	@GetMapping("/{id}/edit")
	public String edit(@PathVariable UUID id, Model model) {
		var track = trackService.findById(id);
		var form = new TrackForm();
		form.setId(track.getId());
		form.setName(track.getName());
		form.setCountry(track.getCountry());
		model.addAttribute("trackForm", form);
        model.addAttribute("pageTitle", "Edit Track");
		model.addAttribute("track", track);
        prepareEnvironment(id, model);
		return "admin/track-form";
	}

	@PostMapping("/{id}/image")
	public String uploadImage(@PathVariable UUID id, @RequestParam MultipartFile image,
	                          RedirectAttributes redirectAttributes) {
		try {
			trackService.uploadImage(id, image);
			redirectAttributes.addFlashAttribute("successMessage", "Image updated");
		} catch (BusinessRuleException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/tracks/" + id + "/edit";
	}

	@PostMapping("/save")
	public String save(@Valid @ModelAttribute TrackForm trackForm, BindingResult result,
	                   Model model, RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
            model.addAttribute("rainCapabilities", TrackRainCapability.values());
            if (trackForm.getId() != null) prepareEnvironment(trackForm.getId(), model);
            model.addAttribute("pageTitle", trackForm.getId() == null ? "New Track" : "Edit Track");
            if (trackForm.getId() != null) model.addAttribute("track", trackService.findById(trackForm.getId()));
			return "admin/track-form";
		}
		try {
			trackService.save(trackForm.getId(), trackForm.getName(), trackForm.getCountry(), trackForm.getTimeOfDayText(), trackForm.getRainCapability());
			redirectAttributes.addFlashAttribute("successMessage",
					"Track saved: " + trackForm.getName());
		} catch (BusinessRuleException e) {
			result.reject("track", e.getMessage());
            model.addAttribute("rainCapabilities", TrackRainCapability.values());
            model.addAttribute("pageTitle", trackForm.getId() == null ? "New Track" : "Edit Track");
            if (trackForm.getId() != null) { model.addAttribute("track", trackService.findById(trackForm.getId())); prepareEnvironment(trackForm.getId(), model); }
            return "admin/track-form";
		}
		return "redirect:/admin/tracks";
	}

    private void prepareEnvironment(UUID id, Model model) {
        var view = environments.view(id);
        model.addAttribute("environment", view);
        model.addAttribute("rainCapabilities", TrackRainCapability.values());
        if (!model.containsAttribute("environmentForm")) {
            var form = new TrackEnvironmentForm(); form.setTimeOfDayText(view.timeOfDayText());
            form.setRainCapability(view.values().rainCapability()); form.setExpectedVersion(view.version());
            form.setExpectedGeneration(view.generation()); model.addAttribute("environmentForm", form);
        }
    }

    @PostMapping("/{id}/environment")
    public String saveEnvironment(@PathVariable UUID id, @Valid @ModelAttribute("environmentForm") TrackEnvironmentForm form,
                                  BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                environments.save(id, form.getTimeOfDayText(), form.getRainCapability(), form.getExpectedVersion(), form.getExpectedGeneration());
                redirect.addFlashAttribute("successMessage", "Track environment saved");
                return "redirect:/admin/tracks/" + id + "/edit#track-environment";
            } catch (BusinessRuleException failure) { result.reject("environment", failure.getMessage()); }
        }
        return edit(id, model);
    }

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			trackService.delete(id);
			redirectAttributes.addFlashAttribute("successMessage", "Track deleted");
		} catch (BusinessRuleException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/tracks";
	}
}
