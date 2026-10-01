package org.ctc.discord.web;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.dto.DiscordPostFilterForm;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.service.DiscordPostListService;
import org.ctc.domain.model.Match;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

	private final DiscordPostListService discordPostListService;

	@GetMapping
	public String list(
			@ModelAttribute("filter") DiscordPostFilterForm filter,
			@PageableDefault(size = 50, sort = "postedAt", direction = Sort.Direction.DESC) Pageable pageable,
			Model model) {
		List<Match> matches = discordPostListService.matchOptions();
		model.addAttribute("posts", discordPostListService.findPosts(filter, pageable));
		model.addAttribute("seasons", discordPostListService.seasonOptions());
		model.addAttribute("matches", matches);
		model.addAttribute("matchLabels", DiscordPostListService.matchLabels(matches));
		model.addAttribute("postTypes", Arrays.asList(DiscordPostType.values()));
		return VIEW;
	}
}
