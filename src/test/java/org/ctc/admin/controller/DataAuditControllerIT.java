package org.ctc.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.SiteSlug;
import org.ctc.domain.model.SiteSlugKind;
import org.ctc.domain.repository.SiteSlugRepository;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class DataAuditControllerIT {

	@Autowired private MockMvc mockMvc;
	@Autowired private SiteSlugRepository siteSlugRepository;
	@Autowired private TestHelper testHelper;

	@Test
	void givenReservedSlugAndCollidingTeamNames_whenAuditPageShown_thenFindingsAreListedAndEscaped() throws Exception {
		String id = UUID.randomUUID().toString().substring(0, 8);
		siteSlugRepository.save(new SiteSlug(SiteSlugKind.TEAM, "test-audit-page-" + id, "test-audit-page-" + id, null));
		testHelper.createTeam("Test Audit Page One " + id, "Test<b>" + id);
		testHelper.createTeam("Test Audit Page Two " + id, "Test-b-" + id);

		var result = mockMvc.perform(get("/admin/data-audit"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/data-audit"))
				.andExpect(content().string(containsString("Ambiguous public URLs")))
				.andExpect(content().string(containsString("team URL &#39;test-audit-page-" + id + "&#39;")))
				.andExpect(content().string(containsString("Test&lt;b&gt;" + id)))
				.andExpect(content().string(not(containsString("Test<b>" + id))))
				.andReturn();
		var page = Jsoup.parse(result.getResponse().getContentAsString());
		assertThat(page.select("#sidebar a.active[href='/admin/data-audit'][aria-current='page']")).hasSize(1);
	}
}
