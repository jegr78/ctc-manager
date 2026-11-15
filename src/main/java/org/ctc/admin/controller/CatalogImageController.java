package org.ctc.admin.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.CarImageKind;
import org.ctc.domain.model.TrackImageKind;
import org.ctc.domain.service.CatalogImageService;
import org.ctc.gt7sync.Gt7CarShotService;
import org.ctc.gt7sync.GtEngineTrackMapService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Gallery actions of the car and track editors; every action returns to the editor's image section. */
@Controller
@RequiredArgsConstructor
public class CatalogImageController {
    private final CatalogImageService gallery;
    private final Gt7CarShotService gt7Shots;
    private final GtEngineTrackMapService trackMaps;

    @PostMapping("/admin/cars/{id}/images")
    public String uploadCarImages(@PathVariable UUID id, @RequestParam(name = "files", required = false) List<MultipartFile> files,
                                  @RequestParam(required = false) String kind, @RequestParam(required = false) String label,
                                  RedirectAttributes redirect) {
        redirect.addFlashAttribute("galleryKind", kind);
        redirect.addFlashAttribute("galleryLabel", label);
        return run(redirect, carEditor(id) + "#car-images", () -> {
            int added = gallery.uploadCarImages(id, files, parse(CarImageKind.class, kind), label);
            return added == 1 ? "1 image added." : added + " images added.";
        });
    }

    @PostMapping("/admin/cars/{id}/images/gt7")
    public String loadGt7CarImages(@PathVariable UUID id, RedirectAttributes redirect) {
        return run(redirect, carEditor(id) + "#car-images", () -> gt7Shots.load(id).message());
    }

    @PostMapping("/admin/cars/{id}/images/{imageId}/delete")
    public String deleteCarImage(@PathVariable UUID id, @PathVariable UUID imageId, RedirectAttributes redirect) {
        return run(redirect, carEditor(id) + "#car-images", () -> {
            gallery.deleteCarImage(id, imageId);
            return "Image deleted.";
        });
    }

    @PostMapping("/admin/cars/{id}/images/{imageId}/move")
    public String moveCarImage(@PathVariable UUID id, @PathVariable UUID imageId, @RequestParam String direction, RedirectAttributes redirect) {
        boolean up = "up".equals(direction);
        return run(redirect, carEditor(id) + "#image-" + imageId + (up ? "-up" : "-down"),
                () -> "Image moved to position " + gallery.moveCarImage(id, imageId, up) + ".");
    }

    @PostMapping("/admin/tracks/{id}/images")
    public String uploadTrackImages(@PathVariable UUID id, @RequestParam(name = "files", required = false) List<MultipartFile> files,
                                    @RequestParam(required = false) String kind, @RequestParam(required = false) String label,
                                    RedirectAttributes redirect) {
        redirect.addFlashAttribute("galleryKind", kind);
        redirect.addFlashAttribute("galleryLabel", label);
        return run(redirect, trackEditor(id) + "#track-images", () -> {
            int added = gallery.uploadTrackImages(id, files, parse(TrackImageKind.class, kind), label);
            return added == 1 ? "1 image added." : added + " images added.";
        });
    }

    @PostMapping("/admin/tracks/{id}/images/gt-engine")
    public String loadGtEngineMap(@PathVariable UUID id, RedirectAttributes redirect) {
        return run(redirect, trackEditor(id) + "#track-images", () -> trackMaps.load(id).message());
    }

    @PostMapping("/admin/tracks/{id}/images/{imageId}/delete")
    public String deleteTrackImage(@PathVariable UUID id, @PathVariable UUID imageId, RedirectAttributes redirect) {
        return run(redirect, trackEditor(id) + "#track-images", () -> {
            gallery.deleteTrackImage(id, imageId);
            return "Image deleted.";
        });
    }

    @PostMapping("/admin/tracks/{id}/images/{imageId}/move")
    public String moveTrackImage(@PathVariable UUID id, @PathVariable UUID imageId, @RequestParam String direction, RedirectAttributes redirect) {
        boolean up = "up".equals(direction);
        return run(redirect, trackEditor(id) + "#image-" + imageId + (up ? "-up" : "-down"),
                () -> "Image moved to position " + gallery.moveTrackImage(id, imageId, up) + ".");
    }

    private static final Pattern UPLOAD = Pattern.compile("/admin/(car|track)s/([0-9a-fA-F-]{36})/images");

    /** The gallery to return to when a multipart request to an upload endpoint exceeds the size limit. */
    public static Optional<String> oversizedUpload(String uri, RedirectAttributes redirect) {
        var match = UPLOAD.matcher(uri == null ? "" : uri);
        if (!match.matches()) return Optional.empty();
        redirect.addFlashAttribute("galleryError", "The upload is larger than 100 MB in total; add fewer files at once.");
        return Optional.of("redirect:/admin/" + match.group(1) + "s/" + match.group(2) + "/edit#" + match.group(1) + "-images");
    }

    private static String run(RedirectAttributes redirect, String target, Supplier<String> action) {
        try {
            redirect.addFlashAttribute("galleryStatus", action.get());
            redirect.getFlashAttributes().remove("galleryKind");
            redirect.getFlashAttributes().remove("galleryLabel");
        } catch (BusinessRuleException rejected) {
            redirect.addFlashAttribute("galleryError", rejected.getMessage());
        }
        return "redirect:" + target;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException unknown) {
            throw new BusinessRuleException("Choose an image kind");
        }
    }

    private static String carEditor(UUID id) { return "/admin/cars/" + id + "/edit"; }

    private static String trackEditor(UUID id) { return "/admin/tracks/" + id + "/edit"; }
}
