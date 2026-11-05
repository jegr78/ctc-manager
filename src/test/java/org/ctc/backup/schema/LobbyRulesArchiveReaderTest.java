package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class LobbyRulesArchiveReaderTest {
    final ObjectMapper mapper = new ObjectMapper();

    @Test
    void givenCurrentTyped2027Payload_whenReading_thenRetainProfileAndBothExplicitChoices() {
        // given
        var payload = payload();
        // when
        var decoded = assertDoesNotThrow(() -> PickPayloadReader.read(mapper.valueToTree(payload)));
        // then
        assertThat(decoded).isEqualTo(payload);
        assertThat(decoded.lobbyRulesProfile()).isEqualTo(LobbyRulesProfile.CTC_2027);
        assertThat(decoded.startType()).isEqualTo(RaceStartType.ROLLING);
        assertThat(decoded.slipstream()).isEqualTo(RaceSlipstream.WEAK);
    }

    @Test
    void givenCaptured2027Publication_whenReading_thenKeepCapturedValuesAndExactFingerprint() {
        // given
        var payload = payload();
        var content = new PickPublicationContent(payload.trackId(), payload.carId(), null, "Test track", "Test car", null,
                12, 2, 3, 4, "Default", 1, TimeProgressionMode.MULTIPLIER, 0, "S01", "Night", "RS, RM", "RM",
                LobbyRulesProfile.CTC_2027, RaceStartType.ROLLING, RaceSlipstream.WEAK);
        // when
        var decoded = assertDoesNotThrow(() -> PickPublicationContentReader.read(mapper.valueToTree(content)));
        // then
        assertThat(decoded).isEqualTo(content);
        assertThat(decoded.fingerprint()).isEqualTo(content.fingerprint());
    }

    @Test
    void givenHistoricalPublicationWithoutRuleFields_whenNormalizingSchema13_thenKeepOriginalHashMaskAndTemplateBytes() {
        // given
        var legacy = new PickPublicationContent(null, null, null, "Test old track", "Test old car", null,
                12, 2, 3, 4, "Default", 1, TimeProgressionMode.MULTIPLIER, 0, "S01", "Night", "RS, RM", "RM");
        ObjectNode content = mapper.valueToTree(legacy);
        content.remove(java.util.List.of("lobbyRulesProfile", "startType", "slipstream"));
        var row = mapper.createObjectNode().put("fieldMask", 8191).put("requestManifest", "Test exact old template bytes\n<raw>");
        row.set("content", content);
        // when
        var normalized = LobbyRulesArchive.normalize(13, "pick_publication_attempts", row);
        var restored = PickPublicationContentReader.read(normalized.get("content"));
        // then
        assertThat(restored.fingerprint()).isEqualTo(legacy.fingerprint());
        assertThat(restored.lobbyRulesProfile()).isEqualTo(LobbyRulesProfile.LEGACY);
        assertThat(normalized.get("fieldMask").intValue()).isEqualTo(8191);
        assertThat(normalized.get("requestManifest")).isEqualTo(row.get("requestManifest"));
        assertThat(row.get("content").has("lobbyRulesProfile")).isFalse();
        var visible = restored.project(UUID.randomUUID(), PickDisclosureField.fields(8191)).settings();
        assertThat(visible.startType()).isNull(); assertThat(visible.slipstream()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"lobbyRulesProfile,missing", "lobbyRulesProfile,null", "lobbyRulesProfile,boolean", "lobbyRulesProfile,unknown",
            "startType,missing", "startType,null", "startType,boolean", "startType,unknown",
            "slipstream,missing", "slipstream,null", "slipstream,boolean", "slipstream,unknown"})
    void givenCurrentSchemaWithMissingOrMalformedChoices_whenNormalizing_thenNeverInventValues(String field, String problem) {
        // given
        ObjectNode payload = mapper.valueToTree(payload());
        switch (problem) {
            case "missing" -> payload.remove(field);
            case "null" -> payload.putNull(field);
            case "boolean" -> payload.put(field, true);
            case "unknown" -> payload.put(field, "Test private invalid option");
            default -> throw new AssertionError(problem);
        }
        var row = mapper.createObjectNode(); row.set("payload", payload);
        // when / then
        assertThatThrownBy(() -> LobbyRulesArchive.normalize(14, "matchday_pick_revisions", row))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("Test private invalid option");
    }

    @Test
    void givenOldSchemaClaimingNewChoicesOrDisclosureBits_whenNormalizing_thenRejectUnsupportedClaims() {
        // given
        var row = mapper.createObjectNode(); row.set("payload", mapper.valueToTree(payload()));
        // when / then
        assertThatThrownBy(() -> LobbyRulesArchive.normalize(13, "matchday_pick_revisions", row)).isInstanceOf(IllegalArgumentException.class);
        var publication = mapper.createObjectNode().put("fieldMask", 8192);
        publication.set("content", mapper.createObjectNode());
        assertThatThrownBy(() -> LobbyRulesArchive.normalize(13, "pick_publication_attempts", publication)).isInstanceOf(IllegalArgumentException.class);
    }

    PickPayload payload() {
        return new PickPayload(UUID.randomUUID(), UUID.randomUUID(), null, 12, 2, 3, 4, PickInitialFuel.DEFAULT, 1,
                TimeProgressionMode.MULTIPLIER, 0, "S01", "Night", Set.of(PickTyre.RS, PickTyre.RM), Set.of(PickTyre.RM),
                LobbyRulesProfile.CTC_2027, RaceStartType.ROLLING, RaceSlipstream.WEAK);
    }
}
