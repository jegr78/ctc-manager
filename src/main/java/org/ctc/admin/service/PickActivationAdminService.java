package org.ctc.admin.service;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.MatchdayPickView.Value;
import org.ctc.admin.dto.PickActivationForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PickPublicationContent;
import org.ctc.domain.model.TimeProgressionMode;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.service.PickActivationService;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PickActivationAdminService {
    private final PickActivationService activation;
    private final Environment environment;

    public View view(UUID seasonId) {
        var review = activation.review(seasonId);
        var rows = review.configured().stream().map(candidate -> new Row(candidate.raceId(), candidate.label(), candidate.confirmedPublic(),
                candidate.evidence(), values(candidate.content()))).toList();
        return new View(review, rows);
    }
    public PickActivationForm form(View view) {
        var form = new PickActivationForm(); form.setExpectedFingerprint(view.review().fingerprint()); form.setExpectedGeneration(view.review().generation());
        return form;
    }
    public void enable(UUID seasonId, PickActivationForm form, Principal principal) {
        activation.enable(seasonId, form.getExpectedFingerprint(), form.getClassifications(), actor(principal, form.getExpectedGeneration()));
    }
    public void pause(UUID seasonId, PickActivationForm form, Principal principal) {
        activation.pause(seasonId, actor(principal, form.getExpectedGeneration()));
    }
    private ManagementPickActor actor(Principal principal, String generation) {
        String name = principal == null ? null : principal.getName();
        if (principal == null && environment.acceptsProfiles(Profiles.of("dev", "local"))) name = "development-manager";
        if (name == null || name.isBlank() || name.length() > 128) throw new BusinessRuleException("An authenticated management actor is required");
        return new ManagementPickActor(name, generation);
    }
    private static List<Value> values(PickPublicationContent content) {
        var v = content.settings();
        String progression = v.timeProgressionMode() == TimeProgressionMode.EQUAL_CONDITIONS ? "Equal Conditions"
                : v.timeProgressionMultiplier() == null ? "Not set" : v.timeProgressionMultiplier() + "x";
        return List.of(new Value("Track", text(content.trackName())), new Value("Car", text(content.carName())),
                new Value("Pool entry", text(content.poolEntryLabel())), new Value("Laps", text(v.laps())),
                new Value("Tyre wear", text(v.tyreWear())), new Value("Fuel consumption", text(v.fuelConsumption())),
                new Value("Refuelling litres/second", text(v.refuelingSpeed())), new Value("Initial fuel", text(v.initialFuel())),
                new Value("Required pit stops", text(v.requiredPitStops())), new Value("Time progression", progression),
                new Value("Weather", text(v.weather())), new Value("Time of day", text(v.timeOfDay())),
                new Value("Available tyres", text(v.availableTyres())), new Value("Mandatory tyres", text(v.mandatoryTyres())));
    }
    private static String text(Object value) { return value == null || value.toString().isBlank() ? "Not set" : value.toString(); }
    public record View(PickActivationService.Review review, List<Row> rows) { }
    public record Row(UUID raceId, String label, boolean confirmedPublic, List<PickActivationService.Evidence> evidence, List<Value> values) { }
}
