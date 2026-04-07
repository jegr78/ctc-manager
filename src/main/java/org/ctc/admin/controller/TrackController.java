package org.ctc.admin.controller;

import org.ctc.admin.dto.TrackForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.service.TrackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/admin/tracks")
@RequiredArgsConstructor
public class TrackController {

    private final TrackService trackService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("tracks", trackService.findAllSorted());
        return "admin/tracks";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("trackForm", new TrackForm());
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
        model.addAttribute("track", track);
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
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "admin/track-form";
        }
        try {
            trackService.save(trackForm.getId(), trackForm.getName(), trackForm.getCountry());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Track saved: " + trackForm.getName());
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/tracks";
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
