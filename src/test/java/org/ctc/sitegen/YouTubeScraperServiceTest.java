package org.ctc.sitegen;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class YouTubeScraperServiceTest {

    @Spy
    private YouTubeScraperService service;

    @Test
    void givenValidChannelPage_whenScrapeVideoId_thenReturnsFirstVideoId() throws IOException {
        // given
        var html = "<html><script>var ytInitialData = {\"videoId\":\"dQw4w9WgXcW\",\"title\":\"test\"}</script></html>";
        doReturn(html).when(service).fetchChannelHtml(any());

        // when
        var result = service.scrapeVideoId("https://www.youtube.com/@CommunityTeamCup", "fallback");

        // then
        assertThat(result).isEqualTo("dQw4w9WgXcW");
    }

    @Test
    void givenNoVideoIdInPage_whenScrapeVideoId_thenReturnsFallback() throws IOException {
        // given
        var html = "<html><body>No videos here</body></html>";
        doReturn(html).when(service).fetchChannelHtml(any());

        // when
        var result = service.scrapeVideoId("https://www.youtube.com/@CommunityTeamCup", "my-fallback-id");

        // then
        assertThat(result).isEqualTo("my-fallback-id");
    }

    @Test
    void givenIOException_whenScrapeVideoId_thenReturnsFallback() throws IOException {
        // given
        doThrow(new IOException("Connection timed out")).when(service).fetchChannelHtml(any());

        // when
        var result = service.scrapeVideoId("https://www.youtube.com/@CommunityTeamCup", "fallback-id");

        // then
        assertThat(result).isEqualTo("fallback-id");
    }

    @Test
    void givenNullChannelUrl_whenScrapeVideoId_thenReturnsFallback() {
        // when
        var result = service.scrapeVideoId(null, "fallback-id");

        // then
        assertThat(result).isEqualTo("fallback-id");
    }

    @Test
    void givenBlankChannelUrl_whenScrapeVideoId_thenReturnsFallback() {
        // when
        var result = service.scrapeVideoId("  ", "fallback-id");

        // then
        assertThat(result).isEqualTo("fallback-id");
    }

    @Test
    void givenMultipleVideoIds_whenScrapeVideoId_thenReturnsFirstMatch() throws IOException {
        // given
        var html = "<html><script>{\"videoId\":\"FIRST_ID_1234\",\"videoId\":\"SECOND_ID_567\"}</script></html>";
        doReturn(html).when(service).fetchChannelHtml(any());

        // when
        var result = service.scrapeVideoId("https://www.youtube.com/@CommunityTeamCup", "fallback");

        // then
        assertThat(result).isEqualTo("FIRST_ID_1234");
    }
}
