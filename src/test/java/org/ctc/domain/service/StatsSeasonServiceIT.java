package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.stats.StatsLookupException;
import org.ctc.domain.stats.StatsScope;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@CtcDevSpringBootContext
@Tag("integration")
class StatsSeasonServiceIT {
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired StatsSeasonService service;
    @Autowired SeasonRepository seasons;
    Season published;
    Season test;
    UUID previouslyActive;

    @BeforeEach
    void setUp() {
        // given
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tx.executeWithoutResult(status -> {
            published = helper.createSeason("T-SSS " + suffix, 2091, 1);
            test = helper.createSeason("Test stats " + suffix, 2091, 2);
        });
        service.clearCaches();
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(published.getId()).orElseThrow());
            helper.deleteSeasonCascade(seasons.findById(test.getId()).orElseThrow());
            if (previouslyActive != null) {
                seasons.flush();
                var restored = seasons.findById(previouslyActive).orElseThrow();
                restored.setActive(true);
                seasons.save(restored);
            }
        });
        service.clearCaches();
    }

    @Test
    void givenTestAndPublishedSeasons_whenCountingAndChoosing_thenOnlyThePublishedSeasonCounts() {
        // when
        var counted = service.counted().stream().map(Season::getId).toList();
        // then
        assertThat(counted).as("a season named Test never counts").contains(published.getId()).doesNotContain(test.getId());
        assertThat(service.period(StatsScope.SEASON, published.getId().toString()).season().id()).isEqualTo(published.getId());
        assertThatThrownBy(() -> service.period(StatsScope.SEASON, test.getId().toString()))
                .isInstanceOf(StatsLookupException.class).hasMessage("No season found");
        assertThat(service.seasonKey(published.getDisplayLabel())).isEqualTo(published.getId());
        assertThat(service.seasonKey(test.getId().toString())).isNull();
    }

    @Test
    void givenNoActiveSeason_whenAskingForTheCurrentSeason_thenFallBackToAlltime() {
        // given
        tx.executeWithoutResult(status -> seasons.findByActiveTrue().ifPresent(active -> {
            previouslyActive = active.getId();
            active.setActive(false);
            seasons.save(active);
        }));
        // when
        var period = service.period(StatsScope.CURRENT, null);
        // then
        assertThat(period.alltime()).isTrue();
        assertThat(period.fallback()).as("the answer says that no season is active").isTrue();
        assertThat(period.seasonIds()).contains(published.getId()).doesNotContain(test.getId());
    }
}
