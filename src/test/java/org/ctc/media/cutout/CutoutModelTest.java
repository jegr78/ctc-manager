package org.ctc.media.cutout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CutoutModelTest {

    private static CutoutModel model;

    @BeforeAll
    static void loadModel() {
        model = new CutoutModel(System.getProperty("app.trailer.cutout-model-path",
                System.getenv().getOrDefault("APP_TRAILER_CUTOUT_MODEL_PATH", "data/models/cutout.onnx")));
    }

    @AfterAll
    static void release() {
        model.release();
    }

    @Test
    void givenStudioFixture_whenCutout_thenCornersTransparentAndCentreOpaque() {
        // when
        BufferedImage cut = model.cutout(studioFixture());

        // then
        int w = cut.getWidth();
        int h = cut.getHeight();
        assertThat(cut.getColorModel().hasAlpha()).isTrue();
        for (int[] corner : new int[][] {{0, 0}, {w - 1, 0}, {0, h - 1}, {w - 1, h - 1}}) {
            assertThat(alpha(cut, corner[0], corner[1])).as("corner %s,%s transparent", corner[0], corner[1]).isZero();
        }
        assertThat(alpha(cut, w / 2, h / 2)).as("car body opaque").isGreaterThan(200);
    }

    @Test
    void givenStudioFixture_whenCutout_thenTrimmedToBoundingBoxWithMargin() {
        // when
        BufferedImage cut = model.cutout(studioFixture());

        // then
        assertThat(cut.getWidth()).as("trimmed width: 340 px body plus 2 x 24 px margin, give or take mask softness")
                .isBetween(340 + 2 * 24 - 20, 340 + 2 * 24 + 20);
        assertThat(cut.getHeight()).isLessThan(360);
    }

    @Test
    void givenMissingModelFile_whenCutout_thenThrowsUnavailable() {
        // given
        var missing = new CutoutModel(Path.of("does-not-exist.onnx").toString());

        // when / then
        assertThatThrownBy(() -> missing.cutout(studioFixture()))
                .isInstanceOf(CutoutModelUnavailableException.class)
                .hasMessage("Cutout model not installed");
    }

    @Test
    void givenMissingModelFile_whenIsAvailable_thenFalse() {
        // when / then
        assertThat(new CutoutModel("does-not-exist.onnx").isAvailable()).isFalse();
        assertThat(model.isAvailable()).as("pinned model is installed for tests").isTrue();
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }

    private static BufferedImage studioFixture() {
        var image = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(10, 10, 10), 0, 180, new Color(50, 50, 50), true));
        g.fillRect(0, 0, 640, 360);
        g.setColor(new Color(200, 20, 30));
        g.fillRoundRect(150, 150, 340, 100, 80, 80);
        g.setColor(new Color(170, 15, 25));
        g.fillPolygon(new int[] {230, 290, 400, 440}, new int[] {150, 105, 105, 150}, 4);
        g.setColor(new Color(25, 25, 25));
        g.fillOval(190, 220, 70, 70);
        g.fillOval(380, 220, 70, 70);
        g.dispose();
        return image;
    }
}
