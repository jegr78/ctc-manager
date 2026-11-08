package org.ctc.gt7sync.environment;

import java.util.List;
import org.ctc.domain.model.TrackRainCapability;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TrackEnvironmentCatalogTest {
    @Test
    void givenReviewedAliases_whenResolvingKnownLayouts_thenUseTheirExactProfileIncludingNestedExceptions() throws Exception {
        // given
        var catalog=new TrackEnvironmentCatalog(); var source=GtPlanetEnvironmentSource.bundled();
        // when / then
        for (String name:List.of("24 Heures du Mans Racing Circuit","24 Heures du Mans Racing Circuit No Chicane")) {
            var profile=catalog.find(name,source); assertThat(profile).isNotNull();
            assertThat(profile.values().timeOfDayOptions()).hasSize(11); assertThat(profile.values().rainCapability()).isEqualTo(TrackRainCapability.RAIN);
        }
        assertThat(catalog.find("Circuit de Spa-Francorchamps",source).timeOfDay()).hasSize(4);
        assertThat(catalog.find("Spa 24h Layout",source).timeOfDay()).hasSize(11);
        assertThat(catalog.find("Tokyo Expressway - Central Clockwise",source).values().rainCapability()).isEqualTo(TrackRainCapability.RAIN);
        assertThat(catalog.find("Tokyo Expressway - South Clockwise",source).values().rainCapability()).isEqualTo(TrackRainCapability.DRY_ONLY);
        assertThat(catalog.find("Autodrome Lago Maggiore - East",source).timeOfDay()).hasSize(5);
        assertThat(catalog.find("Autodrome Lago Maggiore - East End",source).timeOfDay()).hasSize(7);
        assertThat(catalog.find("Willow Springs International Raceway: Horse Thief Mile",source).timeOfDay()).hasSize(4);
        assertThat(catalog.find("Willow Springs International Raceway: Big Willow",source).timeOfDay()).hasSize(7);
        assertThat(catalog.find("Michelin Raceway Road Atlanta ",source)).isNotNull();
    }
    @Test
    void givenNewOrChangedLayoutNames_whenResolving_thenNeverGuessFromACircuitPrefix() throws Exception {
        // given
        var catalog=new TrackEnvironmentCatalog(); var source=GtPlanetEnvironmentSource.bundled();
        // when / then
        for (String name:List.of("SUGO International Racing Course","Nürburgring Endurance II","Nürburgring Nordschleife Tourist","High Speed Ring New","high speed ring","Test Circuit")) assertThat(catalog.find(name,source)).isNull();
        assertThat(catalog.mappings()).hasSize(120);
        assertThat(catalog.mappings().values()).allMatch(key -> source.profiles().stream().anyMatch(p -> p.key().equals(key)));
    }
    @Test
    void givenDuplicateOrMalformedAliases_whenConstructingCatalog_thenRejectAmbiguousMappings() {
        // given / when / then
        assertThatThrownBy(() -> new TrackEnvironmentCatalog(List.of(new TrackEnvironmentCatalog.Mapping("Test","Region | Track"),new TrackEnvironmentCatalog.Mapping("Test ","Other | Track"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrackEnvironmentCatalog(List.of(new TrackEnvironmentCatalog.Mapping(" ","Region | Track"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
