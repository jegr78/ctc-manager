package org.ctc.discord.web;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.dto.DiscordPostFilterForm;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/discord/posts")
@RequiredArgsConstructor
@Slf4j
public class DiscordPostController {

	private static final String VIEW = "admin/discord-posts";

	private final DiscordPostRepository discordPostRepository;
	private final SeasonRepository seasonRepository;
	private final MatchRepository matchRepository;

	@GetMapping
	public String list(
			@ModelAttribute("filter") DiscordPostFilterForm filter,
			@PageableDefault(size = 50, sort = "postedAt", direction = Sort.Direction.DESC) Pageable pageable,
			Model model) {
		Specification<DiscordPost> spec = buildSpec(filter);
		Page<DiscordPost> posts = discordPostRepository.findAll(spec, pageable);

		List<Match> matches = matchRepository.findAll().stream()
				.sorted(Comparator
						.comparing((Match m) -> m.getMatchday().getSeason().getYear(), Comparator.reverseOrder())
						.thenComparing(m -> m.getMatchday().getLabel())
						.thenComparing(m -> m.getHomeTeam().getShortName()))
				.toList();
		Map<UUID, String> matchLabels = matches.stream()
				.collect(Collectors.toMap(Match::getId, DiscordPostController::matchLabel));
		Map<UUID, String> matchPairings = matches.stream()
				.collect(Collectors.toMap(Match::getId,
						m -> m.getHomeTeam().getShortName() + " vs. " + m.getAwayTeam().getShortName()));
		Map<String, List<Match>> matchesByGroup = matches.stream()
				.collect(Collectors.groupingBy(
						m -> m.getMatchday().getSeason().getYear() + " | " + m.getMatchday().getLabel(),
						LinkedHashMap::new,
						Collectors.toList()));

		model.addAttribute("posts", posts);
		model.addAttribute("seasons", seasonRepository.findAll(Sort.by(Sort.Direction.DESC, "year")));
		model.addAttribute("matches", matches);
		model.addAttribute("matchLabels", matchLabels);
		model.addAttribute("matchPairings", matchPairings);
		model.addAttribute("matchesByGroup", matchesByGroup);
		model.addAttribute("postTypes", Arrays.asList(DiscordPostType.values()));
		model.addAttribute("activeRoute", "discord-posts");
		return VIEW;
	}

	private static String matchLabel(Match m) {
		return m.getMatchday().getSeason().getYear() + " | " + m.getMatchday().getLabel()
				+ " | " + m.getHomeTeam().getShortName() + " vs. " + m.getAwayTeam().getShortName();
	}

	private static Specification<DiscordPost> buildSpec(DiscordPostFilterForm filter) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (filter.getSeasonId() != null) {
				predicates.add(cb.equal(root.get("seasonId"), filter.getSeasonId()));
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
}
