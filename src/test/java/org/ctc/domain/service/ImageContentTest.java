package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.ctc.domain.service.ImageContent.Dimensions;
import org.ctc.domain.service.ImageContent.Format;
import org.ctc.testsupport.TestImages;
import org.junit.jupiter.api.Test;

class ImageContentTest {

    @Test
    void givenEverySupportedHeader_whenReadingTheSize_thenReturnWidthAndHeight() {
        // when / then
        assertThat(ImageContent.dimensions(TestImages.png(1920, 1080))).isEqualTo(new Dimensions(1920, 1080));
        assertThat(ImageContent.dimensions(TestImages.jpeg(3840, 2160))).as("skips APP0 before the frame").isEqualTo(new Dimensions(3840, 2160));
        assertThat(ImageContent.dimensions(TestImages.webpExtended(2048, 1152))).isEqualTo(new Dimensions(2048, 1152));
        assertThat(ImageContent.dimensions(TestImages.webpLossless(640, 360))).isEqualTo(new Dimensions(640, 360));
        assertThat(ImageContent.dimensions(TestImages.webpLossy(800, 450))).isEqualTo(new Dimensions(800, 450));
    }

    @Test
    void givenLeadingBytes_whenDetecting_thenNameTheFormatOrNothing() {
        // when / then
        assertThat(ImageContent.detect(TestImages.png(1, 1))).isEqualTo(Format.PNG);
        assertThat(ImageContent.detect(TestImages.jpeg(1, 1))).isEqualTo(Format.JPEG);
        assertThat(ImageContent.detect(TestImages.webpLossy(1, 1))).isEqualTo(Format.WEBP);
        assertThat(ImageContent.detect("GIF89a".getBytes())).isNull();
        assertThat(ImageContent.detect(new byte[0])).isNull();
    }

    @Test
    void givenTruncatedOrForeignData_whenReadingTheSize_thenRejectIt() {
        // when / then
        assertThatThrownBy(() -> ImageContent.dimensions(Arrays.copyOf(TestImages.png(10, 10), 20)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("The image size could not be read");
        assertThatThrownBy(() -> ImageContent.dimensions(Arrays.copyOf(TestImages.jpeg(10, 10), 8)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("The image size could not be read");
        assertThatThrownBy(() -> ImageContent.dimensions(TestImages.png(0, 10)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("The image size could not be read");
        assertThatThrownBy(() -> ImageContent.dimensions("GIF89a........".getBytes()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Only PNG, JPEG and WebP images are supported");
    }
}
