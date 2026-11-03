package org.ctc.domain.service;

import java.util.EnumSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PickDisclosureField;
import org.ctc.domain.model.PickPublicationState;
import org.ctc.domain.model.RaceSettingsValues;
import org.ctc.domain.picks.PublicPickProjection;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.PickPublicationSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PickDisclosureService {
    private final RaceRepository races;
    private final PickPublicationSnapshotRepository snapshots;

    @Transactional(readOnly = true)
    public PublicPickProjection forPublic(UUID raceId) {
        var race = races.findById(raceId).orElseThrow(() -> new EntityNotFoundException("Race", raceId));
        if (race.isPicksManaged()) {
            var confirmed = snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(raceId).stream()
                    .map(snapshot -> snapshot.getAttempt())
                    .filter(attempt -> attempt.getState() == PickPublicationState.SUCCEEDED && attempt.matchesTarget(race))
                    .toList();
            long version = confirmed.stream().mapToLong(attempt -> attempt.getContentVersion()).max().orElse(0);
            var latest = confirmed.stream().filter(attempt -> attempt.getContentVersion() == version).toList();
            if (latest.isEmpty()) return new PublicPickProjection(raceId, null, null, null, null);
            var first = latest.getFirst();
            var fields = EnumSet.noneOf(PickDisclosureField.class);
            for (var attempt : latest) {
                if (!first.getContentHash().equals(attempt.getContentHash())
                        || first.getOrigin() != attempt.getOrigin() || first.getSourceVersion() != attempt.getSourceVersion()
                        || !attempt.getContentHash().equals(attempt.getContent().fingerprint())) {
                    throw new BusinessRuleException("Published pick content has inconsistent confirmation evidence");
                }
                fields.addAll(attempt.fields());
            }
            return first.getContent().project(raceId, fields);
        }
        var car = RaceCarPresentation.from(race);
        return new PublicPickProjection(raceId, car.carName(), car.poolEntryLabel(), race.getTrack() == null ? null : race.getTrack().getName(),
                race.getSettings() == null ? null : RaceSettingsValues.from(race.getSettings()));
    }
}
