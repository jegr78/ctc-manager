package org.ctc.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.ctc.domain.model.*;
import org.ctc.domain.service.SeasonCarPoolService;

@Getter
@Setter
public class SeasonPoolEntryForm {
    private UUID entryId;
    @NotBlank
    @Size(max = 120)
    private String label;
    @Valid
    @NotEmpty
    @Size(max = 32)
    private List<ClauseForm> clauses = new ArrayList<>(List.of(new ClauseForm()));

    @Getter
    @Setter
    public static class ClauseForm {
        @NotNull
        private CarCategory category;
        private Drivetrain drivetrain;
        @Min(1000)
        @Max(9999)
        private Integer modelYear;
    }

    public List<SeasonCarPoolService.ClauseInput> toInputs() {
        return clauses.stream().map(clause -> new SeasonCarPoolService.ClauseInput(clause.getCategory(), clause.getDrivetrain(), clause.getModelYear())).toList();
    }

    public static SeasonPoolEntryForm from(SeasonCarPoolEntry entry) {
        var form = new SeasonPoolEntryForm();
        form.setEntryId(entry.getId());
        form.setLabel(entry.getLabel());
        form.getClauses().clear();
        for (var clause : entry.getClauses()) {
            var input = new ClauseForm();
            input.setCategory(clause.getCategory());
            input.setDrivetrain(clause.getDrivetrain());
            input.setModelYear(clause.getModelYear());
            form.getClauses().add(input);
        }
        return form;
    }
}
