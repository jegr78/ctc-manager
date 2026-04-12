package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Entity
@Table(name = "psn_aliases")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "driver")
public class PsnAlias extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@NotBlank
	@Column(nullable = false, unique = true)
	private String alias;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "driver_id", nullable = false)
	private Driver driver;

	public PsnAlias(Driver driver, String alias) {
		this.driver = driver;
		this.alias = alias;
	}
}
