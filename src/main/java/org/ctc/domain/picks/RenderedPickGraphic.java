package org.ctc.domain.picks;

import java.util.Set;
import org.ctc.domain.model.PickDisclosureField;

/** Frozen image bytes and the fields proven by the exact captured template. */
public record RenderedPickGraphic(byte[] png, String templateHash, Set<PickDisclosureField> fields) {
    public RenderedPickGraphic {
        png = png.clone(); fields = Set.copyOf(fields);
    }
    @Override public byte[] png() { return png.clone(); }
    @Override public String toString() { return "RenderedPickGraphic[internal]"; }
}
