package org.ctc.admin.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Team;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Rendert die vollständige GT7 "Lobby Settings"-Übersicht (Variante C / Ledger)
 * als 1920×1080 PNG — analog zu {@link SettingsGraphicService}, aber für ALLE
 * Lobby-Einstellungen.
 *
 * <p>Prinzip "fest ↔ match-spezifisch": Default-Werte stehen als fester Text im
 * Template ({@code templates/admin/lobby-settings-render.html}). Match-spezifische
 * Werte werden über die Map {@code v} befüllt und im Template per
 * {@code th:text="${v.key} ?: 'Default'"} ausgegeben. Fehlt ein Key, greift der
 * {@code ?:}-Default — so lässt sich pro Liga/Saison im Template-Editor frei
 * entscheiden, was variabel ist, ohne Render-Fehler zu riskieren.
 */
@Slf4j
@Service
public class LobbySettingsGraphicService extends AbstractGraphicService implements TemplateManageable {

	private static final String DEFAULT_TEMPLATE = "templates/admin/lobby-settings-render.html";
	private static final String CUSTOM_TEMPLATE_FILE = "lobby-settings-template.html";

	public LobbySettingsGraphicService(TemplateEngine templateEngine,
	                                   @Value("${app.upload-dir:uploads}") String uploadDir) {
		super(templateEngine, uploadDir);
	}

	public String generateLobbySettings(Race race) throws IOException {
		if (race.getTrack() == null) {
			throw new IllegalStateException("Race has no track");
		}
		if (race.getSettings() == null || !race.hasAllSettings()) {
			throw new IllegalStateException("Race settings are incomplete");
		}

		var settings = race.getSettings();
		var season = race.getMatchday().getSeason();
		Team home = race.getHomeTeam();
		Team away = race.getAwayTeam();

		// --- Match-spezifische Werte → Map "v" (nur was variabel sein soll!) ---
		Map<String, Object> v = new HashMap<>();
		v.put("roomName", buildRoomName(season.getYear(), race.getMatchday().getLabel(), home, away));
		v.put("track", race.getTrack().getName());
		v.put("laps", settings.getNumberOfLaps());
		v.put("weather", settings.getWeather());
		v.put("timeOfDay", settings.getTimeOfDay());
		v.put("timeSpeed", settings.getTimeProgressionMultiplier() + "×");
		v.put("tyreWear", settings.getTyreWearMultiplier() + "×");
		v.put("fuelRate", settings.getFuelConsumptionMultiplier() + "×");
		v.put("refuelSpeed", settings.getRefuelingSpeed() + " Litre/sec");
		v.put("initialFuel", settings.getInitialFuel());
		v.put("minPitStops", settings.getNumberOfRequiredPitStops());
		v.put("usableTyres", settings.getAvailableTyres());
		v.put("requiredTyre", settings.getMandatoryTyres());
		// "Filter by Category" und "Mechanical Damage" sind im Template fest verdrahtet
		// ("—" bzw. "Light") und brauchen keinen Wert hier.
		// TODO: optional, nur falls euer Modell das Feld trägt — sonst greift der
		//       ?:-Default ("—") im Template:
		// v.put("customWeather", settings.getCustomWeather());

		var ctx = new Context();
		ctx.setVariable("fontBase64", encodeClasspathResource(FONT_CLASSPATH, "font/woff2"));
		ctx.setVariable("ctcLogoBase64", encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"));
		ctx.setVariable("seasonYear", String.valueOf(season.getYear()));
		ctx.setVariable("seasonName", season.getName() + " · " + season.getYear());
		ctx.setVariable("v", v);

		String html = renderTemplate(ctx);

		Path raceDir = uploadDir.resolve("races").resolve(race.getId().toString());
		Files.createDirectories(raceDir);
		Path outputFile = raceDir.resolve("lobby-settings.png");

		renderScreenshot(html, outputFile);   // bestehender 1920×1080-Screenshot, fullPage:false

		log.info("Generated lobby settings graphic: {}", outputFile);
		return "/uploads/races/" + race.getId() + "/lobby-settings.png";
	}

	private String buildRoomName(int year, String matchday, Team home, Team away) {
		String name = "CTC – " + year + " – " + matchday;
		if (home != null && away != null) {
			name += " – " + home.getShortName() + " vs. " + away.getShortName();
		}
		return name;
	}

	// ---- TemplateManageable (identisch zu SettingsGraphicService) ----

	private String renderTemplate(Context ctx) throws IOException {
		Path customTemplate = uploadDir.resolve(CUSTOM_TEMPLATE_FILE);
		if (Files.exists(customTemplate)) {
			return processStringTemplate(Files.readString(customTemplate), ctx);
		}
		return templateEngine.process("admin/lobby-settings-render", ctx);
	}

	public String loadTemplate() throws IOException {
		Path customTemplate = uploadDir.resolve(CUSTOM_TEMPLATE_FILE);
		if (Files.exists(customTemplate)) {
			return Files.readString(customTemplate);
		}
		return loadDefaultTemplate();
	}

	public String loadDefaultTemplate() throws IOException {
		var resource = new ClassPathResource(DEFAULT_TEMPLATE);
		try (var is = resource.getInputStream()) {
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	public void saveTemplate(String content) throws IOException {
		Files.createDirectories(uploadDir);
		Files.writeString(uploadDir.resolve(CUSTOM_TEMPLATE_FILE), content);
		log.info("Saved custom lobby settings template");
	}

	public void resetTemplate() throws IOException {
		Files.deleteIfExists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
		log.info("Reset lobby settings template to default");
	}

	public boolean hasCustomTemplate() {
		return Files.exists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
	}
}
