package org.ctc.discord.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.ctc.discord.dto.DiscordPostFilterForm;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lists Discord posts for the admin page with its season, match and type filters. */
@Service
@RequiredArgsConstructor
public class DiscordPostListService {

	private final DiscordPostRepository discordPostRepository;
	private final SeasonRepository seasonRepository;
	private final MatchRepository matchRepository;

	/**
	 * A season filter matches season and phase posts of that season as well as posts of its
	 * matches, matchdays and races.
	 */
	@Transactional(readOnly = true)
	public Page<DiscordPost> findPosts(DiscordPostFilterForm filter, Pageable pageable) {
		return discordPostRepository.findAll(spec(filter), pageable);
	}

	@Transactional(readOnly = true)
	public List<Season> seasonOptions() {
		return seasonRepository.findAll(Sort.by(Sort.Direction.DESC, "year"));
	}

	@Transactional(readOnly = true)
	public List<Match> matchOptions() {
		return matchRepository.findAll().stream()
				.sorted(Comparator
						.comparing((Match m) -> m.getMatchday().getSeason().getYear(), Comparator.reverseOrder())
						.thenComparing(m -> m.getMatchday().getLabel())
						.thenComparing(m -> m.getHomeTeam().getShortName()))
				.toList();
	}

	public static Map<UUID, String> matchLabels(List<Match> matches) {
		return matches.stream().collect(Collectors.toMap(Match::getId, DiscordPostListService::matchLabel));
	}

	private static String matchLabel(Match m) {
		String awayShort = m.getAwayTeam() != null ? m.getAwayTeam().getShortName() : "Bye";
		return m.getMatchday().getSeason().getYear() + " | " + m.getMatchday().getLabel()
				+ " | " + m.getHomeTeam().getShortName() + " vs. " + awayShort;
	}

	private static Specification<DiscordPost> spec(DiscordPostFilterForm filter) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (filter.getSeasonId() != null) {
				predicates.add(inSeason(root, query, cb, filter.getSeasonId()));
			}
			if (filter.getMatchId() != null) {
				predicates.add(cb.equal(root.get("matchId"), filter.getMatchId()));
			}
			if (filter.getPostType() != null) {
				predicates.add(cb.equal(root.get("postType"), filter.getPostType()));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};
	}

	private static Predicate inSeason(Root<DiscordPost> root, CriteriaQuery<?> query, CriteriaBuilder cb, UUID seasonId) {
		var matchIds = query.subquery(UUID.class);
		var match = matchIds.from(Match.class);
		matchIds.select(match.get("id"))
				.where(cb.equal(match.get("matchday").get("phase").get("season").get("id"), seasonId));
		var matchdayIds = query.subquery(UUID.class);
		var matchday = matchdayIds.from(Matchday.class);
		matchdayIds.select(matchday.get("id"))
				.where(cb.equal(matchday.get("phase").get("season").get("id"), seasonId));
		var raceIds = query.subquery(UUID.class);
		var race = raceIds.from(Race.class);
		raceIds.select(race.get("id"))
				.where(cb.equal(race.get("matchday").get("phase").get("season").get("id"), seasonId));
		return cb.or(
				cb.equal(root.get("seasonId"), seasonId),
				root.get("matchId").in(matchIds),
				root.get("matchdayId").in(matchdayIds),
				root.get("raceId").in(raceIds));
	}
}
