package org.ctc.e2e;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
import org.ctc.backup.service.BackupArchiveService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Phase 74-10 / Phase 75-08 — Playwright E2E walkthrough for the Backup Import feature.
 *
 * <p>Drives the full click-through flow (upload → preview → confirm → execute) under
 * the {@code dev} profile ({@link PlaywrightConfig} base class boots {@code @SpringBootTest}
 * with {@code WebEnvironment.RANDOM_PORT} + {@code @ActiveProfiles("dev")}):
 * <ol>
 *   <li>Upload a Phase-73 export ZIP (generated at runtime via {@link BackupArchiveService#writeZip}).</li>
 *   <li>Assert the 26-card grid and the schema-match pill are visible — SC#1.</li>
 *   <li>Proceed through the confirm page and execute the REAL import — proves D-15 #1
 *       success flash (Phase 75-08; the Phase 74 D-02#5 stub-flash scenario was removed
 *       because the stub no longer exists in production code).</li>
 *   <li>Assert cancel flow deletes the staging file — D-06 + D-16.</li>
 *   <li>Assert missing-checkbox validation error — D-10 server-side @AssertTrue.</li>
 *   <li>Assert stateless re-parse via UUID after cookie-jar wipe — SC#5.</li>
 * </ol>
 *
 * <p>Phase 75-08 D-15 / D-17 carry-forward: the {@code admin/backup-confirm.html} template
 * is unchanged; only the controller body was upgraded from a Phase 74 stub-flash to a real
 * {@link org.ctc.backup.service.BackupImportService#execute(java.util.UUID)} delegation.
 * The success-flash regex enforced here is the locked English D-15 #1 string:
 * {@code "Import completed. {N} rows restored across {M} tables."}.
 */
@Tag("e2e")
class BackupImportE2ETest extends PlaywrightConfig {

    @Autowired
    BackupArchiveService backupArchiveService;

    @Value("${app.backup.staging-dir:data/dev/backup-staging}")
    Path stagingDir;

    @BeforeEach
    void setUp() {
        setupPage();
        // Pre-register dialog handler: auto-accept the JS confirm() dialog from Execute Import (D-10).
        // Registered AFTER setupPage() so it is bound to the freshly-built page instance.
        page.onDialog(d -> d.accept());
    }

    @AfterEach
    void tearDown() {
        teardownPage();
    }

    // =========================================================================
    // Phase 75-08 Happy-path: full upload → preview → confirm → REAL execute walkthrough.
    // Replaces the Phase 74 D-02#5 stub-flash scenario. The success flash now binds the
    // locked D-15 #1 string ("Import completed. {N} rows restored across {M} tables.")
    // and the real BackupImportService.execute(...) round-trips the dev fixture through
    // wipe + restore on H2.
    // =========================================================================

    @Test
    void givenValidBackupZip_whenAdminClicksConfirm_thenSuccessFlashRenderedOnAdminBackup(
            @TempDir Path tempDir) throws Exception {

        // given — generate a real Phase-73 export ZIP at runtime from the live dev fixture
        // (REVISION-iteration-1 consistency fix: BackupArchiveService.writeZip is the canonical
        // ZIP writer; BackupExportService.export does NOT exist — see 75-06 SUMMARY).
        Path fixtureZip = tempDir.resolve("ctc-backup-test.zip");
        try (OutputStream out = Files.newOutputStream(fixtureZip)) {
            backupArchiveService.writeZip(out, Instant.now());
        }
        Assertions.assertThat(Files.size(fixtureZip))
                .as("Fixture ZIP must contain bytes (BackupArchiveService.writeZip output)")
                .isGreaterThan(0L);

        // when — navigate to /admin/backup
        page.navigate(url("/admin/backup"));
        assertThat(page).hasURL(Pattern.compile(".*/admin/backup$"));
        assertThat(page.locator("h1")).containsText("Backup");
        assertThat(page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Import Backup"))).isVisible();

        // when — upload the fixture ZIP
        page.locator("input[type='file'][name='file']").setInputFiles(fixtureZip);
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Import Backup")).click();

        // then — preview page rendered with correct content (SC#1)
        assertThat(page).hasURL(Pattern.compile(".*/admin/backup/import-preview$"));
        assertThat(page.locator("h1")).containsText("Backup Import");
        assertThat(page.locator("h1")).containsText("Preview");

        // schema-match pill — locked UI-SPEC string (IMPORT-02)
        assertThat(page.locator(".alert.alert-success").first())
                .containsText("Schema version 2 matches.");

        // header card — file metadata visible
        assertThat(page.locator(".card").first()).containsText("File:");
        assertThat(page.locator(".card").first()).containsText("Size:");
        assertThat(page.locator(".card").first()).containsText("Uploads:");
        assertThat(page.locator(".card").first()).containsText("Total imported rows:");

        // 26-card grid — one card per entity (24 league + 2 Discord, D-03/D-21 + Phase 101)
        assertThat(page.locator(".card-grid > .card")).hasCount(26);

        // staging UUID must be present (D-18 stateless staging)
        String stagingUuid = page.locator("input[name='stagingId']").first().getAttribute("value");
        Assertions.assertThat(stagingUuid)
                .as("Preview form must carry the staging UUID per D-18")
                .isNotBlank();
        UUID.fromString(stagingUuid); // fail loud on malformed UUID

        // when — click Proceed to Confirm
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Proceed to Confirm")).click();

        // then — confirm page rendered
        assertThat(page).hasURL(Pattern.compile(".*/admin/backup/import-confirm$"));
        assertThat(page.locator("h1")).containsText("Backup Import");
        assertThat(page.locator("h1")).containsText("Confirm");
        assertThat(page.locator(".alert.alert-warning")).isVisible();
        assertThat(page.locator("#acknowledged")).not().isChecked();

        // when — tick acknowledgment and click Execute Import
        // (dialog handler registered in @BeforeEach auto-accepts the JS confirm())
        page.locator("#acknowledged").check();
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Execute Import")).click();

        // then — Phase 75-08 D-15 #1: land on /admin/backup with the real success flash
        // "Import completed. {N} rows restored across {M} tables." (English, locked terse style).
        // The wipe+restore round-trips the dev fixture through H2 — restoredTotal/entityCount
        // are positive integers because the fixture seeds 26 entities with data.
        assertThat(page).hasURL(Pattern.compile(".*/admin/backup$"));
        assertThat(page.locator(".alert.alert-success"))
                .containsText(Pattern.compile("Import completed\\. \\d+ rows restored across \\d+ tables\\."));

        // negative assertions (defense-in-depth)
        assertThat(page.locator(".alert.alert-error")).hasCount(0);
        Assertions.assertThat(page.content())
                .as("No Phase 74 stub-flash on success — Phase 75 replaced D-02#5 with D-15 #1")
                .doesNotContain("Validation succeeded. Import execution will be enabled in Phase 75.");
        Assertions.assertThat(page.content())
                .as("No reject-path strings on success")
                .doesNotContain("Schema version mismatch", "safety checks", "Upload too large");
    }

    // =========================================================================
    // Cancel cleanup: POST to /import-cancel deletes staging file
    // =========================================================================

    @Test
    void givenPreviewPage_whenAdminClicksCancel_thenStagingFileDeletedAndRedirects(
            @TempDir Path tempDir) throws IOException {

        // given — reach the preview page
        uploadFixtureAndOpenPreview(tempDir);
        String stagingUuid = currentStagingUuid();
        Path stagingFile = stagingDir.resolve("upload-" + stagingUuid + ".zip");

        // pre-assert: staging file must exist on disk between preview and cancel
        Assertions.assertThat(Files.exists(stagingFile))
                .as("Staging file must exist on disk between preview and cancel")
                .isTrue();

        // pre-assert: cancel form is a POST form per Plan 08 + Plan 09 CSRF trade-off
        assertThat(page.locator("form[action$='/import-cancel']")).hasCount(1);
        Assertions.assertThat(page.locator("form[action$='/import-cancel']").getAttribute("method"))
                .as("Cancel must be a POST form per Plan 08 + Plan 09 CSRF trade-off")
                .isEqualToIgnoringCase("post");
        Assertions.assertThat(page.locator("form[action$='/import-cancel'] input[name='stagingId']")
                        .getAttribute("value"))
                .as("Cancel form must carry the same staging UUID as the Proceed form")
                .isEqualTo(stagingUuid);

        // when — click Cancel (POST form button), pin the HTTP method via waitForRequest
        page.waitForRequest(
                req -> "POST".equals(req.method()) && req.url().endsWith("/import-cancel"),
                () -> page.locator("form[action$='/import-cancel'] button[type='submit']").click()
        );

        // then — redirect to /admin/backup
        assertThat(page).hasURL(Pattern.compile(".*/admin/backup$"));

        // staging file must be deleted synchronously (D-16 + Plan 08 deleteStagingFile(UUID))
        Assertions.assertThat(Files.exists(stagingFile))
                .as("Staging file must be deleted synchronously by import-cancel per D-16 + Plan 08 deleteStagingFile(UUID)")
                .isFalse();

        // Flash success (Plan 08 locked string)
        assertThat(page.locator(".alert.alert-success")).containsText("Import canceled.");
        assertThat(page.locator(".alert.alert-error")).hasCount(0);
    }

    // =========================================================================
    // Missing-checkbox validation: server-side @AssertTrue enforcement
    // =========================================================================

    @Test
    void givenConfirmPage_whenAdminSubmitsWithoutTickingCheckbox_thenSeesFieldError(
            @TempDir Path tempDir) throws IOException {

        // given — reach the confirm page
        uploadFixtureAndOpenPreview(tempDir);
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Proceed to Confirm")).click();
        assertThat(page).hasURL(Pattern.compile(".*/admin/backup/import-confirm$"));

        // pre-assert: checkbox unchecked
        assertThat(page.locator("#acknowledged")).not().isChecked();

        // when — click Execute Import WITHOUT ticking
        // (dialog handler from @BeforeEach auto-accepts JS confirm() if it fires;
        //  server-side @AssertTrue is the authoritative gate per D-10)
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Execute Import")).click();

        // then — stay on the confirm/execute URL (server returned the backup-confirm VIEW via
        // the /import-execute POST endpoint — no redirect on validation error, per Spring MVC
        // convention; URL is /import-execute because that is the POST target, not a redirect).
        assertThat(page).hasURL(Pattern.compile(".*/admin/backup/import-execute$"));

        // field-error visible with locked copy — class-only selector (tag-agnostic per Plan 09 Notes)
        assertThat(page.locator(".field-error")).isVisible();
        assertThat(page.locator(".field-error"))
                .containsText("You must acknowledge the deletion warning to continue.");

        // no stub flash (server rejected)
        assertThat(page.locator(".alert.alert-success")).hasCount(0);
    }

    // =========================================================================
    // SC#5 — stateless re-parse: confirm page renders after cookie-jar wipe
    // =========================================================================

    @Test
    void givenStagingUuid_whenCookieJarIsClearedBetweenPreviewAndExecute_thenPagesStillFunction(
            @TempDir Path tempDir) throws IOException {

        // given — upload fixture + reach preview page in the original context
        uploadFixtureAndOpenPreview(tempDir);

        // SC#5 baseline: preview rendered correctly BEFORE the cookie wipe
        assertThat(page).hasURL(Pattern.compile(".*/admin/backup/import-preview$"));

        String stagingUuid = currentStagingUuid();

        // wipe cookies on the original context to prove even the FIRST page didn't depend on them
        context.clearCookies();

        // when — spawn a completely fresh BrowserContext (zero-cookie state)
        BrowserContext freshContext = browser.newContext();
        try {
            Page freshPage = freshContext.newPage();
            freshPage.onDialog(d -> d.accept());

            // safety net: visit landing page to establish any session (dev CSRF is disabled,
            // so this is a no-op guard); then wipe again for truly empty jar
            freshPage.navigate(url("/admin/backup"));
            freshContext.clearCookies();

            // POST to /admin/backup/import-confirm with only the staging UUID
            // (no session cookie — proves server re-reads staging file by UUID, not by HttpSession)
            APIResponse confirmResp = freshContext.request().post(
                    url("/admin/backup/import-confirm"),
                    RequestOptions.create()
                            .setForm(FormData.create().set("stagingId", stagingUuid))
            );
            Assertions.assertThat(confirmResp.status())
                    .as("Confirm page must render successfully in a session-wiped fresh context — proves stateless re-parse (SC#5)")
                    .isEqualTo(200);
            String confirmBody = confirmResp.text();
            Assertions.assertThat(confirmBody)
                    .as("Confirm page in fresh context must contain the same staging UUID — proves server reads by UUID, not session")
                    .contains(stagingUuid)
                    .contains("Backup Import")
                    .contains("Confirm")
                    .contains("I am an admin and I understand all operational data will be deleted.");

            // POST to /admin/backup/import-execute in the same fresh, cookie-wiped context.
            // setMaxRedirects(0) prevents Playwright from following the redirect automatically —
            // we need the raw 302/303 response to assert the Location header.
            APIResponse executeResp = freshContext.request().post(
                    url("/admin/backup/import-execute"),
                    RequestOptions.create()
                            .setMaxRedirects(0)
                            .setForm(FormData.create()
                                    .set("stagingId", stagingUuid)
                                    .set("acknowledged", "true"))
            );
            Assertions.assertThat(executeResp.status())
                    .as("Execute must redirect (302/303) when called from a session-wiped fresh context")
                    .isBetween(300, 399);
            Assertions.assertThat(executeResp.headers().get("location"))
                    .as("Execute must redirect to /admin/backup per D-17 (endpoint URL unchanged Phase 74 → 75)")
                    .endsWith("/admin/backup");

            // NOTE: Rendered Flash banner is intentionally NOT asserted here.
            // Spring Flash uses HttpSession-backed handover across the redirect.
            // After the cookie wipe there is no session cookie to carry the Flash key —
            // correct architectural behaviour (Flash is session-coupled; staging file is UUID-coupled).
            // Task 1's happy path covers the Flash render in a normal session (orthogonal axis).

        } finally {
            freshContext.close();
        }
    }

    // =========================================================================
    // Shared helpers
    // =========================================================================

    /**
     * Generates a runtime fixture ZIP, uploads it, and navigates to the preview page.
     * Returns after asserting the preview URL is loaded.
     * Shared by cancel, missing-checkbox, and cookie-jar tests to avoid duplication.
     */
    private void uploadFixtureAndOpenPreview(Path tempDir) throws IOException {
        Path fixtureZip = tempDir.resolve("ctc-backup-test.zip");
        try (OutputStream out = Files.newOutputStream(fixtureZip)) {
            backupArchiveService.writeZip(out, Instant.now());
        }
        page.navigate(url("/admin/backup"));
        page.locator("input[type='file'][name='file']").setInputFiles(fixtureZip);
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Import Backup")).click();
        assertThat(page).hasURL(Pattern.compile(".*/admin/backup/import-preview$"));
    }

    /**
     * Captures and validates the staging UUID from the current page's hidden input.
     * Fails loudly if the UUID is blank or malformed.
     */
    private String currentStagingUuid() {
        String value = page.locator("input[name='stagingId']").first().getAttribute("value");
        Assertions.assertThat(value)
                .as("Preview/Confirm form must carry stagingId per D-18")
                .isNotBlank();
        UUID.fromString(value); // fail loud on malformed
        return value;
    }
}
