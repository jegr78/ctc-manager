package org.ctc.sitegen.publication;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.SitePublicationForm;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class SitePublicationController {
    public static final String PREVIEW_POLICY = "sandbox allow-scripts; default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'none'; form-action 'none'; base-uri 'none'; frame-src 'none'; frame-ancestors 'self'";
    private final SitePublicationService service;

    @GetMapping("/admin/site-preview/{id}/{*name}")
    public ResponseEntity<Resource> preview(@PathVariable String id, @PathVariable String name) {
        try {
            Resource resource = service.previewAsset(id, name.startsWith("/") ? name.substring(1) : name);
            return ResponseEntity.ok().header("Content-Security-Policy", PREVIEW_POLICY)
                    .header("Cache-Control", "no-store").header("Access-Control-Allow-Origin", "*")
                    .contentType(MediaTypeFactory.getMediaType(resource).orElse(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM))
                    .body(resource);
        } catch (IOException | IllegalArgumentException error) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/admin/site-publication")
    public String publish(@ModelAttribute SitePublicationForm form, RedirectAttributes redirect) {
        try {
            if (!form.isConfirmed()) throw new IllegalArgumentException("Confirm publication of the reviewed snapshot.");
            service.submit(form.getSnapshotId(), form.getManifestSha256());
            redirect.addFlashAttribute("successMessage", "Publication requested. The host publisher reports checks and deployment here.");
            redirect.addAttribute("snapshot", form.getSnapshotId());
        } catch (IOException | IllegalArgumentException | IllegalStateException error) {
            redirect.addFlashAttribute("errorMessage", "Publication was not requested. Review the snapshot and publication status before retrying.");
        }
        return "redirect:/admin/generate";
    }
}
