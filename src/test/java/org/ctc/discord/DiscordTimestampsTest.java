package org.ctc.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DiscordTimestamps. RED phase: fails to compile until Task 8
 * lands DiscordTimestamps at org.ctc.discord.DiscordTimestamps.
 *
 * Format contract (Discord docs):
 *   F = Long date+time (Tuesday, 21 May 2026 14:30)
 *   f = Short date+time
 *   D = Long date
 *   d = Short date
 *   t = Short time
 *   R = Relative
 *
 * Epoch derivation: dt.atZone(zone).toEpochSecond().
 *   2026-05-21T14:30:00 in Europe/Berlin (UTC+2 in May DST)
 *     → 12:30:00 UTC → epoch 1747830600.
 *   Same LocalDateTime in UTC → 14:30:00 UTC → epoch 1747837800 (differs by 7200s).
 */
class DiscordTimestampsTest {

	private static final LocalDateTime DT = LocalDateTime.of(2026, 5, 21, 14, 30, 0);
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-21T00:00:00Z"), ZoneOffset.UTC);
	private static final long EPOCH_BERLIN = DT.atZone(ZoneId.of("Europe/Berlin")).toEpochSecond();
	private static final long EPOCH_UTC = DT.atZone(ZoneOffset.UTC).toEpochSecond();

	@Test
	void givenBerlinTzAndKnownDateTime_whenLongDateTime_thenReturnsCorrectEpochAndStyleF() {
		// given
		DiscordTimestamps ts = new DiscordTimestamps(CLOCK, "Europe/Berlin");

		// when
		String actual = ts.longDateTime(DT);

		// then
		assertThat(actual).isEqualTo("<t:" + EPOCH_BERLIN + ":F>");
	}

	@Test
	void givenBerlinTzAndKnownDateTime_whenShortDateTime_thenReturnsStyleF() {
		DiscordTimestamps ts = new DiscordTimestamps(CLOCK, "Europe/Berlin");
		assertThat(ts.shortDateTime(DT)).isEqualTo("<t:" + EPOCH_BERLIN + ":f>");
	}

	@Test
	void givenBerlinTzAndKnownDateTime_whenLongDate_thenReturnsStyleD() {
		DiscordTimestamps ts = new DiscordTimestamps(CLOCK, "Europe/Berlin");
		assertThat(ts.longDate(DT)).isEqualTo("<t:" + EPOCH_BERLIN + ":D>");
	}

	@Test
	void givenBerlinTzAndKnownDateTime_whenShortDate_thenReturnsStyled() {
		DiscordTimestamps ts = new DiscordTimestamps(CLOCK, "Europe/Berlin");
		assertThat(ts.shortDate(DT)).isEqualTo("<t:" + EPOCH_BERLIN + ":d>");
	}

	@Test
	void givenBerlinTzAndKnownDateTime_whenShortTime_thenReturnsStyleT() {
		DiscordTimestamps ts = new DiscordTimestamps(CLOCK, "Europe/Berlin");
		assertThat(ts.shortTime(DT)).isEqualTo("<t:" + EPOCH_BERLIN + ":t>");
	}

	@Test
	void givenBerlinTzAndKnownDateTime_whenRelative_thenReturnsStyleR() {
		DiscordTimestamps ts = new DiscordTimestamps(CLOCK, "Europe/Berlin");
		assertThat(ts.relative(DT)).isEqualTo("<t:" + EPOCH_BERLIN + ":R>");
	}

	@Test
	void givenSameDateTimeInUtc_whenLongDateTime_thenEpochDiffersFromBerlinByDstOffset() {
		// given — same wall-clock LocalDateTime interpreted in UTC produces a different epoch
		DiscordTimestamps berlin = new DiscordTimestamps(CLOCK, "Europe/Berlin");
		DiscordTimestamps utc = new DiscordTimestamps(CLOCK, "UTC");

		// when
		String berlinOut = berlin.longDateTime(DT);
		String utcOut = utc.longDateTime(DT);

		// then — Berlin in May is UTC+2 (DST), so its epoch is 7200s earlier than the same
		// wall-clock interpreted as UTC.
		assertThat(berlinOut).isEqualTo("<t:" + EPOCH_BERLIN + ":F>");
		assertThat(utcOut).isEqualTo("<t:" + EPOCH_UTC + ":F>");
		assertThat(EPOCH_UTC - EPOCH_BERLIN).isEqualTo(7200L);
	}
}
