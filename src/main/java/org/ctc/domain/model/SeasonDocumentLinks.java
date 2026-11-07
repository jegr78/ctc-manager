package org.ctc.domain.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class SeasonDocumentLinks {
    public static final int MAX_LENGTH = 2048;
    public static final String MESSAGE = "Enter an absolute HTTPS URL without credentials, or leave blank (maximum 2048 characters).";

    private SeasonDocumentLinks() { }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        String url = value.strip();
        if (url.length() > MAX_LENGTH || url.indexOf('\\') >= 0
                || url.chars().anyMatch(c -> Character.isISOControl(c) || Character.isWhitespace(c))
                || url.toLowerCase(Locale.ROOT).matches(".*%(?:0[0-9a-f]|1[0-9a-f]|7f).*")) {
            throw new IllegalArgumentException(MESSAGE);
        }
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException(MESSAGE);
            }
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException(MESSAGE, invalid);
        }
        return url;
    }
}
