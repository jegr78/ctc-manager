package org.ctc.domain.service;

import java.util.Locale;

/** Recognises PNG, JPEG and WebP files by their leading bytes and reads their pixel size without decoding them. */
public final class ImageContent {
    private ImageContent() { }

    public enum Format {
        PNG, JPEG, WEBP;

        /** The format a filename declares through its extension. */
        public static Format ofFilename(String filename) {
            String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".png")) return PNG;
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return JPEG;
            if (lower.endsWith(".webp")) return WEBP;
            throw new IllegalArgumentException("Unsupported image file type: " + filename);
        }

        public boolean matches(byte[] data) {
            return switch (this) {
                case PNG -> startsWith(data, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
                case JPEG -> startsWith(data, 0, 0xFF, 0xD8, 0xFF);
                case WEBP -> startsWith(data, 0, 'R', 'I', 'F', 'F') && startsWith(data, 8, 'W', 'E', 'B', 'P');
            };
        }

        public String extension() {
            return switch (this) {
                case PNG -> ".png";
                case JPEG -> ".jpg";
                case WEBP -> ".webp";
            };
        }
    }

    public record Dimensions(int width, int height) { }

    /** The format the leading bytes show, or null for anything else. */
    public static Format detect(byte[] data) {
        for (var format : Format.values()) if (format.matches(data)) return format;
        return null;
    }

    /** The pixel size stored in the header; throws when the header is truncated or unknown. */
    public static Dimensions dimensions(byte[] data) {
        var format = detect(data);
        if (format == null) throw new IllegalArgumentException("Only PNG, JPEG and WebP images are supported");
        var size = switch (format) {
            case PNG -> data.length >= 24 && startsWith(data, 12, 'I', 'H', 'D', 'R') ? new Dimensions(int32(data, 16), int32(data, 20)) : null;
            case JPEG -> jpeg(data);
            case WEBP -> webp(data);
        };
        if (size == null || size.width() <= 0 || size.height() <= 0) throw new IllegalArgumentException("The image size could not be read");
        return size;
    }

    private static Dimensions jpeg(byte[] data) {
        int position = 2;
        while (position + 9 < data.length) {
            if ((data[position] & 0xFF) != 0xFF) return null;
            int marker = data[position + 1] & 0xFF;
            if (marker == 0xFF) { position++; continue; }
            int length = uint16(data, position + 2);
            boolean frame = marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (frame) return new Dimensions(uint16(data, position + 7), uint16(data, position + 5));
            if (length < 2) return null;
            position += 2 + length;
        }
        return null;
    }

    private static Dimensions webp(byte[] data) {
        if (data.length < 30) return null;
        if (startsWith(data, 12, 'V', 'P', '8', ' ')) {
            return new Dimensions(uint16le(data, 26) & 0x3FFF, uint16le(data, 28) & 0x3FFF);
        }
        if (startsWith(data, 12, 'V', 'P', '8', 'L')) {
            int b1 = data[21] & 0xFF, b2 = data[22] & 0xFF, b3 = data[23] & 0xFF, b4 = data[24] & 0xFF;
            return new Dimensions(1 + (((b2 & 0x3F) << 8) | b1), 1 + (((b4 & 0x0F) << 10) | (b3 << 2) | ((b2 & 0xC0) >> 6)));
        }
        if (startsWith(data, 12, 'V', 'P', '8', 'X')) {
            return new Dimensions(1 + uint24le(data, 24), 1 + uint24le(data, 27));
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int offset, int... expected) {
        if (data == null || data.length < offset + expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if ((data[offset + index] & 0xFF) != expected[index]) return false;
        }
        return true;
    }

    private static int int32(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16) | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    private static int uint16(byte[] data, int offset) { return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF); }

    private static int uint16le(byte[] data, int offset) { return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8); }

    private static int uint24le(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8) | ((data[offset + 2] & 0xFF) << 16);
    }
}
