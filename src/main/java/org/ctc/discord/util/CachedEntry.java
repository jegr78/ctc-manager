package org.ctc.discord.util;

import java.time.Clock;
import java.time.Instant;

public record CachedEntry<T>(T value, Instant expiresAt) {

	public boolean isValid(Clock clock) {
		return clock.instant().isBefore(expiresAt);
	}
}
