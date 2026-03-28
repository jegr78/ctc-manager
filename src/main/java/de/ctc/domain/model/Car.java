package de.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "cars", uniqueConstraints = @UniqueConstraint(columnNames = {"manufacturer", "name"}))
@Getter @Setter @NoArgsConstructor @ToString
public class Car {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank @Column(nullable = false)
    private String manufacturer;

    @NotBlank @Column(nullable = false)
    private String name;

    public Car(String manufacturer, String name) {
        this.manufacturer = manufacturer;
        this.name = name;
    }

    public String getDisplayName() {
        return manufacturer + " — " + name;
    }
}
