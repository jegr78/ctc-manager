package org.ctc.sitegen.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ctc.domain.model.SiteSlug;
import org.ctc.domain.model.SiteSlugKind;

/**
 * The stored profile slugs of all teams and drivers for one site generation, and the slugs
 * several profiles shared before slugs were stored.
 */
public record SiteSlugs(Map<UUID, String> teams, Map<UUID, String> drivers, List<SharedSlug> shared) {

	public SiteSlugs {
		teams = Map.copyOf(teams);
		drivers = Map.copyOf(drivers);
		shared = List.copyOf(shared);
		teams.values().forEach(SiteSlugs::requireFormat);
		drivers.values().forEach(SiteSlugs::requireFormat);
		shared.forEach(s -> requireFormat(s.slug()));
	}

	public String team(UUID teamId) {
		return require(teams, teamId, "team");
	}

	public String driver(UUID driverId) {
		return require(drivers, driverId, "driver");
	}

	public List<SharedSlug> shared(SiteSlugKind kind) {
		return shared.stream().filter(s -> s.kind() == kind).toList();
	}

	private static void requireFormat(String slug) {
		if (!SiteSlug.FORMAT.matcher(slug).matches()) {
			throw new IllegalStateException("Invalid stored site slug: " + slug);
		}
	}

	private static String require(Map<UUID, String> slugs, UUID id, String kind) {
		String slug = slugs.get(id);
		if (slug == null) {
			throw new MissingSlugException("No site slug allocated for " + kind + " " + id);
		}
		return slug;
	}

	/** Thrown for a team or driver created after the slugs of this generation were allocated. */
	public static class MissingSlugException extends IllegalStateException {

		public MissingSlugException(String message) {
			super(message);
		}
	}

	/** A slug whose URL now lists every profile that shared it. */
	public record SharedSlug(SiteSlugKind kind, String slug, List<UUID> memberIds) {

		public SharedSlug {
			memberIds = List.copyOf(memberIds);
		}
	}
}
