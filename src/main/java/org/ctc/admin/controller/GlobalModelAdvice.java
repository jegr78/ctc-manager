package org.ctc.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAdvice {

	@Value("${app.version}")
	private String appVersion;

	@ModelAttribute("appVersion")
	public String appVersion() {
		return appVersion;
	}

	@ModelAttribute("activeRoute")
	public String activeRoute(HttpServletRequest request) {
		String uri = request.getRequestURI();
		if (uri == null || !uri.startsWith("/admin")) {
			return null;
		}
		if (uri.startsWith("/admin/tools/power-rankings")) return "power-rankings";
		if (uri.startsWith("/admin/tools/team-cards")) return "team-cards";
		if (uri.startsWith("/admin/tools/template-editors")) return "template-editors";
		if (uri.startsWith("/admin/discord-config")) return "discord-config";
		if (uri.startsWith("/admin/discord")) return "discord-posts";
		if (uri.startsWith("/admin/race-scorings")) return "race-scorings";
		if (uri.startsWith("/admin/match-scorings")) return "match-scorings";
		if (uri.startsWith("/admin/seasons")) return "seasons";
		if (uri.startsWith("/admin/matchdays")) return "matchdays";
		if (uri.startsWith("/admin/matches")) return "matchdays";
		if (uri.startsWith("/admin/races")) return "races";
		if (uri.startsWith("/admin/playoffs")) return "playoffs";
		if (uri.startsWith("/admin/playoff-matchups")) return "playoffs";
		if (uri.startsWith("/admin/teams")) return "teams";
		if (uri.startsWith("/admin/drivers")) return "drivers";
		if (uri.startsWith("/admin/cars")) return "cars";
		if (uri.startsWith("/admin/tracks")) return "tracks";
		if (uri.startsWith("/admin/standings")) return "standings";
		if (uri.startsWith("/admin/import")) return "import";
		if (uri.startsWith("/admin/gt7-sync")) return "gt7-sync";
		if (uri.startsWith("/admin/backup")) return "backup";
		return null;
	}
}
