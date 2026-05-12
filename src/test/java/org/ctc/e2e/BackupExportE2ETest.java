package org.ctc.e2e;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Phase 73-04 — Playwright E2E for the visible Backup feature.
 *
 * <p>Drives the full click-through download flow under the {@code dev} profile
 * ({@link PlaywrightConfig} base class boots {@code @SpringBootTest} with
 * {@code WebEnvironment.RANDOM_PORT} + {@code @ActiveProfiles("dev")}):
 * <ol>
 *   <li>Navigate to {@code /admin}.</li>
 *   <li>Click the new "Backup" sidebar entry under the "Data" group.</li>
 *   <li>Assert the URL resolves to {@code /admin/backup}.</li>
 *   <li>Click the "Export Backup" button and intercept the browser's download.</li>
 *   <li>Assert the {@code suggestedFilename()} matches the locked basic-form ISO
 *       regex {@code ctc-backup-\d{8}T\d{6}Z\.zip}.</li>
 *   <li>Save the download to a temp file and re-open it as a {@link ZipInputStream}
 *       to assert the manifest-first invariant ({@code manifest.json} is the very
 *       first entry) survives end-to-end — defense-in-depth that the download is
 *       actually a real ZIP, not just a correctly-named blob.</li>
 * </ol>
 *
 * <p>This test class lives under {@code src/test/java/org/ctc/e2e/} and follows the
 * {@code *Test} suffix that the {@code -Pe2e} Maven profile's Failsafe
 * configuration picks up via the include pattern {@code **\/e2e/**\/*Test.java}
 * (see {@code pom.xml} profile {@code id=e2e}). Surefire excludes
 * {@code **\/e2e/**} so the test never runs in the unit-test phase.
 */
class BackupExportE2ETest extends PlaywrightConfig {

	private static final Pattern ISO_FILENAME_REGEX =
			Pattern.compile("ctc-backup-\\d{8}T\\d{6}Z\\.zip");

	@BeforeEach
	void setUp() {
		setupPage();
	}

	@AfterEach
	void tearDown() {
		teardownPage();
	}

	@Test
	void givenAdminUI_whenClickBackupSidebarThenExport_thenZipDownloadsWithIsoFilenameAndManifestFirst()
			throws Exception {
		// given — start on the admin root (which redirects to /admin/seasons per AdminWorkflowE2ETest)
		page.navigate(url("/admin"));

		// when — click the Backup sidebar entry; setExact(true) avoids matching the unrelated
		// "Backup" substring in any future tooltip/aria-label.
		page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Backup").setExact(true))
				.first()
				.click();

		// then — URL must resolve to /admin/backup and the Export Backup CTA is visible
		assertThat(page).hasURL(Pattern.compile(".*/admin/backup$"));
		assertThat(page.locator("h1")).containsText("Backup");
		assertThat(page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName("Export Backup"))).isVisible();

		// when — intercept the download triggered by the form POST. waitForDownload returns
		// the Download handle as soon as the browser sees the Content-Disposition: attachment
		// header on the response.
		Download download = page.waitForDownload(() ->
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Export Backup"))
						.click());

		// then — the filename matches the locked basic-form ISO regex
		String suggested = download.suggestedFilename();
		org.assertj.core.api.Assertions.assertThat(suggested)
				.as("Download suggestedFilename must match the locked ctc-backup-<basic-ISO>.zip pattern")
				.matches(ISO_FILENAME_REGEX);

		// then — save the bytes and assert it is a real ZIP whose first entry is manifest.json
		Path savedZip = Files.createTempFile("backup-e2e-", ".zip");
		try {
			download.saveAs(savedZip);

			org.assertj.core.api.Assertions.assertThat(Files.size(savedZip))
					.as("Downloaded ZIP must contain bytes (not just a correctly-named empty file)")
					.isGreaterThan(0L);

			try (InputStream in = Files.newInputStream(savedZip);
				 ZipInputStream zis = new ZipInputStream(in)) {
				ZipEntry first = zis.getNextEntry();
				org.assertj.core.api.Assertions.assertThat(first)
						.as("Downloaded ZIP must contain at least one entry")
						.isNotNull();
				org.assertj.core.api.Assertions.assertThat(first.getName())
						.as("manifest.json must be ZipEntry #0 — wire-contract invariant verified end-to-end")
						.isEqualTo("manifest.json");
			}
		} finally {
			Files.deleteIfExists(savedZip);
		}
	}
}
