package org.ctc.sitegen;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "ctc.site")
public class SiteProperties {

    private String outputDir;
    private List<LinkEntry> links = new ArrayList<>();
    private String youtubeChannelUrl = "https://www.youtube.com/@CommunityTeamCup";
    private String youtubeVideoId = "";

    @Getter
    @Setter
    public static class LinkEntry {
        private String name;
        private String url;
    }
}
