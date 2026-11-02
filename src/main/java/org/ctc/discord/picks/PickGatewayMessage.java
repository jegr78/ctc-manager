package org.ctc.discord.picks;

import java.util.List;

public record PickGatewayMessage(String content, List<Button> buttons, Selection selection) {
    public PickGatewayMessage {
        buttons = List.copyOf(buttons);
        if (content == null || content.isEmpty() || content.length() > 2000 || buttons.size() > 5) {
            throw new IllegalArgumentException("Private message exceeds Discord limits");
        }
    }
    @Override public String toString() { return "PickGatewayMessage[private]"; }
    public record Button(String id, String label) { }
    public record Selection(String id, String placeholder, List<PickGatewayModal.Option> options) {
        public Selection {
            options = List.copyOf(options);
            if (options.isEmpty() || options.size() > 25) throw new IllegalArgumentException("Select requires 1 to 25 choices");
        }
    }
}
