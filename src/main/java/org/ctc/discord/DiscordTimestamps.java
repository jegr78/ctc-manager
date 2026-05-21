package org.ctc.discord;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DiscordTimestamps {

	private final Clock clock;
	private final ZoneId zone;

	public DiscordTimestamps(Clock clock, @Value("${app.timezone:Europe/Berlin}") String zoneIdName) {
		this.clock = clock;
		this.zone = ZoneId.of(zoneIdName);
	}

	public String longDateTime(LocalDateTime dt) {
		return format(dt, "F");
	}

	public String shortDateTime(LocalDateTime dt) {
		return format(dt, "f");
	}

	public String longDate(LocalDateTime dt) {
		return format(dt, "D");
	}

	public String shortDate(LocalDateTime dt) {
		return format(dt, "d");
	}

	public String shortTime(LocalDateTime dt) {
		return format(dt, "t");
	}

	public String relative(LocalDateTime dt) {
		return format(dt, "R");
	}

	Clock clock() {
		return clock;
	}

	private String format(LocalDateTime dt, String style) {
		long epoch = dt.atZone(zone).toEpochSecond();
		return "<t:" + epoch + ":" + style + ">";
	}
}
