package org.ctc.admin.service;

import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.AttachmentType;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceAttachment;
import org.ctc.domain.repository.RaceAttachmentRepository;
import org.ctc.domain.repository.RaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaceGraphicService {

    private final org.ctc.domain.service.ScoreWriteLockService scoreLocks;
    private final RaceRepository raceRepository;
    private final RaceAttachmentRepository raceAttachmentRepository;
    private final LineupGraphicService lineupGraphicService;
    private final ResultsGraphicService resultsGraphicService;
    private final ProvisionalScoresGraphicService provisionalScoresGraphicService;
    private final SettingsGraphicService settingsGraphicService;
    private final LobbySettingsGraphicService lobbySettingsGraphicService;
    private final OverlayGraphicService overlayGraphicService;

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void generateLineup(UUID raceId) {
        generateAndSaveGraphic(raceId, "Lineups", lineupGraphicService::generateLineup);
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void generateResults(UUID raceId) {
        generateAndSaveGraphic(raceId, "Results", resultsGraphicService::generateResults);
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void generateProvisional(UUID raceId) {
        generateAndSaveGraphic(raceId, "Provisional", provisionalScoresGraphicService::generateProvisionalFile);
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void generateSettings(UUID raceId) {
        generateAndSaveGraphic(raceId, "Settings", settingsGraphicService::generateSettings);
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void generateLobbySettings(UUID raceId) {
        generateAndSaveGraphic(raceId, "LobbySettings", lobbySettingsGraphicService::generateLobbySettings);
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void generateOverlay(UUID raceId) {
        generateAndSaveGraphic(raceId, "Overlay", overlayGraphicService::generateOverlay);
    }

    private void generateAndSaveGraphic(UUID raceId, String suffix, GraphicGenerator generator) {
        scoreLocks.lockRace(raceId);
        var race = raceRepository.findById(raceId).orElseThrow();
        try {
            String url = generator.generate(race);
            String attachmentName = race.getMatchday().getLabel() + "-"
                    + race.getHomeTeam().getShortName() + "-" + race.getAwayTeam().getShortName() + "-" + suffix;
            var attachment = race.getAttachments().stream()
                    .filter(existing -> existing.getType() == AttachmentType.FILE && url.equals(existing.getUrl()))
                    .findFirst().orElseGet(() -> {
                        var created = new RaceAttachment(race, AttachmentType.FILE, attachmentName, url);
                        race.getAttachments().add(created); return created;
                    });
            attachment.setName(attachmentName);
            raceAttachmentRepository.save(attachment);
        } catch (IOException e) {
            log.error("{} generation failed for race {}", suffix, raceId, e);
            throw new RuntimeException("Generation failed: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface GraphicGenerator {
        String generate(Race race) throws IOException;
    }
}
