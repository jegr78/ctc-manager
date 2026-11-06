package org.ctc.admin.service;

import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.MatchdayPairingsForm;
import org.ctc.admin.dto.PairingPickWindowForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.service.PickWindowService;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PickWindowAdminService {
    private final PickWindowService windows;
    private final Environment environment;

    public MatchdayPairingsForm matchdayForm(UUID id) {
        var view = windows.readMatchday(id);
        var form = new MatchdayPairingsForm();
        form.setId(id); form.setPicksOpen(view.open()); form.setPickDeadline(view.deadline()); form.setScheduledWeekend(view.weekend());
        form.setExpectedVersion(view.version()); form.setExpectedScope(view.scope()); form.setExpectedGeneration(view.generation());
        return form;
    }

    public PairingPickWindowForm pairingForm(PickWindowService.PairingWindow view) {
        var form = new PairingPickWindowForm();
        form.setPickDeadlineOverride(view.override()); form.setExpectedVersion(view.version());
        form.setExpectedScope(view.scope()); form.setExpectedGeneration(view.generation());
        return form;
    }

    public PickWindowService.PairingWindow pairing(UUID id, boolean playoff) { return windows.readPairing(id, playoff); }

    public void saveMatchday(UUID id, MatchdayPairingsForm form, Principal principal) {
        if (!id.equals(form.getId()) || form.getExpectedVersion() == null) throw new BusinessRuleException("Reload the current matchday picks settings");
        windows.saveMatchday(id, form.isPicksOpen(), form.getPickDeadline(), form.getScheduledWeekend(), form.getExpectedVersion(), form.getExpectedScope(), actor(principal, form.getExpectedGeneration()));
    }

    public void savePairing(UUID id, boolean playoff, PairingPickWindowForm form, Principal principal) {
        if (form.getExpectedVersion() == null) throw new BusinessRuleException("Reload the current match picks settings");
        windows.savePairing(id, playoff, form.getPickDeadlineOverride(), form.getExpectedVersion(), form.getExpectedScope(), actor(principal, form.getExpectedGeneration()));
    }

    private ManagementPickActor actor(Principal principal, String generation) {
        String name = principal == null ? null : principal.getName();
        if (name != null && !name.isBlank() && name.length() <= 128) return new ManagementPickActor(name, generation);
        if (principal == null && environment.acceptsProfiles(Profiles.of("dev", "local"))) return new ManagementPickActor("development-manager", generation);
        throw new BusinessRuleException("An authenticated management actor is required");
    }
}
