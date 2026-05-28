package org.ctc.discord;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.util.CachedEntry;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordEmojiCache {

	private static final Duration TTL = Duration.ofMinutes(60);

	private volatile Map<String, CachedEntry<String>> store = Map.of();
	private final Clock clock;

	public String emojiFor(String shortName) {
		CachedEntry<String> entry = store.get(shortName);
		if (entry != null && entry.isValid(clock)) {
			return entry.value();
		}
		return ":" + shortName + ":";
	}

	public int refresh(Map<String, String> shortNameToTag) {
		Map<String, CachedEntry<String>> next = new HashMap<>(shortNameToTag.size());
		for (Map.Entry<String, String> e : shortNameToTag.entrySet()) {
			next.put(e.getKey(), new CachedEntry<>(e.getValue(), clock.instant().plus(TTL)));
		}
		this.store = Map.copyOf(next);
		log.debug("Discord emoji cache refreshed with {} entries", next.size());
		return next.size();
	}
}
