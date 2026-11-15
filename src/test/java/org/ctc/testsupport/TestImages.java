package org.ctc.testsupport;

import java.io.ByteArrayOutputStream;

/** Minimal image headers that carry a pixel size, for upload and parsing tests. */
public final class TestImages {
    private TestImages() { }

    public static byte[] png(int width, int height) {
        var out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13, 'I', 'H', 'D', 'R'});
        out.writeBytes(int32(width));
        out.writeBytes(int32(height));
        out.writeBytes(new byte[] {8, 6, 0, 0, 0});
        return out.toByteArray();
    }

    /** A JPEG with an APP0 segment before its baseline frame header. */
    public static byte[] jpeg(int width, int height) {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 4, 'J', 'F',
                (byte) 0xFF, (byte) 0xC0, 0, 17, 8, (byte) (height >> 8), (byte) height, (byte) (width >> 8), (byte) width, 3, 0, 0, 0, 0};
    }

    public static byte[] webpExtended(int width, int height) {
        byte[] data = riff("VP8X", 30);
        put24le(data, 24, width - 1);
        put24le(data, 27, height - 1);
        return data;
    }

    public static byte[] webpLossless(int width, int height) {
        byte[] data = riff("VP8L", 30);
        int w = width - 1, h = height - 1;
        data[20] = 0x2F;
        data[21] = (byte) w;
        data[22] = (byte) (((w >> 8) & 0x3F) | ((h & 0x03) << 6));
        data[23] = (byte) (h >> 2);
        data[24] = (byte) ((h >> 10) & 0x0F);
        return data;
    }

    public static byte[] webpLossy(int width, int height) {
        byte[] data = riff("VP8 ", 30);
        data[26] = (byte) width;
        data[27] = (byte) (width >> 8);
        data[28] = (byte) height;
        data[29] = (byte) (height >> 8);
        return data;
    }

    private static byte[] riff(String chunk, int length) {
        byte[] data = new byte[length];
        System.arraycopy("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, data, 0, 4);
        System.arraycopy(("WEBP" + chunk).getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, data, 8, 8);
        return data;
    }

    private static void put24le(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >> 8);
        data[offset + 2] = (byte) (value >> 16);
    }

    private static byte[] int32(int value) {
        return new byte[] {(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value};
    }
}
