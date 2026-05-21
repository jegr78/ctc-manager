package org.ctc.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.ctc.discord.dto.Role;
import org.junit.jupiter.api.Test;

class DiscordRoleCacheTest {

	private static final Instant T0 = Instant.parse("2026-05-21T10:00:00Z");

	@Test
	void givenRefreshedCache_whenSnapshot_thenContainsRoleEntries() {
		// given
		DiscordRoleCache cache = new DiscordRoleCache(Clock.fixed(T0, ZoneOffset.UTC));
		Role admin = new Role("100", "Admin", 5);
		Role member = new Role("101", "Member", 1);

		// when
		cache.refresh(List.of(admin, member));

		// then
		assertThat(cache.snapshot()).containsOnlyKeys("100", "101");
		assertThat(cache.snapshot().get("100")).isEqualTo(admin);
	}

	@Test
	void givenCacheMiss_whenGet_thenReturnsNull() {
		// given
		DiscordRoleCache cache = new DiscordRoleCache(Clock.fixed(T0, ZoneOffset.UTC));

		// when / then — never refreshed
		assertThat(cache.get("999")).isNull();
	}

	@Test
	void givenClockAdvanced61Min_whenSnapshot_thenEmpty() {
		// given
		MutableClock clock = new MutableClock(T0);
		DiscordRoleCache cache = new DiscordRoleCache(clock);
		cache.refresh(List.of(new Role("100", "Admin", 5)));

		// when
		clock.set(T0.plusSeconds(61L * 60L));

		// then
		assertThat(cache.snapshot()).isEmpty();
		assertThat(cache.get("100")).isNull();
	}

	@Test
	void givenRefresh_whenCalled_thenReturnsEntryCountAndReplaces() {
		// given
		DiscordRoleCache cache = new DiscordRoleCache(Clock.fixed(T0, ZoneOffset.UTC));
		cache.refresh(List.of(new Role("100", "Admin", 5), new Role("101", "Member", 1)));

		// when — second refresh replaces all entries
		int count = cache.refresh(List.of(new Role("200", "Captain", 3)));

		// then
		assertThat(count).isEqualTo(1);
		assertThat(cache.get("100")).isNull();
		assertThat(cache.get("200")).isNotNull();
		assertThat(cache.get("200").name()).isEqualTo("Captain");
	}

	@Test
	void givenClockAtTtlBoundary_whenSnapshot_thenTreatsAsExpired() {
		// given — at exactly +60min, expiresAt == now → isBefore == false
		MutableClock clock = new MutableClock(T0);
		DiscordRoleCache cache = new DiscordRoleCache(clock);
		cache.refresh(List.of(new Role("100", "Admin", 5)));

		// when
		clock.set(T0.plusSeconds(60L * 60L));

		// then
		assertThat(cache.snapshot()).isEmpty();
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
