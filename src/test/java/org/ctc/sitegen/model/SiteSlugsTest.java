package org.ctc.sitegen.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SiteSlugsTest {

	@Test
	void givenStoredSlugWithAPathSeparator_whenSlugsAreBuilt_thenRejected() {
		// when / then
		assertThatThrownBy(() -> new SiteSlugs(Map.of(UUID.randomUUID(), "../x"), Map.of(), List.of()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Invalid stored site slug: ../x");
	}

	@Test
	void givenTeamWithoutAllocatedSlug_whenItsSlugIsRead_thenMissingSlug() {
		// given
		UUID teamId = UUID.randomUUID();
		var slugs = new SiteSlugs(Map.of(), Map.of(), List.of());

		// when / then
		assertThatThrownBy(() -> slugs.team(teamId))
				.isInstanceOf(SiteSlugs.MissingSlugException.class)
				.hasMessage("No site slug allocated for team " + teamId);
	}
}
