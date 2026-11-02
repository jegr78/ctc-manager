package org.ctc.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.ctc.domain.model.CarPoolMode;

@Getter
@Setter
public class SeasonPoolModeForm {
    @NotNull
    private CarPoolMode carPoolMode;
}
