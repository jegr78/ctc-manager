package org.ctc.backup.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.UUID;
import org.ctc.domain.model.AttachmentType;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceAttachment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@link RaceAttachmentMixIn} contract:
 * <ul>
 *   <li>{@code race} foreign key renders as a UUID string.</li>
 *   <li>{@code type} enum renders as the string {@code "FILE"} (Jackson default
 *       {@code Enum.name()} strategy).</li>
 *   <li>{@code url} stays as the original path string — drives the {@code uploads/} mirror.</li>
 *   <li>{@code image} computed property (from {@code isImage()}) is absent.</li>
 * </ul>
 */
class RaceAttachmentMixInTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new BackupSerializationModule())
                .registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Test
    void givenFileAttachment_whenSerialize_thenRaceIsIdRefAndTypeIsStringEnum() throws Exception {
        // given
        Race race = new Race();
        race.setId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));

        RaceAttachment attachment = new RaceAttachment(
                race,
                AttachmentType.FILE,
                "screenshot.png",
                "/uploads/races/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/screenshot.png");
        attachment.setId(UUID.fromString("11111111-2222-3333-4444-555555555555"));

        // when
        String json = mapper.writeValueAsString(attachment);
        JsonNode node = mapper.readTree(json);

        // then — race FK as UUID string
        assertThat(node.get("race").isTextual()).isTrue();
        assertThat(node.get("race").asText())
                .isEqualTo("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        // then — type enum as string
        assertThat(node.get("type").isTextual()).isTrue();
        assertThat(node.get("type").asText()).isEqualTo("FILE");

        // then — url preserved verbatim
        assertThat(node.get("url").asText())
                .isEqualTo("/uploads/races/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/screenshot.png");
        assertThat(node.get("name").asText()).isEqualTo("screenshot.png");

        // then — isImage() computed property suppressed
        assertThat(node.has("image"))
                .as("isImage() computed property must be ignored")
                .isFalse();

        // then — id present
        assertThat(node.get("id").asText())
                .isEqualTo("11111111-2222-3333-4444-555555555555");
    }
}
