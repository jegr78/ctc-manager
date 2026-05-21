package org.ctc.discord;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

	private final Map<String, CachedEntry<Role>> store = new ConcurrentHashMap<>();
	private final Clock clock;

	public Map<String, Role> snapshot() {
		Map<String, Role> valid = new LinkedHashMap<>();
		store.forEach((roleId, entry) -> {
			if (entry.isValid(clock)) {
				valid.put(roleId, entry.value());
			}
		});
		return Map.copyOf(valid);
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
		for (Role role : roles) {
			next.put(role.id(), new CachedEntry<>(role, clock.instant().plus(TTL)));
		}
		store.clear();
		store.putAll(next);
		log.debug("Discord role cache refreshed with {} entries", next.size());
		return next.size();
	}
}
