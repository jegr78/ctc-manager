package org.ctc.admin.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonFormat;
import org.springframework.format.annotation.DateTimeFormat;

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

    // Both optional: without race scoring no results can be entered, without match scoring games award no match points.
    private UUID raceScoringId;
    private UUID matchScoringId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
    private Integer totalRounds;     // optional → boxed
    private int legs = 1;            // mandatory default → primitive
    private Integer eventDurationMinutes;
    private String label;            // optional, falls back to phaseType.displayName
    private Integer sortIndex;       // null → service auto-sets
}
