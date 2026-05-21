package org.ctc.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DiscordEmojiCache. RED phase: fails to compile until Task 6
 * lands DiscordEmojiCache and DiscordConfig @Bean Clock.
 *
 * Contract:
 * - emojiFor(known) returns long-form &lt;:name:id&gt; while inside the 60-min TTL.
 * - emojiFor(unknown) returns fallback literal :name: — never throws.
 * - Clock advanced past TTL boundary triggers fallback.
 * - refresh(Map) atomically replaces all entries and returns the size.
 */
class DiscordEmojiCacheTest {

	private static final Instant T0 = Instant.parse("2026-05-21T10:00:00Z");

	@Test
	void givenRefreshedCache_whenEmojiForKnownShortName_thenReturnsLongForm() {
		// given
		DiscordEmojiCache cache = new DiscordEmojiCache(Clock.fixed(T0, ZoneOffset.UTC));
		cache.refresh(Map.of("flag_de", "<:flag_de:123456789012345678>"));

		// when
		String actual = cache.emojiFor("flag_de");

		// then
		assertThat(actual).isEqualTo("<:flag_de:123456789012345678>");
	}

	@Test
	void givenCacheMiss_whenEmojiForUnknown_thenReturnsFallbackLiteral() {
		// given
		DiscordEmojiCache cache = new DiscordEmojiCache(Clock.fixed(T0, ZoneOffset.UTC));

		// when — never refreshed, lookup unknown
		String actual = cache.emojiFor("trophy");

		// then
		assertThat(actual).isEqualTo(":trophy:");
	}

	@Test
	void givenClockAdvanced61Min_whenEmojiFor_thenReturnsFallback() {
		// given — populate at T0, advance Clock past the 60-min TTL
		MutableClock clock = new MutableClock(T0);
		DiscordEmojiCache cache = new DiscordEmojiCache(clock);
		cache.refresh(Map.of("flag_de", "<:flag_de:123>"));

		// when — Clock jumps 61 minutes forward; entry should be expired
		clock.set(T0.plusSeconds(61L * 60L));

		// then
		assertThat(cache.emojiFor("flag_de")).isEqualTo(":flag_de:");
	}

	@Test
	void givenRefresh_whenCalled_thenReturnsEntryCountAndReplacesMap() {
		// given
		DiscordEmojiCache cache = new DiscordEmojiCache(Clock.fixed(T0, ZoneOffset.UTC));
		cache.refresh(Map.of("a", "<:a:1>", "b", "<:b:2>"));

		// when — second refresh replaces all entries
		int count = cache.refresh(Map.of("c", "<:c:3>"));

		// then
		assertThat(count).isEqualTo(1);
		assertThat(cache.emojiFor("a")).isEqualTo(":a:"); // removed by refresh
		assertThat(cache.emojiFor("c")).isEqualTo("<:c:3>");
	}

	@Test
	void givenClockAtTtlBoundary_whenEmojiFor_thenTreatsAsExpired() {
		// given — populate at T0; at exactly +60min, expiresAt == now → isBefore == false
		MutableClock clock = new MutableClock(T0);
		DiscordEmojiCache cache = new DiscordEmojiCache(clock);
		cache.refresh(Map.of("x", "<:x:9>"));

		// when — advance to exactly the TTL boundary
		clock.set(T0.plusSeconds(60L * 60L));

		// then — boundary treated as expired (Clock.instant().isBefore(expiresAt) is false)
		assertThat(cache.emojiFor("x")).isEqualTo(":x:");
	}

	private static final class MutableClock extends Clock {
		private Instant now;

		MutableClock(Instant initial) {
			this.now = initial;
		}

		void set(Instant next) {
			this.now = next;
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}
	}
}
