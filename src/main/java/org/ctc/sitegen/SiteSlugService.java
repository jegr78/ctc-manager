package org.ctc.sitegen;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.SiteSlug;
import org.ctc.domain.model.SiteSlugKind;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.SiteSlugRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.sitegen.model.SiteSlugs;
import org.ctc.sitegen.model.SiteSlugs.SharedSlug;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives every team and driver a stored, unique profile slug. A stored slug never changes, so a
 * renamed or colliding profile keeps its URL. A new profile whose slug is taken gets the next
 * free {@code -2}, {@code -3} suffix. Profiles without a stored slug that share one (from before
 * slugs were stored) all get suffixes, and the shared slug is reserved for a page listing them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteSlugService {

	private static final Comparator<SiteSlug> SLUG_ORDER =
			Comparator.comparingInt((SiteSlug row) -> row.getSlug().length()).thenComparing(SiteSlug::getSlug);

	private final SiteSlugRepository siteSlugRepository;
	private final TeamRepository teamRepository;
	private final DriverRepository driverRepository;
	private final SiteSlugger siteSlugger;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public SiteSlugs allocate() {
		var shared = new ArrayList<SharedSlug>();
		var teams = allocate(SiteSlugKind.TEAM, teamRepository.findAll().stream()
				.map(t -> new Profile(t.getId(), t.getShortName(), t.getCreatedAt())).toList(), shared);
		var drivers = allocate(SiteSlugKind.DRIVER, driverRepository.findAll().stream()
				.map(d -> new Profile(d.getId(), d.getPsnId(), d.getCreatedAt())).toList(), shared);
		return new SiteSlugs(teams, drivers, shared);
	}

	private Map<UUID, String> allocate(SiteSlugKind kind, List<Profile> profiles, List<SharedSlug> shared) {
		var stored = siteSlugRepository.findByKind(kind);
		var taken = new HashSet<String>();
		var slugs = new HashMap<UUID, String>();
		for (SiteSlug row : stored) {
			taken.add(row.getSlug());
			if (row.getEntityId() != null) {
				slugs.put(row.getEntityId(), row.getSlug());
			}
		}

		var pendingByBase = new TreeMap<String, List<Profile>>();
		for (Profile profile : profiles) {
			if (!slugs.containsKey(profile.id())) {
				pendingByBase.computeIfAbsent(siteSlugger.slugify(profile.name()), k -> new ArrayList<>()).add(profile);
			}
		}

		var created = new ArrayList<SiteSlug>();
		var storedSlugs = Set.copyOf(taken);
		pendingByBase.forEach((base, group) -> {
			if (group.size() == 1 && !storedSlugs.contains(base)) {
				assign(kind, group.getFirst(), base, base, slugs, created);
			}
		});
		taken.addAll(pendingByBase.keySet());
		pendingByBase.forEach((base, group) -> {
			if (group.size() == 1 && !storedSlugs.contains(base)) {
				return;
			}
			group.sort(Comparator.comparing(Profile::createdAt, Comparator.nullsFirst(Comparator.naturalOrder()))
					.thenComparing(Profile::id));
			if (!storedSlugs.contains(base)) {
				created.add(new SiteSlug(kind, base, base, null));
				log.warn("{} profiles {} shared the URL slug '{}'; it now lists them",
						kind, group.stream().map(Profile::name).toList(), base);
			}
			for (Profile profile : group) {
				String slug = nextFree(base, taken);
				taken.add(slug);
				assign(kind, profile, slug, base, slugs, created);
			}
		});
		siteSlugRepository.saveAll(created);

		var all = new ArrayList<>(stored);
		all.addAll(created);
		all.stream().filter(row -> row.getEntityId() == null).map(SiteSlug::getSlug).sorted()
				.forEach(reserved -> shared.add(new SharedSlug(kind, reserved, all.stream()
						.filter(row -> row.getEntityId() != null && row.getBaseSlug().equals(reserved))
						.sorted(SLUG_ORDER)
						.map(SiteSlug::getEntityId).toList())));
		return slugs;
	}

	private static void assign(SiteSlugKind kind, Profile profile, String slug, String base,
	                           Map<UUID, String> slugs, List<SiteSlug> created) {
		slugs.put(profile.id(), slug);
		created.add(new SiteSlug(kind, slug, base, profile.id()));
	}

	private static String nextFree(String base, Set<String> taken) {
		int suffix = 2;
		while (taken.contains(base + "-" + suffix)) {
			suffix++;
		}
		return base + "-" + suffix;
	}

	private record Profile(UUID id, String name, LocalDateTime createdAt) {
	}
}
