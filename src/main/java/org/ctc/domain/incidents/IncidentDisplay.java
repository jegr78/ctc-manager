package org.ctc.domain.incidents;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;

public final class IncidentDisplay {
    private IncidentDisplay() { }

    public static String reference(String canonical) {
        if (canonical != null && canonical.matches("INC-[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}")) {
            var id = UUID.fromString(canonical.substring(4));
            return "INC:" + Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(16)
                    .putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array());
        }
        return canonical;
    }

    public static String footer(String marker, Integer presentationVersion) {
        return presentationVersion == null ? marker : "-# ||" + marker + "||";
    }

    public static boolean hasMarker(String content, String marker) {
        return content != null && (content.endsWith("\n" + marker) || content.endsWith("\n" + footer(marker, 1)));
    }

    public static boolean hasCaseMarker(String content, String reference) {
        int line = content == null ? -1 : content.lastIndexOf('\n');
        if (line < 0) return false;
        String footer = content.substring(line + 1);
        return footer.startsWith("[CTC " + reference + " ") || footer.startsWith("-# ||[CTC " + reference + " ");
    }
}
