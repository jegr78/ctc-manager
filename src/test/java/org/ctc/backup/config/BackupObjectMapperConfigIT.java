package org.ctc.backup.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the dual-bean shape from BackupObjectMapperConfig: the {@code @Primary} default bean
 * preserves JacksonAutoConfiguration behaviour for admin REST/AJAX paths, while the
 * {@code @Qualifier("backupObjectMapper")} strict bean serves BackupExportService.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupObjectMapperConfigIT {

    @Autowired
    private ObjectMapper defaultMapper;

    @Autowired
    @Qualifier("backupObjectMapper")
    private ObjectMapper backupMapper;

    @Test
    void givenTwoMapperBeans_whenComparingInstances_thenTheyAreDifferent() {
        // when / then
        assertThat(defaultMapper).isNotSameAs(backupMapper);
    }

    @Test
    void givenBackupMapper_whenCheckingFailOnUnknownProperties_thenItIsEnabled() {
        // when / then
        assertThat(backupMapper.getDeserializationConfig()
                .isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
                .as("backupObjectMapper must enable FAIL_ON_UNKNOWN_PROPERTIES")
                .isTrue();
    }

    @Test
    void givenDefaultMapper_whenCheckingFailOnUnknownProperties_thenItIsDisabled() {
        // when / then — Spring Boot's auto-config default is FAIL_ON_UNKNOWN_PROPERTIES=false;
        // declaring @Primary defaultObjectMapper(builder) must preserve that.
        assertThat(defaultMapper.getDeserializationConfig()
                .isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
                .as("default ObjectMapper preserves Spring Boot's permissive default")
                .isFalse();
    }

    @Test
    void givenBackupMapper_whenCheckingWriteDatesAsTimestamps_thenItIsDisabled() {
        // when / then
        assertThat(backupMapper.getSerializationConfig()
                .isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
                .as("backupObjectMapper must disable WRITE_DATES_AS_TIMESTAMPS")
                .isFalse();
    }

    @Test
    void givenBackupMapper_whenSerializingInstant_thenIsoString() throws Exception {
        // given
        Instant instant = Instant.parse("2026-05-11T10:00:00Z");
        // when
        String json = backupMapper.writeValueAsString(instant);
        // then — JavaTimeModule must produce ISO-8601 string, NOT a millis number
        assertThat(json).isEqualTo("\"2026-05-11T10:00:00Z\"");
    }
}
