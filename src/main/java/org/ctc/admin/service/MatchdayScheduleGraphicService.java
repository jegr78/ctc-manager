package org.ctc.admin.service;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.service.StandingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
public class MatchdayScheduleGraphicService extends AbstractMatchdayGraphicService {

	private static final String DEFAULT_TEMPLATE = "admin/matchday-schedule-render";
	private static final String CUSTOM_TEMPLATE_FILE = "matchday-schedule-template.html";

	public MatchdayScheduleGraphicService(TemplateEngine templateEngine,
	                                      StandingsService standingsService,
	                                      SeasonTeamRepository seasonTeamRepository,
	                                      @Value("${app.upload-dir:uploads}") String uploadDir) {
		super(templateEngine, standingsService, seasonTeamRepository, uploadDir);
	}

	public byte[] generateSchedule(Matchday matchday) throws IOException {
		var data = prepareBaseContext(matchday);

		var ctx = new Context();
		ctx.setVariable("data", data);

		String html = renderTemplate(ctx);
		return renderToBytes(html);
	}

	@Override
	protected String getTemplateFileName() {
		return CUSTOM_TEMPLATE_FILE;
	}

	@Override
	protected String getDefaultTemplatePath() {
		return DEFAULT_TEMPLATE;
	}
}
