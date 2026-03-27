package de.ctc.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class PlayoffForm {

    private UUID id;
    private UUID seasonId;
    private String name;
    private int bestOfLegs = 1;
    private int numberOfTeams = 8;
}
