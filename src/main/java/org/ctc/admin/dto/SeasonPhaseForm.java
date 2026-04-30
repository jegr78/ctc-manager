package org.ctc.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonFormat;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class SeasonPhaseForm {

    private UUID id;

    // NOTE: seasonId is INTENTIONALLY NOT a DTO field (W-7 IDOR / Mass-Assignment hardening).
    // The path variable {seasonId} on the SeasonPhaseController is the single source of
    // truth — controllers MUST resolve seasonId via @PathVariable only, never via form
    // binding. Eliminates cross-tenant tampering at the DTO boundary.

    @NotNull
    private PhaseType phaseType;

    @NotNull
    private PhaseLayout layout;

    @NotNull
    private SeasonFormat format = SeasonFormat.LEAGUE;

    private UUID raceScoringId;     // optional in form; service enforces non-null when phase is non-PLAYOFF
    private UUID matchScoringId;

    private LocalDate startDate;
    private LocalDate endDate;
    private Integer totalRounds;     // optional → boxed
    private int legs = 1;            // mandatory default → primitive
    private Integer eventDurationMinutes;
    private String label;            // optional, falls back to phaseType.displayName per D-05
    private Integer sortIndex;       // null → service auto-sets per D-10
}
