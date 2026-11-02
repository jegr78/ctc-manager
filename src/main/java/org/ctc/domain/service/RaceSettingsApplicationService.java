package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.repository.SeasonCarPoolEntryRepository;
import org.ctc.domain.model.PickPayload;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.Track;
import org.ctc.domain.model.SeasonCarPoolEntry;
import org.ctc.domain.model.RaceSettings;
import org.ctc.domain.model.RaceSettingsValues;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class RaceSettingsApplicationService {
    private final CarRepository cars;
    private final TrackRepository tracks;
    private final SeasonCarPoolEntryRepository entries;
    public void apply(Race race, PickPayload payload) {
        var car = cars.findById(payload.carId()).orElseThrow(() -> new BusinessRuleException("Pick car no longer exists"));
        var track = tracks.findById(payload.trackId()).orElseThrow(() -> new BusinessRuleException("Pick track no longer exists"));
        var entry = payload.carPoolEntryId() == null ? null : entries.findById(payload.carPoolEntryId())
                .orElseThrow(() -> new BusinessRuleException("Pick pool entry no longer exists"));
        apply(race, track, car, entry, RaceSettingsValues.from(payload));
    }

    public void apply(Race race, Track track, Car car, SeasonCarPoolEntry entry, RaceSettingsValues values) {
        race.setTrack(track);
        race.setCar(car);
        race.setCarPoolEntry(entry);
        var settings = race.getSettings();
        if (settings == null) {
            settings = new RaceSettings(race);
            race.setSettings(settings);
        }
        values.applyTo(settings);
    }
}
