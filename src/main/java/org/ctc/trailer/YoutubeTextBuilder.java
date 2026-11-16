package org.ctc.trailer;

import org.ctc.domain.model.MusicTrack;

/** Suggested YouTube title and description for a trailer; the description ends with the music credit. */
public final class YoutubeTextBuilder {

    private YoutubeTextBuilder() {
    }

    public record YoutubeText(String title, String description) {
        public String asFile() {
            return "Title: " + title + "\n\nDescription:\n" + description;
        }
    }

    public static YoutubeText build(TrailerPlan plan, MusicTrack music) {
        String title = plan.title() + " · " + plan.seasonLabel() + " | Community Team Cup Trailer";
        var description = new StringBuilder()
                .append(plan.title()).append(" of ").append(plan.seasonLabel()).append(" in the Community Team Cup.\n\n");
        for (var race : plan.races()) {
            description.append(race.pairingLabel()).append(": ").append(race.trackName()).append(" · ").append(race.carName())
                    .append(" (").append(race.legNumbers().size() == 1 ? "leg " : "legs ")
                    .append(String.join(" & ", race.legNumbers().stream().map(String::valueOf).toList())).append(")\n");
        }
        String credit = music.getCredit() != null && !music.getCredit().isBlank() ? music.getCredit()
                : music.getTitle() + " by " + music.getArtist();
        description.append("\nGame on!\n\nMusic: ").append(credit).append("\n");
        return new YoutubeText(title, description.toString());
    }
}
