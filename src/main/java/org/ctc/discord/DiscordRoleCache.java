package org.ctc.discord;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.dto.Role;
import org.ctc.discord.util.CachedEntry;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordRoleCache {

	private static final Duration TTL = Duration.ofMinutes(60);

	private volatile Map<String, CachedEntry<Role>> store = Map.of();
	private final Clock clock;

	/** Returns a snapshot of all non-expired role entries from the current store reference. */
	public Map<String, Role> snapshot() {
		Map<String, CachedEntry<Role>> current = store;
		return current.entrySet().stream()
				.filter(e -> e.getValue().isValid(clock))
				.collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().value()));
	}

	public Role get(String roleId) {
		CachedEntry<Role> entry = store.get(roleId);
		if (entry != null && entry.isValid(clock)) {
			return entry.value();
		}
		return null;
	}

	public int refresh(List<Role> roles) {
		Map<String, CachedEntry<Role>> next = new HashMap<>(roles.size());
		Instant expiry = clock.instant().plus(TTL);
		for (Role role : roles) {
			next.put(role.id(), new CachedEntry<>(role, expiry));
		}
		this.store = Map.copyOf(next);
		log.debug("Discord role cache refreshed with {} entries", next.size());
		return next.size();
	}
}
