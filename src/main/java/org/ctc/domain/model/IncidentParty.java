package org.ctc.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentParty {
    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 16)
    private IncidentPartyType type;
    @Column(name = "party_id", nullable = false)
    private UUID id;
    @Column(name = "party_label", nullable = false, length = 255)
    private String label;
    @Column(name = "party_role_id", nullable = false, length = 32)
    private String roleId;

    public IncidentParty(IncidentPartyType type, UUID id, String label, String roleId) {
        this.type = type; this.id = id; this.label = label; this.roleId = roleId;
    }
}
