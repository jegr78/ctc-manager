package org.ctc.admin.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.TrailerStartForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.trailer.TrailerScope;
import org.ctc.trailer.TrailerService;
import org.ctc.trailer.TrailerViewService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class TrailerController {

    private final TrailerService trailers;
    private final TrailerViewService views;

    @PostMapping("/admin/matchdays/{id}/trailer")
    public String startMatchday(@PathVariable UUID id, @ModelAttribute TrailerStartForm form, RedirectAttributes redirect) {
        return start(new TrailerScope.MatchdayScope(id), form, redirect, "/admin/matchdays/" + id + "#matchday-trailer");
    }

    @PostMapping("/admin/playoffs/round/{roundId}/trailer")
    public String startRound(@PathVariable UUID roundId, @ModelAttribute TrailerStartForm form, RedirectAttributes redirect) {
        String target = "/admin/playoffs/" + views.playoffIdForRound(roundId) + "#round-trailer-" + roundId;
        return start(new TrailerScope.PlayoffRoundScope(roundId), form, redirect, target);
    }

    @GetMapping("/admin/matchdays/{id}/trailer/status")
    @ResponseBody
    public Map<String, Object> matchdayStatus(@PathVariable UUID id) {
        return status(new TrailerScope.MatchdayScope(id));
    }

    @GetMapping("/admin/playoffs/round/{roundId}/trailer/status")
    @ResponseBody
    public Map<String, Object> roundStatus(@PathVariable UUID roundId) {
        return status(new TrailerScope.PlayoffRoundScope(roundId));
    }

    @GetMapping("/admin/matchdays/{id}/trailer/{file}")
    public ResponseEntity<Resource> matchdayFile(@PathVariable UUID id, @PathVariable String file) {
        return download(new TrailerScope.MatchdayScope(id), file);
    }

    @GetMapping("/admin/playoffs/round/{roundId}/trailer/{file}")
    public ResponseEntity<Resource> roundFile(@PathVariable UUID roundId, @PathVariable String file) {
        return download(new TrailerScope.PlayoffRoundScope(roundId), file);
    }

    private String start(TrailerScope scope, TrailerStartForm form, RedirectAttributes redirect, String target) {
        try {
            trailers.start(scope, form.getMusicId());
            redirect.addFlashAttribute("successMessage", "Rendering the trailer in the background.");
        } catch (BusinessRuleException rejected) {
            redirect.addFlashAttribute("errorMessage", rejected.getMessage());
        }
        return "redirect:" + target;
    }

    private Map<String, Object> status(TrailerScope scope) {
        return trailers.status(scope)
                .<Map<String, Object>>map(s -> Map.of("running", s.running(), "state", s.state().name(), "message", s.message()))
                .orElse(Map.of("running", false, "state", "NONE", "message", ""));
    }

    private ResponseEntity<Resource> download(TrailerScope scope, String file) {
        var outputs = trailers.latest(scope);
        if (outputs.isEmpty()) return ResponseEntity.notFound().build();
        var latest = outputs.get();
        return switch (file) {
            case "video" -> attachment(latest.mp4(), MediaType.parseMediaType("video/mp4"), latest.downloadName() + ".mp4");
            case "layers" -> attachment(latest.zip(), MediaType.parseMediaType("application/zip"), latest.downloadName() + "-layers.zip");
            case "youtube" -> attachment(latest.youtubeText(), new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8),
                    latest.downloadName() + "-youtube.txt");
            default -> ResponseEntity.notFound().build();
        };
    }

    private static ResponseEntity<Resource> attachment(Path path, MediaType type, String filename) {
        if (!Files.isRegularFile(path)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(new FileSystemResource(path));
    }
}
