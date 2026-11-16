package org.ctc.discord.picks;

import java.util.ArrayList;
import java.util.Locale;
import org.ctc.domain.stats.DriverStats;
import org.ctc.domain.stats.StatsPeriod;
import org.ctc.domain.stats.TeamStats;

/** Renders stats figures as Discord embeds with escaped names. */
final class StatsEmbeds {
    private StatsEmbeds() { }

    static PickGatewayMessage.Embed driver(DriverStats stats, String url) {
        var fields = new ArrayList<PickGatewayMessage.Field>();
        if (!stats.teams().isEmpty()) {
            String teams = String.join(", ", stats.teams().stream().map(PickInteractionViews::escape).toList())
                    + (stats.moreTeams() > 0 ? " +" + stats.moreTeams() : "");
            fields.add(new PickGatewayMessage.Field(stats.period().alltime() ? "Teams" : "Team", teams, false));
        }
        if (stats.rank() > 0) {
            fields.add(new PickGatewayMessage.Field("Rank", "Rank " + stats.rank() + " of " + stats.ranked(), false));
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
        return new PickGatewayMessage.Embed(PickInteractionViews.escape(stats.psnId()), url, scope(stats.period()), fields, null);
    }

    static PickGatewayMessage.Embed team(TeamStats stats, String url) {
        var fields = new ArrayList<PickGatewayMessage.Field>();
        String rank = "Rank " + stats.rank() + " of " + stats.ranked() + (stats.group() == null ? "" : " · " + PickInteractionViews.escape(stats.group()));
        fields.add(new PickGatewayMessage.Field("Rank", rank, false));
        fields.add(new PickGatewayMessage.Field("Matches", count(stats.played(), "match", "matches") + " · " + count(stats.races(), "race") + " · "
                + count(stats.wins(), "win") + " · "
                + count(stats.draws(), "draw") + " · " + count(stats.losses(), "loss", "losses") + " · " + stats.winRate() + "% won", false));
        fields.add(new PickGatewayMessage.Field("Points", stats.pointsFor() + " scored · " + stats.pointsAgainst() + " conceded · "
                + (stats.difference() > 0 ? "+" : "") + stats.difference() + " difference · " + stats.matchPoints() + " match points", false));
        if (stats.playoffResult() != null) fields.add(new PickGatewayMessage.Field("Playoffs", PickInteractionViews.escape(stats.playoffResult()), false));
        if (stats.period().alltime()) {
            var history = new ArrayList<String>();
            history.add(count(stats.seasons(), "season"));
            if (stats.bestResult() != null) history.add("best " + PickInteractionViews.escape(stats.bestResult()));
            if (stats.playoffAppearances() > 0 || stats.titles() > 0) {
                history.add(count(stats.playoffAppearances(), "playoff appearance"));
                history.add(count(stats.titles(), "title"));
            }
            fields.add(new PickGatewayMessage.Field("History", String.join(" · ", history), false));
        }
        if (!stats.topDrivers().isEmpty()) {
            fields.add(new PickGatewayMessage.Field("Top drivers", String.join(" · ", stats.topDrivers().stream()
                    .map(driver -> PickInteractionViews.escape(driver.psnId()) + " " + driver.points() + " pts").toList()), false));
        }
        return new PickGatewayMessage.Embed(PickInteractionViews.escape(stats.name()), url, scope(stats.period()), fields, null);
    }

    static String scope(StatsPeriod period) {
        if (period.fallback()) return "Alltime · no season is active";
        return PickInteractionViews.escape(period.title());
    }

    private static String count(int value, String noun) {
        return count(value, noun, noun + "s");
    }

    private static String count(int value, String noun, String plural) {
        return value + " " + (value == 1 ? noun : plural);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
