package de.ctc.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class RaceResultForm {

    private UUID driverId;
    private String driverPsnId;
    private String teamShortName;
    private int position;
    private int qualiPosition;
    private boolean fastestLap;
}
