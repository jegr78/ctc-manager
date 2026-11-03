package org.ctc.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentPayload {
    @Column(nullable = false, length = 2000)
    private String involvedDrivers;
    @Column(nullable = false, length = 200)
    private String replayTimestamp;
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String description;
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String evidenceLinks;

    public IncidentPayload(String involvedDrivers, String replayTimestamp, String description, String evidenceLinks) {
        this.involvedDrivers = involvedDrivers;
        this.replayTimestamp = replayTimestamp;
        this.description = description;
        this.evidenceLinks = evidenceLinks == null ? "" : evidenceLinks;
    }
}
