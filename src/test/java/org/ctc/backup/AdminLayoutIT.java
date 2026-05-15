package org.ctc.backup;

import org.hamcrest.Matchers;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sidebar-rendering IT for the "Data → Backup" sidebar entry.
 * Verifies the Data group label, active CSS class on the Backup page,
 * and absence of active class on other admin pages.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminLayoutIT {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void givenBackupPage_whenRenderLayout_thenDataSidebarGroupAndBackupEntryArePresent() throws Exception {
		// when
		MvcResult result = mockMvc.perform(get("/admin/backup"))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("<span class=\"sidebar-group-label\">Data</span>")))
				.andReturn();

		// then — Jsoup-parse the rendered HTML and verify the Backup anchor lives in the Data group
		Document doc = Jsoup.parse(result.getResponse().getContentAsString());
		Element backupAnchor = doc.selectFirst("nav a[href=/admin/backup]");
		assertThat(backupAnchor)
				.as("Sidebar must expose a Backup link pointing at /admin/backup")
				.isNotNull();
		assertThat(backupAnchor.text()).isEqualTo("Backup");
	}

	@Test
	void givenBackupPage_whenRenderLayout_thenBackupEntryHasActiveClass() throws Exception {
		// when
		MvcResult result = mockMvc.perform(get("/admin/backup"))
				.andExpect(status().isOk())
				.andReturn();

		// then
		Document doc = Jsoup.parse(result.getResponse().getContentAsString());
		Element backupAnchor = doc.selectFirst("nav a[href=/admin/backup]");
		assertThat(backupAnchor).isNotNull();
		assertThat(backupAnchor.classNames())
				.as("Backup link must carry the 'active' class when on /admin/backup (title.contains('Backup'))")
				.contains("active");
	}

	@Test
	void givenSeasonsPage_whenRenderLayout_thenBackupEntryDoesNotHaveActiveClass() throws Exception {
		// when
		MvcResult result = mockMvc.perform(get("/admin/seasons"))
				.andExpect(status().isOk())
				.andReturn();

		// then — Backup anchor is still present (sidebar is global) but without the active class
		Document doc = Jsoup.parse(result.getResponse().getContentAsString());
		Element backupAnchor = doc.selectFirst("nav a[href=/admin/backup]");
		assertThat(backupAnchor)
				.as("Sidebar Backup entry must render on every admin page")
				.isNotNull();
		assertThat(backupAnchor.classNames())
				.as("Backup link must NOT carry the 'active' class when on /admin/seasons")
				.doesNotContain("active");
	}
}
