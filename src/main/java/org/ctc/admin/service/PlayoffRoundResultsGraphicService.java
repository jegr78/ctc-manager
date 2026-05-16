package org.ctc.admin.service;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.PlayoffRound;
import org.ctc.domain.repository.PlayoffSeedRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
public class PlayoffRoundResultsGraphicService extends AbstractPlayoffRoundGraphicService {

	private static final String DEFAULT_TEMPLATE = "admin/playoff-round-results-render";
	private static final String CUSTOM_TEMPLATE_FILE = "playoff-round-results-template.html";

	public PlayoffRoundResultsGraphicService(TemplateEngine templateEngine,
	                                         PlayoffSeedRepository playoffSeedRepository,
	                                         SeasonTeamRepository seasonTeamRepository,
	                                         @Value("${app.upload-dir:uploads}") String uploadDir) {
		super(templateEngine, playoffSeedRepository, seasonTeamRepository, uploadDir);
	}

	public byte[] generateResults(PlayoffRound round) throws IOException {
		var data = prepareBaseContext(round);

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
