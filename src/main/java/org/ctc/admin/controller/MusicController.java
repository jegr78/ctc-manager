package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.MusicTrackForm;
import org.ctc.admin.dto.MusicTrackView;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.service.MusicLibraryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/music")
@RequiredArgsConstructor
public class MusicController {

    private final MusicLibraryService library;

    @GetMapping
    public String list(Model model) {
        if (!model.containsAttribute("musicTrackForm")) {
            model.addAttribute("musicTrackForm", new MusicTrackForm());
        }
        return page(model);
    }

    @PostMapping
    public String upload(@Valid @ModelAttribute("musicTrackForm") MusicTrackForm form, BindingResult binding,
                         @RequestParam(name = "file", required = false) MultipartFile file,
                         Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return page(model);
        }
        try {
            var track = library.upload(file, form.getTitle(), form.getArtist(), form.getCredit());
            redirect.addFlashAttribute("successMessage", "Added \"" + track.getTitle() + "\".");
            return "redirect:/admin/music";
        } catch (BusinessRuleException e) {
            model.addAttribute("fileError", e.getMessage());
            return page(model);
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
        library.delete(id);
        redirect.addFlashAttribute("successMessage", "Music track deleted.");
        return "redirect:/admin/music";
    }

    private String page(Model model) {
        model.addAttribute("musicTracks", library.list().stream().map(MusicTrackView::of).toList());
        model.addAttribute("maxUploadMegabytes", MusicLibraryService.MAX_UPLOAD_BYTES / (1024 * 1024));
        return "admin/music";
    }
}
