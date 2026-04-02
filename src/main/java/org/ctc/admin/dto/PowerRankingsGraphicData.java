package org.ctc.admin.dto;

import java.util.List;

public record PowerRankingsGraphicData(
        String title,
        String subtitle,
        String ctcLogoBase64,
        String fontBase64,
        List<PowerRankingEntry> teams,
        List<PowerRankingEntry> leftColumn,
        List<PowerRankingEntry> rightColumn
) {

    public record PowerRankingEntry(
            int rank,
            String teamName,
            String teamShortName,
            String logoBase64,
            String primaryColor,
            String secondaryColor,
            String accentColor
    ) {}
}
