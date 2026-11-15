package org.ctc.sitegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.SiteSlug;
import org.ctc.domain.model.SiteSlugKind;
import org.ctc.domain.repository.SiteSlugRepository;
import org.junit.jupiter.api.Test;

class SiteProfileLinksTest {
    final SiteProperties properties = new SiteProperties();
    final SiteSlugRepository slugs = mock(SiteSlugRepository.class);
    final SiteProfileLinks links = new SiteProfileLinks(properties, slugs, new SiteSlugger());
    final UUID team = UUID.randomUUID();

    @Test
    void givenBaseUrlAndStoredSlug_whenLinkingTeamAndDriver_thenBuildTheGeneratedProfilePaths() {
        // given
        properties.setPublicBaseUrl("https://example.test/ctc");
        when(slugs.findFirstByKindAndEntityId(SiteSlugKind.TEAM, team)).thenReturn(Optional.of(slug("t-alf")));
        var driver = UUID.randomUUID();
        when(slugs.findFirstByKindAndEntityId(SiteSlugKind.DRIVER, driver)).thenReturn(Optional.of(slug("test-driver")));
        // when / then
        assertThat(links.team(team, "2026 | #1 | Spring Cup")).isEqualTo("https://example.test/ctc/season/2026-1-spring-cup/team/t-alf.html");
        assertThat(links.driver(driver, "2026 | #1 | Spring Cup")).isEqualTo("https://example.test/ctc/season/2026-1-spring-cup/driver/test-driver.html");
    }

    @Test
    void givenNoBaseUrlOrNoSlug_whenLinking_thenReturnNoLink() {
        // given
        when(slugs.findFirstByKindAndEntityId(SiteSlugKind.TEAM, team)).thenReturn(Optional.of(slug("t-alf")));
        // when / then
        assertThat(links.team(team, "2026 | #1 | Spring Cup")).as("without a base URL embeds carry no link").isNull();
        properties.setPublicBaseUrl("https://example.test/");
        assertThat(links.team(UUID.randomUUID(), "2026 | #1 | Spring Cup")).as("a team without a stored slug has no page").isNull();
        assertThat(links.team(team, null)).isNull();
    }

    private static SiteSlug slug(String value) {
        var slug = new SiteSlug();
        slug.setSlug(value);
        return slug;
    }
}
