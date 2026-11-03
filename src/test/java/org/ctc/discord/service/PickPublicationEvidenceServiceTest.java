package org.ctc.discord.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.ctc.discord.DiscordWebhookClient;
import org.ctc.discord.dto.NamedAttachment;
import org.ctc.discord.dto.WebhookMessageEvidence;
import org.ctc.discord.dto.WebhookMessageEvidence.Attachment;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PickPublicationAttempt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class PickPublicationEvidenceServiceTest {
    static final String WEBHOOK = "123456789012345678", MESSAGE = "234567890123456789", CHANNEL = "345678901234567890";
    static final String URL = "https://discord.com/api/v10/webhooks/" + WEBHOOK + "/Test_capture-token";
    static final String IMAGE_URL = "https://cdn.discordapp.com/attachments/" + CHANNEL + "/456789012345678901/settings-race-1.png?ex=abcdef&is=abcdef&hm=Test_signature";
    final DiscordWebhookClient client = mock(DiscordWebhookClient.class);
    final ObjectMapper mapper = new ObjectMapper();
    final PickPublicationEvidenceService service = new PickPublicationEvidenceService(client, mapper);
    final byte[] image = "image".getBytes(StandardCharsets.UTF_8);
    String manifest;

    @BeforeEach void setUp() throws Exception {
        manifest = PickPublicationManifest.capture("Test explicit preview", List.of(new NamedAttachment("settings-race-1.png", image)), mapper);
        when(client.getMessageEvidence(URL, MESSAGE)).thenReturn(message(MESSAGE, WEBHOOK, CHANNEL, "Test explicit preview", 0, files()));
        when(client.downloadAttachment(IMAGE_URL, image.length)).thenReturn(image);
    }

    @Test
    void givenMatchingActualMessageAndImageBytes_whenVerifying_thenAcceptOnlyTheCapturedPhysicalOutput() throws Exception {
        // when
        assertDoesNotThrow(() -> service.verify(URL, MESSAGE, WEBHOOK, CHANNEL, manifest, PickPublicationAttempt.manifestHash(manifest)));
        // then
        verify(client).getMessageEvidence(URL, MESSAGE); verify(client).downloadAttachment(IMAGE_URL, image.length);
    }

    @ParameterizedTest
    @ValueSource(strings = {"message", "webhook", "channel", "content", "ephemeral", "deferred", "missing-file", "extra-file", "filename", "size", "bytes", "request-hash", "unknown-manifest"})
    void givenMismatchedOrUnverifiableOutput_whenReconciling_thenRejectWithoutReleasingPrivateValues(String problem) throws Exception {
        // given
        String captured = manifest, hash = PickPublicationAttempt.manifestHash(manifest);
        switch (problem) {
            case "message" -> when(client.getMessageEvidence(URL, MESSAGE)).thenReturn(message("999999999999999999", WEBHOOK, CHANNEL, "Test explicit preview", 0, files()));
            case "webhook" -> when(client.getMessageEvidence(URL, MESSAGE)).thenReturn(message(MESSAGE, "999999999999999999", CHANNEL, "Test explicit preview", 0, files()));
            case "channel" -> when(client.getMessageEvidence(URL, MESSAGE)).thenReturn(message(MESSAGE, WEBHOOK, "999999999999999999", "Test explicit preview", 0, files()));
            case "content" -> when(client.getMessageEvidence(URL, MESSAGE)).thenReturn(message(MESSAGE, WEBHOOK, CHANNEL, "TEST-PRIVATE-UNVERIFIED-CONTENT", 0, files()));
            case "ephemeral" -> when(client.getMessageEvidence(URL, MESSAGE)).thenReturn(message(MESSAGE, WEBHOOK, CHANNEL, "Test explicit preview", 64, files()));
            case "deferred" -> when(client.getMessageEvidence(URL, MESSAGE)).thenReturn(message(MESSAGE, WEBHOOK, CHANNEL, "Test explicit preview", 128, files()));
            case "missing-file" -> when(client.getMessageEvidence(URL, MESSAGE)).thenReturn(message(MESSAGE, WEBHOOK, CHANNEL, "Test explicit preview", 0, List.of()));
            case "extra-file" -> when(client.getMessageEvidence(URL, MESSAGE)).thenReturn(message(MESSAGE, WEBHOOK, CHANNEL, "Test explicit preview", 0, List.of(files().getFirst(), files().getFirst())));
            case "filename" -> when(client.getMessageEvidence(URL, MESSAGE)).thenReturn(message(MESSAGE, WEBHOOK, CHANNEL, "Test explicit preview", 0,
                    List.of(new Attachment("456789012345678901", "settings-race-2.png", image.length, IMAGE_URL))));
            case "size" -> when(client.getMessageEvidence(URL, MESSAGE)).thenReturn(message(MESSAGE, WEBHOOK, CHANNEL, "Test explicit preview", 0,
                    List.of(new Attachment("456789012345678901", "settings-race-1.png", image.length + 1, IMAGE_URL))));
            case "bytes" -> when(client.downloadAttachment(IMAGE_URL, image.length)).thenReturn("wrong".getBytes(StandardCharsets.UTF_8));
            case "request-hash" -> hash = "0".repeat(64);
            case "unknown-manifest" -> { captured = "{}"; hash = PickPublicationAttempt.manifestHash(captured); }
            default -> throw new AssertionError(problem);
        }
        String expected = captured, requestHash = hash;
        // when / then
        assertThatThrownBy(() -> service.verify(URL, MESSAGE, WEBHOOK, CHANNEL, expected, requestHash))
                .isInstanceOf(BusinessRuleException.class).hasMessageNotContaining("TEST-PRIVATE-UNVERIFIED-CONTENT")
                .hasMessageNotContaining("Test_capture-token").hasMessageNotContaining("Test_signature");
    }

    private List<Attachment> files() { return List.of(new Attachment("456789012345678901", "settings-race-1.png", image.length, IMAGE_URL)); }
    private WebhookMessageEvidence message(String id, String webhook, String channel, String content, int flags, List<Attachment> files) {
        return new WebhookMessageEvidence(id, channel, webhook, content, flags, files);
    }
}
