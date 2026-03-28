package de.ctc.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class RaceForm {

    private UUID id;
    private UUID matchdayId;
    private UUID homeTeamId;
    private UUID awayTeamId;
    private String track;
    private String car;
    private LocalDateTime dateTime;
    private List<RaceResultForm> results = new ArrayList<>();
}
