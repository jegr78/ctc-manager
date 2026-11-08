package org.ctc.domain.model;

import java.util.List;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TrackEnvironmentProvenanceTest {
    @Test
    void givenImportedLayout_whenEncodingSourceAndFingerprint_thenRetainExactProfileRevisionAndOrderedTimes() {
        // given
        var p=new TrackEnvironmentProvenance("Europe-Middle East | Test", "2026-07-08T01:07:32Z", "2026-11-08T12:00:00Z", "a".repeat(64));
        var values=new TrackEnvironmentValues(List.of("Dawn","Night"),TrackRainCapability.RAIN);
        // when / then
        assertThat(TrackEnvironmentProvenance.decode(p.encode())).isEqualTo(p);
        assertThat(TrackEnvironmentProvenance.valuesHash(values)).isNotEqualTo(TrackEnvironmentProvenance.valuesHash(new TrackEnvironmentValues(List.of("Night","Dawn"),TrackRainCapability.RAIN)));
        assertThat(TrackEnvironmentProvenance.decode(null)).isNull();
    }
    @Test
    void givenMalformedSource_whenDecodingOrValidatingState_thenRejectAsDomainError() {
        // given / when / then
        for(String source:List.of("bad", "null", "{}", "{\"profile\":\"x\",\"profile\":\"y\"}", "{} {}")) assertThatThrownBy(() -> TrackEnvironmentProvenance.decode(source)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new TrackEnvironmentProvenance("Test\nlayout","bad","bad","x")).isInstanceOf(BusinessRuleException.class);
        var track=new Track("Test");track.setEnvironmentImportHash("a".repeat(64));
        assertThatThrownBy(() -> TrackEnvironmentProvenance.requireConsistent(track)).isInstanceOf(BusinessRuleException.class);
        var p=new TrackEnvironmentProvenance("Test", "2026-07-08T01:07:32Z", "2026-11-08T12:00:00Z", "a".repeat(64));
        track.setEnvironmentSource(p.encode());
        assertThatThrownBy(() -> TrackEnvironmentProvenance.requireConsistent(track)).isInstanceOf(BusinessRuleException.class);
        track.setEnvironmentOverride(true);
        assertThatCode(() -> TrackEnvironmentProvenance.requireConsistent(track)).doesNotThrowAnyException();
    }
}
