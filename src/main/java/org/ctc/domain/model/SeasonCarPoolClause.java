package org.ctc.domain.model;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "season_car_pool_clauses")
@Getter
@Setter
@NoArgsConstructor
public class SeasonCarPoolClause extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false)
    private SeasonCarPoolEntry entry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CarCategory category;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Drivetrain drivetrain;

    @Column(name = "model_year")
    private Integer modelYear;

    @Column(name = "sort_index", nullable = false)
    private int sortIndex;
}
