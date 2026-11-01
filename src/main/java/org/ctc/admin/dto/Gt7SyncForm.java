package org.ctc.admin.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Gt7SyncForm {
    private List<String> selectedCars = new ArrayList<>();
    private List<String> selectedTracks = new ArrayList<>();
    private List<String> selectedMetadata = new ArrayList<>();
    private UUID previewId;
}
