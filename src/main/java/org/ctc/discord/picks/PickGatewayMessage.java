package org.ctc.discord.picks;

import java.util.List;

public record PickGatewayMessage(String content, List<Button> buttons, Selection selection, Embed embed) {
    public PickGatewayMessage(String content, List<Button> buttons, Selection selection) { this(content, buttons, selection, null); }
    public PickGatewayMessage {
        buttons = List.copyOf(buttons);
        if (content == null) content = "";
        if ((content.isEmpty() && embed == null) || content.length() > 2000 || buttons.size() > 5) {
            throw new IllegalArgumentException("Private message exceeds Discord limits");
        }
    }
    @Override public String toString() { return "PickGatewayMessage[private]"; }
    public record Button(String id, String label) { }
    public record Selection(String id, String placeholder, List<PickGatewayModal.Option> options, int minValues, int maxValues) {
        public Selection(String id, String placeholder, List<PickGatewayModal.Option> options) { this(id, placeholder, options, 1, 1); }
        public Selection {
            options = List.copyOf(options);
            if (options.isEmpty() || options.size() > 25) throw new IllegalArgumentException("Select requires 1 to 25 choices");
            if (minValues < 0 || maxValues < 1 || minValues > maxValues || maxValues > options.size()) {
                throw new IllegalArgumentException("Select value range exceeds its choices");
            }
        }
    }
    /** A Discord embed; {@code url} links the title and may be null. */
    public record Embed(String title, String url, String description, List<Field> fields, String footer) {
        public Embed {
            fields = List.copyOf(fields);
            int total = length(title) + length(description) + length(footer)
                    + fields.stream().mapToInt(field -> field.name().length() + field.value().length()).sum();
            if (title == null || title.isEmpty() || title.length() > 256 || length(description) > 4096 || length(footer) > 2048
                    || fields.size() > 25 || total > 6000) {
                throw new IllegalArgumentException("Embed exceeds Discord limits");
            }
        }
        private static int length(String text) { return text == null ? 0 : text.length(); }
    }
    public record Field(String name, String value, boolean inline) {
        public Field {
            if (name == null || name.isEmpty() || name.length() > 256 || value == null || value.isEmpty() || value.length() > 1024) {
                throw new IllegalArgumentException("Embed field exceeds Discord limits");
            }
        }
    }
}
