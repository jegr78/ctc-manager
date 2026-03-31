package org.ctc.admin.controller;

import org.ctc.admin.dto.TrackForm;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.service.FileStorageService;
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

    private final TrackRepository trackRepository;
    private final RaceRepository raceRepository;
    private final SeasonRepository seasonRepository;
    private final FileStorageService fileStorageService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("tracks", trackRepository.findAllByOrderByNameAsc());
        return "admin/tracks";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("trackForm", new TrackForm());
        return "admin/track-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        var track = trackRepository.findById(id).orElseThrow();
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
            var track = trackRepository.findById(id).orElseThrow();
            if (track.getImageUrl() != null) {
                fileStorageService.delete(track.getImageUrl());
            }
            String url = fileStorageService.storeImage("tracks", id, image);
            track.setImageUrl(url);
            trackRepository.save(track);
            redirectAttributes.addFlashAttribute("successMessage", "Image updated");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Image upload failed: " + e.getMessage());
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
            if (trackForm.getId() != null) {
                var existing = trackRepository.findById(trackForm.getId()).orElseThrow();
                existing.setName(trackForm.getName());
                existing.setCountry(trackForm.getCountry());
                trackRepository.save(existing);
            } else {
                trackRepository.save(new Track(trackForm.getName(), trackForm.getCountry()));
            }
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "A track with this name already exists");
            return "redirect:/admin/tracks" + (trackForm.getId() != null ? "/" + trackForm.getId() + "/edit" : "/new");
        }
        log.info("Saved track: {}", trackForm.getName());
        redirectAttributes.addFlashAttribute("successMessage",
                "Track saved: " + trackForm.getName());
        return "redirect:/admin/tracks";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        var track = trackRepository.findById(id).orElseThrow();
        boolean usedInRace = raceRepository.existsByTrackId(id);
        boolean usedInPool = seasonRepository.findAll().stream()
                .anyMatch(s -> s.getTracks().contains(track));
        if (usedInRace || usedInPool) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Cannot delete: track is used in a race or assigned to a season pool");
            return "redirect:/admin/tracks";
        }
        trackRepository.delete(track);
        log.info("Deleted track: {}", track.getName());
        redirectAttributes.addFlashAttribute("successMessage",
                "Track deleted: " + track.getName());
        return "redirect:/admin/tracks";
    }
}
