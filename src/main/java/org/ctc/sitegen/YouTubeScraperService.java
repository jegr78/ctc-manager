package org.ctc.sitegen;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.regex.Pattern;

@Slf4j
@Service
public class YouTubeScraperService {

    private static final Pattern VIDEO_ID_PATTERN =
            Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11,})\"");

    // package-private for testability via @Spy (per D-02, D-04)
    String fetchChannelHtml(String channelUrl) throws IOException {
        return Jsoup.connect(channelUrl)
                .timeout(10_000)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .get()
                .html();
    }

    public String scrapeVideoId(String channelUrl, String fallbackVideoId) {
        try {
            String html = fetchChannelHtml(channelUrl);
            var matcher = VIDEO_ID_PATTERN.matcher(html);
            if (matcher.find()) {
                String videoId = matcher.group(1);
                log.info("Scraped YouTube videoId: {} from {}", videoId, channelUrl);
                return videoId;
            }
            log.warn("No videoId found on YouTube channel page: {}", channelUrl);
        } catch (IOException e) {
            log.warn("Failed to scrape YouTube channel page {}: {}", channelUrl, e.getMessage());
        }
        return fallbackVideoId;
    }
}
