package org.ctc.sitegen;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
