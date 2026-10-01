package org.ctc.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A public profile URL slug of a team or driver. A row without an entity reserves a slug that
 * several profiles shared before slugs were stored; that URL becomes a page linking all of them.
 */
@Entity
@Table(name = "site_slugs")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class SiteSlug extends BaseEntity {

	/** Every slug the allocation produces; a restored slug outside it could name a path outside the site. */
	public static final Pattern FORMAT = Pattern.compile("[a-z0-9-]*");

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private SiteSlugKind kind;

	@Column(nullable = false)
	private String slug;

	@Column(name = "base_slug", nullable = false)
	private String baseSlug;

	@Column(name = "entity_id")
	private UUID entityId;

	public SiteSlug(SiteSlugKind kind, String slug, String baseSlug, UUID entityId) {
		this.kind = kind;
		this.slug = slug;
		this.baseSlug = baseSlug;
		this.entityId = entityId;
	}
}
