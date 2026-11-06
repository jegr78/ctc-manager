package org.ctc.admin.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.ctc.domain.service.RaceDisqualificationService;

@Getter
@Setter
public class DisqualificationForm {
    @NotNull private Boolean disqualified;
    @NotNull @Min(0) private Integer expectedRevision;
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") private String fingerprint;
    @NotBlank @Size(max = 100) private String generation;
    @NotBlank @Size(max = 2000) private String reason;

    public RaceDisqualificationService.Correction correction() {
        return new RaceDisqualificationService.Correction(disqualified, expectedRevision, fingerprint, generation, reason);
    }
    public static DisqualificationForm from(RaceDisqualificationService.View view) {
        var form = new DisqualificationForm(); form.setDisqualified(view.disqualified()); form.setExpectedRevision(view.revision());
        form.setFingerprint(view.fingerprint()); form.setGeneration(view.generation()); form.setReason(""); return form;
    }
}
