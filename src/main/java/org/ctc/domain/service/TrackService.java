package org.ctc.domain.service;

import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackService {

    private final TrackRepository trackRepository;
    private final RaceRepository raceRepository;
    private final SeasonRepository seasonRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public List<Track> findAllSorted() {
        return trackRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Track findById(UUID id) {
        return trackRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Track", id));
    }

    @Transactional
    public Track save(UUID id, String name, String country) {
        Track track;
        if (id != null) {
            track = trackRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Track", id));
            track.setName(name);
            track.setCountry(country);
        } else {
            track = new Track(name, country);
        }
        try {
            track = trackRepository.saveAndFlush(track);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("A track with this name already exists");
        }
        log.info("Saved track: {}", track.getName());
        return track;
    }

    @Transactional
    public void delete(UUID id) {
        var track = trackRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Track", id));

        if (raceRepository.existsByTrackId(id)) {
            throw new BusinessRuleException("Cannot delete: track is used in a race");
        }

        boolean usedInPool = seasonRepository.findAll().stream()
                .anyMatch(s -> s.getTracks().contains(track));
        if (usedInPool) {
            throw new BusinessRuleException("Cannot delete: track is in a season pool");
        }

        if (track.getImageUrl() != null) {
            fileStorageService.delete(track.getImageUrl());
        }

        trackRepository.delete(track);
        log.info("Deleted track: {}", track.getName());
    }

    @Transactional
    public void uploadImage(UUID id, MultipartFile image) {
        var track = trackRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Track", id));
        try {
            if (track.getImageUrl() != null) {
                fileStorageService.delete(track.getImageUrl());
            }
            String url = fileStorageService.storeImage("tracks", id, image);
            track.setImageUrl(url);
            trackRepository.save(track);
            log.info("Updated image for track: {}", track.getName());
        } catch (Exception e) {
            throw new BusinessRuleException("Image upload failed: " + e.getMessage());
        }
    }
}
