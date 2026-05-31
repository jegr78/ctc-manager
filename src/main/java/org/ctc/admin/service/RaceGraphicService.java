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

    private final RaceRepository raceRepository;
    private final RaceAttachmentRepository raceAttachmentRepository;
    private final LineupGraphicService lineupGraphicService;
    private final ResultsGraphicService resultsGraphicService;
    private final SettingsGraphicService settingsGraphicService;
    private final LobbySettingsGraphicService lobbySettingsGraphicService;
    private final OverlayGraphicService overlayGraphicService;

    @Transactional
    public void generateLineup(UUID raceId) {
        generateAndSaveGraphic(raceId, "Lineups", lineupGraphicService::generateLineup);
    }

    @Transactional
    public void generateResults(UUID raceId) {
        generateAndSaveGraphic(raceId, "Results", resultsGraphicService::generateResults);
    }

    @Transactional
    public void generateSettings(UUID raceId) {
        generateAndSaveGraphic(raceId, "Settings", settingsGraphicService::generateSettings);
    }

    @Transactional
    public void generateLobbySettings(UUID raceId) {
        generateAndSaveGraphic(raceId, "LobbySettings", lobbySettingsGraphicService::generateLobbySettings);
    }

    @Transactional
    public void generateOverlay(UUID raceId) {
        generateAndSaveGraphic(raceId, "Overlay", overlayGraphicService::generateOverlay);
    }

    private void generateAndSaveGraphic(UUID raceId, String suffix, GraphicGenerator generator) {
        var race = raceRepository.findById(raceId).orElseThrow();
        try {
            String url = generator.generate(race);
            String attachmentName = race.getMatchday().getLabel() + "-"
                    + race.getHomeTeam().getShortName() + "-" + race.getAwayTeam().getShortName() + "-" + suffix;
            var attachment = new RaceAttachment(race, AttachmentType.FILE, attachmentName, url);
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
