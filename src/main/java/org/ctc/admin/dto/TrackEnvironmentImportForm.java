package org.ctc.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class TrackEnvironmentImportForm {
    @NotNull private UUID previewId;
    @NotNull @Size(min=1,max=256) private List<@NotNull UUID> selectedTracks;
}
