package de.ctc.admin.controller;

import de.ctc.admin.service.TeamCardService;
import de.ctc.domain.model.SeasonTeam;
import de.ctc.domain.repository.SeasonRepository;
import de.ctc.domain.repository.SeasonTeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Controller
@RequestMapping("/admin/tools/team-cards")
@RequiredArgsConstructor
public class TeamCardController {

    private final SeasonRepository seasonRepository;
    private final SeasonTeamRepository seasonTeamRepository;
    private final TeamCardService teamCardService;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @GetMapping
    public String index(@RequestParam(required = false) UUID seasonId, Model model) {
        var seasons = seasonRepository.findAll();
        var activeSeason = seasonId != null
                ? seasonRepository.findById(seasonId).orElse(null)
                : seasons.stream().filter(s -> s.isActive()).findFirst().orElse(null);

        if (activeSeason != null) {
            var seasonTeams = seasonTeamRepository.findBySeasonId(activeSeason.getId());
            var cardStates = seasonTeams.stream().map(st -> new CardState(
                    st,
                    teamCardService.cardExists(st),
                    teamCardService.getCardPath(st)
            )).toList();
            model.addAttribute("season", activeSeason);
            model.addAttribute("cardStates", cardStates);
        }

        model.addAttribute("seasons", seasons);
        model.addAttribute("selectedSeasonId", activeSeason != null ? activeSeason.getId() : null);
        return "admin/team-cards";
    }

    @PostMapping("/generate/{seasonTeamId}")
    public String generate(@PathVariable UUID seasonTeamId, RedirectAttributes redirectAttributes) {
        var seasonTeam = seasonTeamRepository.findById(seasonTeamId).orElseThrow();
        try {
            teamCardService.generateCard(seasonTeam);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Card generated: " + seasonTeam.getTeam().getShortName());
        } catch (Exception e) {
            log.error("Card generation failed for {}", seasonTeam.getTeam().getShortName(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Generation failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/team-cards?seasonId=" + seasonTeam.getSeason().getId();
    }

    @PostMapping("/generate-all")
    public String generateAll(@RequestParam UUID seasonId, RedirectAttributes redirectAttributes) {
        var season = seasonRepository.findById(seasonId).orElseThrow();
        try {
            var paths = teamCardService.generateAllCards(season);
            redirectAttributes.addFlashAttribute("successMessage",
                    paths.size() + " cards generated");
        } catch (Exception e) {
            log.error("Batch card generation failed for season {}", season.getName(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Generation failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/team-cards?seasonId=" + seasonId;
    }

    @GetMapping("/download/{seasonTeamId}")
    public ResponseEntity<Resource> download(@PathVariable UUID seasonTeamId) {
        var seasonTeam = seasonTeamRepository.findById(seasonTeamId).orElseThrow();
        String cardPath = teamCardService.getCardPath(seasonTeam);
        Path file = Paths.get(uploadDir).toAbsolutePath().normalize()
                .resolve(cardPath.substring("/uploads/".length()));

        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + seasonTeam.getTeam().getShortName() + "-card.png\"")
                .body(new FileSystemResource(file));
    }

    @GetMapping("/download-all")
    public ResponseEntity<byte[]> downloadAll(@RequestParam UUID seasonId) throws IOException {
        var seasonTeams = seasonTeamRepository.findBySeasonId(seasonId);
        var baos = new ByteArrayOutputStream();

        try (var zip = new ZipOutputStream(baos)) {
            for (var st : seasonTeams) {
                String cardPath = teamCardService.getCardPath(st);
                Path file = Paths.get(uploadDir).toAbsolutePath().normalize()
                        .resolve(cardPath.substring("/uploads/".length()));
                if (Files.exists(file)) {
                    zip.putNextEntry(new ZipEntry(st.getTeam().getShortName() + "-card.png"));
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
        }

        var season = seasonRepository.findById(seasonId).orElseThrow();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"team-cards-" + season.getName().replaceAll("[^a-zA-Z0-9-]", "_") + ".zip\"")
                .body(baos.toByteArray());
    }

    public record CardState(SeasonTeam seasonTeam, boolean exists, String cardPath) {}
}
