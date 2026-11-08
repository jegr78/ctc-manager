package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.TrackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrackEnvironmentService {
    private final TrackRepository tracks;
    private final EntityManager entities;
    private final PickSessionEpoch epoch;
    private final PickWriteExecutor writes;

    @Transactional(readOnly = true)
    public View view(UUID id) {
        String generation = epoch.managementGeneration();
        var track = tracks.findById(id).orElseThrow(() -> new EntityNotFoundException("Track", id));
        var values = TrackEnvironmentValues.from(track);
        return new View(values, String.join("\n", values.timeOfDayOptions()), track.getEnvironmentVersion(), generation,
                WeatherPreset.available(values.rainCapability()));
    }
    public void save(UUID id, String text, TrackRainCapability rain, long version, String generation) {
        var values = TrackEnvironmentValues.fromText(text, rain);
        writes.execute(() -> {
            if (!epoch.managementGeneration().equals(generation)) throw new BusinessRuleException("Reload this track after a restart or restore");
            var track = lock(id);
            if (version != track.getEnvironmentVersion()) throw new BusinessRuleException("Track environment changed; reload before saving");
            if (version == Long.MAX_VALUE) throw new BusinessRuleException("Track environment version is exhausted");
            track.setTimeOfDayOptions(values.timeOfDayOptions()); track.setRainCapability(values.rainCapability().name());
            track.setEnvironmentVersion(version + 1);
            return null;
        });
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireTimeOfDay(UUID id, String timeOfDay) {
        var values = TrackEnvironmentValues.from(lock(id));
        if (!values.timeOfDayOptions().isEmpty() && !values.timeOfDayOptions().contains(timeOfDay)) {
            throw new BusinessRuleException("Choose a time of day from this layout's current options; refresh the track choices");
        }
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireWeather(UUID id, PickWeatherSelection selection) {
        selection.requireAvailable(TrackEnvironmentValues.from(lock(id)).rainCapability());
    }
    private Track lock(UUID id) {
        var track = tracks.findByIdForEnvironmentUpdate(id).orElseThrow(() -> new EntityNotFoundException("Track", id));
        entities.refresh(track, LockModeType.PESSIMISTIC_WRITE);
        return track;
    }
    public record View(TrackEnvironmentValues values, String timeOfDayText, long version, String generation,
                       List<WeatherPreset> weatherPresets) { }
}
