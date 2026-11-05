package org.ctc.domain.model;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LegacyLobbyFingerprintTest {
    @Test
    void givenEmptyLegacySetup_whenFingerprinting_thenKeepPreviouslyPublishedCanonicalBytes() {
        // given / when / then
        assertThat(PickSetupSnapshot.from(new Race()).fingerprint())
                .isEqualTo("e2498909dffc96e60ef65dab90d386ac70fc7a06db1f28f4d5f58b22aa9492b3");
    }

    @Test
    void givenCompleteLegacySetupAndPublication_whenFingerprinting_thenKeepGoldenHashesIncludingFieldBoundaries() {
        // given
        var track = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var car = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var settings = new RaceSettingsValues(12, 2, 3, 4, "Default", 1, TimeProgressionMode.MULTIPLIER, 0,
                "S01:Sunset", "Night", "RS, RM", "RM");
        var content = new PickPublicationContent(track, car, null, "Test track", "Test car", "Test entry",
                12, 2, 3, 4, "Default", 1, TimeProgressionMode.MULTIPLIER, 0, "S01:Sunset", "Night", "RS, RM", "RM");
        // when / then
        assertThat(new PickSetupSnapshot(track, car, null, settings).fingerprint())
                .isEqualTo("e1f14b181bce0f9907f0b522f3434f3d92c558f5f0623ccd283a4635fa969ee7");
        assertThat(content.fingerprint()).isEqualTo("70a97f251b1fb7d794709555f0b04b266583c6a30d5d90438b76452eece28b72");
    }
}
