package org.ctc.backup.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.ctc.domain.model.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 73 / Plan 01 — Representative MixIn JSON-shape test for {@link Driver}.
 *
 * <p>Verifies the {@link DriverMixIn} contract:
 * <ul>
 *   <li>{@code aliases} back-reference is absent — PsnAlias rows are emitted only in
 *       {@code data/psn-aliases.json} (OQ-4 single-source emission).</li>
 *   <li>{@code seasonDrivers} back-reference is absent.</li>
 *   <li>{@code raceResults} back-reference is absent.</li>
 *   <li>The {@code id} and persistent fields ({@code psnId}, {@code nickname}, {@code active})
 *       are present.</li>
 * </ul>
 */
class DriverMixInTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new BackupSerializationModule())
                .registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Test
    void givenDriverWithAliases_whenSerialize_thenAliasesAbsent() throws Exception {
        // given
        Driver driver = new Driver("PSN_TEST_01", "TestDriver");
        driver.setId(UUID.fromString("12345678-1234-1234-1234-123456789012"));
        driver.addAlias("Alias_One");
        driver.addAlias("Alias_Two");

        // when
        String json = mapper.writeValueAsString(driver);
        JsonNode node = mapper.readTree(json);

        // then — back-reference collections suppressed
        assertThat(node.has("aliases"))
                .as("aliases back-reference must be ignored — PsnAlias is its own top-level entity")
                .isFalse();
        assertThat(node.has("seasonDrivers")).isFalse();
        assertThat(node.has("raceResults")).isFalse();

        // then — persistent fields present
        assertThat(node.has("id")).isTrue();
        assertThat(node.get("id").asText())
                .isEqualTo("12345678-1234-1234-1234-123456789012");
        assertThat(node.get("psnId").asText()).isEqualTo("PSN_TEST_01");
        assertThat(node.get("nickname").asText()).isEqualTo("TestDriver");
        assertThat(node.get("active").asBoolean()).isTrue();
    }
}
