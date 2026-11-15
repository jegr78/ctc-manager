package org.ctc.discord.picks;

import java.util.ArrayList;
import java.util.Locale;
import org.ctc.domain.stats.DriverStats;
import org.ctc.domain.stats.StatsPeriod;

/** Renders stats figures as Discord embeds with escaped names. */
final class StatsEmbeds {
    private StatsEmbeds() { }

    static PickGatewayMessage.Embed driver(DriverStats stats) {
        var fields = new ArrayList<PickGatewayMessage.Field>();
        if (!stats.teams().isEmpty()) {
            String teams = String.join(", ", stats.teams().stream().map(PickInteractionViews::escape).toList())
                    + (stats.moreTeams() > 0 ? " +" + stats.moreTeams() : "");
            fields.add(new PickGatewayMessage.Field(stats.period().alltime() ? "Teams" : "Team", teams, false));
        }
        if (stats.rank() > 0) {
            fields.add(new PickGatewayMessage.Field("Rank", "Rank " + stats.rank() + " of " + stats.ranked() + " · " + stats.points() + " pts", false));
        }
        fields.add(new PickGatewayMessage.Field("Races", count(stats.races(), "race") + " · " + count(stats.wins(), "win") + " · "
                + count(stats.podiums(), "podium") + " · " + count(stats.poles(), "pole") + " · " + count(stats.fastestLaps(), "fastest lap"), false));
        fields.add(new PickGatewayMessage.Field("Positions", "Best " + (stats.bestPosition() == 0 ? "-" : "P" + stats.bestPosition())
                + " · Average " + (stats.averagePosition() == null ? "-" : "P" + decimal(stats.averagePosition())), false));
        fields.add(new PickGatewayMessage.Field("Points", stats.points() + " total · " + decimal(stats.averagePoints()) + " per race", false));
        var extras = new ArrayList<String>();
        if (stats.guestRaces() > 0) extras.add(count(stats.guestRaces(), "guest race"));
        if (stats.disqualifications() > 0) extras.add(count(stats.disqualifications(), "DSQ"));
        if (stats.period().alltime()) extras.add(count(stats.seasons(), "season"));
        if (!extras.isEmpty()) fields.add(new PickGatewayMessage.Field("More", String.join(" · ", extras), false));
        return new PickGatewayMessage.Embed(PickInteractionViews.escape(stats.psnId()), null, scope(stats.period()), fields, null);
    }

    static String scope(StatsPeriod period) {
        if (period.fallback()) return "Alltime · no season is active";
        return PickInteractionViews.escape(period.label());
    }

    private static String count(int value, String noun) {
        return value + " " + noun + (value == 1 ? "" : "s");
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
