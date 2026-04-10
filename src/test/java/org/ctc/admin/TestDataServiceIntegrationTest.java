package org.ctc.admin;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying demo logo classpath resources for DATA-08.
 * Ensures fictive team logos are present and real CTC logos are absent.
 */
class TestDataServiceIntegrationTest {

    // --- DATA-08: Fictive team logos ---

    @Test
    void givenFictiveTeamShortNames_whenLoadingClasspathResource_thenAllTenLogosExist() {
        // given
        var fictiveNames = List.of("VRX", "SGM", "ADR", "TBR", "ICL", "SVT", "NFR", "EGP", "HMS", "PWR");

        // when / then
        for (String shortName : fictiveNames) {
            var resource = new ClassPathResource("demo/team-logos/" + shortName + ".png");
            assertThat(resource.exists())
                    .as("Logo for fictive team %s must exist on classpath", shortName)
                    .isTrue();
        }
    }

    @Test
    void givenRealCtcTeamShortNames_whenLoadingClasspathResource_thenNoLogosExist() {
        // given
        var realNames = List.of("AHR", "ART", "CLR", "DTR", "GXR", "MRL", "P1R", "TCR", "TNR", "VEZ");

        // when / then
        for (String shortName : realNames) {
            var resource = new ClassPathResource("demo/team-logos/" + shortName + ".png");
            assertThat(resource.exists())
                    .as("Logo for real CTC team %s must NOT exist on classpath", shortName)
                    .isFalse();
        }
    }
}
