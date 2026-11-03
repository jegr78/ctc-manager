package org.ctc.discord.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.ctc.discord.dto.NamedAttachment;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PickPublicationManifestTest {
    final ObjectMapper mapper = new ObjectMapper();
    static final String EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    static final String IMAGE = "6105d6cc76af400325e94d588ce511be5bfdbb73b437dc51eca43917d7a43e3d";

    @Test
    void givenCapturedImageAndContent_whenReadingTheManifest_thenRetainExactByteProofAndImmutableDescriptors() {
        // given
        byte[] image = "image".getBytes(StandardCharsets.UTF_8);
        String captured = PickPublicationManifest.capture(null, List.of(new NamedAttachment("settings-race-1.png", image)), mapper);
        image[0] = 'X';
        // when
        var parsed = assertDoesNotThrow(() -> PickPublicationManifest.read(captured, mapper));
        // then
        assertThat(parsed).isNotNull(); assertThat(parsed.contentHash()).isEqualTo(EMPTY);
        assertThat(parsed.attachments()).containsExactly(new PickPublicationManifest.FileProof("settings-race-1.png", 5, IMAGE));
        assertThatThrownBy(() -> parsed.attachments().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(PickPublicationManifest.hash("image".getBytes(StandardCharsets.UTF_8))).isEqualTo(IMAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"format", "coerced-format", "unknown-field", "content-hash", "empty-files", "too-many-files", "duplicate-file",
            "path-filename", "unknown-filename", "zero-bytes", "too-many-bytes", "coerced-bytes", "fractional-bytes", "file-hash", "unknown-file-field", "not-json"})
    void givenUnverifiableCapturedManifest_whenDecoding_thenRejectWithoutPrivateValuesInTheError(String problem) throws Exception {
        // given
        var root = (ObjectNode) mapper.readTree(valid()); var file = (ObjectNode) root.withArray("attachments").get(0);
        switch (problem) {
            case "format" -> root.put("format", 2);
            case "coerced-format" -> root.put("format", "1");
            case "unknown-field" -> root.put("extra", "TEST-PRIVATE-MANIFEST-VALUE");
            case "content-hash" -> root.put("contentHash", "bad");
            case "empty-files" -> root.withArray("attachments").removeAll();
            case "too-many-files" -> { for (int i = 0; i < 10; i++) root.withArray("attachments").add(file.deepCopy().put("filename", "lobby-settings-race-" + (i + 1) + ".png")); }
            case "duplicate-file" -> root.withArray("attachments").add(file.deepCopy());
            case "path-filename" -> file.put("filename", "../settings-race-1.png");
            case "unknown-filename" -> file.put("filename", "TEST-PRIVATE-MANIFEST-VALUE.png");
            case "zero-bytes" -> file.put("bytes", 0);
            case "too-many-bytes" -> file.put("bytes", PickPublicationManifest.MAX_ATTACHMENT_BYTES + 1);
            case "coerced-bytes" -> file.put("bytes", "5");
            case "fractional-bytes" -> file.put("bytes", 5.5);
            case "file-hash" -> file.put("sha256", "TEST-PRIVATE-MANIFEST-VALUE");
            case "unknown-file-field" -> file.put("extra", "TEST-PRIVATE-MANIFEST-VALUE");
            case "not-json" -> { }
            default -> throw new AssertionError(problem);
        }
        String manifest = problem.equals("not-json") ? "TEST-PRIVATE-MANIFEST-VALUE" : root.toString();
        // when / then
        assertThatThrownBy(() -> PickPublicationManifest.read(manifest, mapper)).isInstanceOf(BusinessRuleException.class)
                .hasMessageNotContaining("TEST-PRIVATE-MANIFEST-VALUE");
    }

    private String valid() {
        return "{\"format\":1,\"contentHash\":\"" + EMPTY + "\",\"attachments\":[{\"filename\":\"settings-race-1.png\",\"bytes\":5,\"sha256\":\"" + IMAGE + "\"}]}";
    }
}
