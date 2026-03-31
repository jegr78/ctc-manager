package org.ctc.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Entity
@Table(name = "race_attachments")
@Getter @Setter @NoArgsConstructor @ToString(exclude = {"race"})
public class RaceAttachment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttachmentType type;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 1000)
    private String url;

    public RaceAttachment(Race race, AttachmentType type, String name, String url) {
        this.race = race;
        this.type = type;
        this.name = name;
        this.url = url;
    }

    public boolean isImage() {
        return type == AttachmentType.FILE && name.matches("(?i).*\\.(png|jpg|jpeg|gif|webp)$");
    }
}
