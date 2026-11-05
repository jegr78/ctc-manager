package org.ctc.admin.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.ctc.domain.service.RacePenaltyService;

@Getter
@Setter
public class RacePenaltyForm {
    @NotNull private UUID homeTeamId;
    @NotNull private UUID awayTeamId;
    @NotNull @Min(0) private Integer homePenalty;
    @NotNull @Min(0) private Integer awayPenalty;
    @NotNull @Min(0) private Integer expectedRevision;
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") private String fingerprint;
    @NotBlank @Size(max = 100) private String generation;
    @NotBlank @Size(max = 2000) private String reason;

    public RacePenaltyService.Correction correction() {
        return new RacePenaltyService.Correction(homeTeamId, awayTeamId, homePenalty, awayPenalty,
                expectedRevision, fingerprint, generation, reason);
    }

    public static RacePenaltyForm from(RacePenaltyService.View view) {
        var form = new RacePenaltyForm();
        form.setHomeTeamId(view.race().getHomeTeam().getId());
        form.setAwayTeamId(view.race().getAwayTeam().getId());
        form.setHomePenalty(view.totals().home().penalty());
        form.setAwayPenalty(view.totals().away().penalty());
        form.setExpectedRevision(view.revision());
        form.setFingerprint(view.fingerprint());
        form.setGeneration(view.generation());
        form.setReason("");
        return form;
    }
}
