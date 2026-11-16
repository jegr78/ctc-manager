package org.ctc.media.cutout;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Salient-object segmentation with the pinned ISNet model: returns the source pixels with the predicted alpha mask,
 * trimmed to the object plus a margin. The session loads on first use and is closed by {@link #release()}.
 */
@Slf4j
@Component
public class CutoutModel {

    private static final int INPUT = 1024;
    private static final float MEAN = 0.5f;
    private static final float STD = 1.0f;
    private static final int MARGIN = 24;
    private static final int TRIM_ALPHA = 16;

    private final Path modelPath;
    private OrtSession session;

    public CutoutModel(@Value("${app.trailer.cutout-model-path:data/models/cutout.onnx}") String modelPath) {
        this.modelPath = Path.of(modelPath);
    }

    public boolean isAvailable() {
        return Files.isRegularFile(modelPath);
    }

    public synchronized BufferedImage cutout(BufferedImage studio) {
        if (!isAvailable()) {
            throw new CutoutModelUnavailableException();
        }
        float[] mask = predict(studio);
        return trim(applyMask(studio, mask));
    }

    public synchronized void release() {
        if (session != null) {
            try {
                session.close();
            } catch (OrtException e) {
                log.warn("Closing the cutout model failed: {}", e.getMessage());
            }
            session = null;
        }
    }

    private float[] predict(BufferedImage studio) {
        var resized = scale(studio, INPUT, INPUT, BufferedImage.TYPE_INT_RGB);
        int pixels = INPUT * INPUT;
        float[] data = new float[3 * pixels];
        int max = 1;
        int[] rgb = resized.getRGB(0, 0, INPUT, INPUT, null, 0, INPUT);
        for (int value : rgb) {
            max = Math.max(max, Math.max((value >> 16) & 0xFF, Math.max((value >> 8) & 0xFF, value & 0xFF)));
        }
        for (int i = 0; i < pixels; i++) {
            data[i] = (((rgb[i] >> 16) & 0xFF) / (float) max - MEAN) / STD;
            data[pixels + i] = (((rgb[i] >> 8) & 0xFF) / (float) max - MEAN) / STD;
            data[2 * pixels + i] = ((rgb[i] & 0xFF) / (float) max - MEAN) / STD;
        }
        try {
            var env = OrtEnvironment.getEnvironment();
            var current = session();
            try (var input = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), new long[] {1, 3, INPUT, INPUT});
                 var result = current.run(Map.of(current.getInputNames().iterator().next(), input))) {
                float[][] raw = ((float[][][][]) result.get(0).getValue())[0][0];
                return normalise(raw);
            }
        } catch (OrtException e) {
            throw new IllegalStateException("Cutout inference failed", e);
        }
    }

    private OrtSession session() throws OrtException {
        if (session == null) {
            var options = new OrtSession.SessionOptions();
            session = OrtEnvironment.getEnvironment().createSession(modelPath.toString(), options);
            log.info("Loaded cutout model {}", modelPath.getFileName());
        }
        return session;
    }

    private static float[] normalise(float[][] raw) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float[] row : raw) {
            for (float v : row) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }
        float range = Math.max(max - min, 1e-6f);
        float[] out = new float[INPUT * INPUT];
        for (int y = 0; y < INPUT; y++) {
            for (int x = 0; x < INPUT; x++) {
                out[y * INPUT + x] = (raw[y][x] - min) / range;
            }
        }
        return out;
    }

    private static BufferedImage applyMask(BufferedImage studio, float[] mask) {
        var gray = new BufferedImage(INPUT, INPUT, BufferedImage.TYPE_BYTE_GRAY);
        var raster = gray.getRaster();
        for (int i = 0; i < mask.length; i++) {
            raster.setSample(i % INPUT, i / INPUT, 0, (int) (mask[i] * 255));
        }
        int w = studio.getWidth();
        int h = studio.getHeight();
        var alpha = scale(gray, w, h, BufferedImage.TYPE_BYTE_GRAY).getRaster();
        var out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, (alpha.getSample(x, y, 0) << 24) | (studio.getRGB(x, y) & 0xFFFFFF));
            }
        }
        return out;
    }

    private static BufferedImage trim(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) >= TRIM_ALPHA) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return image;
        }
        int x0 = Math.max(0, minX - MARGIN);
        int y0 = Math.max(0, minY - MARGIN);
        int x1 = Math.min(image.getWidth(), maxX + 1 + MARGIN);
        int y1 = Math.min(image.getHeight(), maxY + 1 + MARGIN);
        var trimmed = new BufferedImage(x1 - x0, y1 - y0, BufferedImage.TYPE_INT_ARGB);
        var g = trimmed.createGraphics();
        g.drawImage(image.getSubimage(x0, y0, x1 - x0, y1 - y0), 0, 0, null);
        g.dispose();
        return trimmed;
    }

    private static BufferedImage scale(BufferedImage source, int w, int h, int type) {
        var out = new BufferedImage(w, h, type);
        var g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
