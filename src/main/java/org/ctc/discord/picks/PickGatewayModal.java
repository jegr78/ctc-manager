package org.ctc.discord.picks;

import java.util.List;

public record PickGatewayModal(String id, String title, List<Field> fields) {
    public PickGatewayModal { fields = List.copyOf(fields); }
    @Override public String toString() { return "PickGatewayModal[id=" + id + "]"; }
    public sealed interface Field permits Text, Selection { String id(); String label(); }
    public record Text(String id, String label, boolean paragraph, String value, int minLength, int maxLength, boolean required) implements Field { }
    public record Selection(String id, String label, List<Option> options, int minValues, int maxValues) implements Field {
        public Selection { options = List.copyOf(options); }
    }
    public record Option(String label, String value) { }
}
