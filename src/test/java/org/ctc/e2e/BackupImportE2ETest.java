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
import org.ctc.backup.schema.BackupSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Tag("e2e")
class BackupImportE2ETest extends PlaywrightConfig {

    @Autowired
    BackupArchiveService backupArchiveService;

    @Autowired
    BackupSchema backupSchema;

    @Value("${app.backup.staging-dir:data/dev/backup-staging}")
    Path stagingDir;

    @BeforeEach
    void setUp() {
        setupPage();
        page.onDialog(d -> d.accept());
    }

    @AfterEach
    void tearDown() {
        teardownPage();
    }

    @Test
    void givenValidBackupZip_whenAdminClicksConfirm_thenSuccessFlashRenderedOnAdminBackup(
            @TempDir Path tempDir) throws Exception {
        // given / when / then

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

        assertThat(page).hasURL(Pattern.compile(".*/admin/backup/import-preview$"));
        assertThat(page.locator("h1")).containsText("Backup Import");
        assertThat(page.locator("h1")).containsText("Preview");

        assertThat(page.locator(".alert.alert-success").first())
                .containsText("Schema version " + BackupSchema.SCHEMA_VERSION + " matches.");

        // header card — file metadata visible
        assertThat(page.locator(".card").first()).containsText("File:");
        assertThat(page.locator(".card").first()).containsText("Size:");
        assertThat(page.locator(".card").first()).containsText("Uploads:");
        assertThat(page.locator(".card").first()).containsText("Total imported rows:");

        // one card per exported entity
        assertThat(page.locator(".card-grid > .card")).hasCount(backupSchema.getExportOrder().size());

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

    @Test
    void givenPreviewPage_whenAdminClicksCancel_thenStagingFileDeletedAndRedirects(
            @TempDir Path tempDir) throws IOException {
        // given / when / then

        // given — reach the preview page
        uploadFixtureAndOpenPreview(tempDir);
        String stagingUuid = currentStagingUuid();
        Path stagingFile = stagingDir.resolve("upload-" + stagingUuid + ".zip");

        // pre-assert: staging file must exist on disk between preview and cancel
        Assertions.assertThat(Files.exists(stagingFile))
                .as("Staging file must exist on disk between preview and cancel")
                .isTrue();

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

        Assertions.assertThat(Files.exists(stagingFile))
                .as("Staging file must be deleted synchronously by import-cancel per D-16 + Plan 08 deleteStagingFile(UUID)")
                .isFalse();

        assertThat(page.locator(".alert.alert-success")).containsText("Import canceled.");
        assertThat(page.locator(".alert.alert-error")).hasCount(0);
    }

    @Test
    void givenConfirmPage_whenAdminSubmitsWithoutTickingCheckbox_thenSeesFieldError(
            @TempDir Path tempDir) throws IOException {
        // given / when / then

        // given — reach the confirm page
        uploadFixtureAndOpenPreview(tempDir);
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Proceed to Confirm")).click();
        assertThat(page).hasURL(Pattern.compile(".*/admin/backup/import-confirm$"));

        // pre-assert: checkbox unchecked
        assertThat(page.locator("#acknowledged")).not().isChecked();

        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Execute Import")).click();

        // then — stay on the confirm/execute URL (server returned the backup-confirm VIEW via
        // the /import-execute POST endpoint — no redirect on validation error, per Spring MVC
        // convention; URL is /import-execute because that is the POST target, not a redirect).
        assertThat(page).hasURL(Pattern.compile(".*/admin/backup/import-execute$"));

        assertThat(page.locator(".field-error")).isVisible();
        assertThat(page.locator(".field-error"))
                .containsText("You must acknowledge the deletion warning to continue.");

        // no stub flash (server rejected)
        assertThat(page.locator(".alert.alert-success")).hasCount(0);
    }

    @Test
    void givenStagingUuid_whenCookieJarIsClearedBetweenPreviewAndExecute_thenPagesStillFunction(
            @TempDir Path tempDir) throws IOException {
        // given / when / then

        // given — upload fixture + reach preview page in the original context
        uploadFixtureAndOpenPreview(tempDir);

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

        } finally {
            freshContext.close();
        }
    }

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

        private String currentStagingUuid() {
        String value = page.locator("input[name='stagingId']").first().getAttribute("value");
        Assertions.assertThat(value)
                .as("Preview/Confirm form must carry stagingId per D-18")
                .isNotBlank();
        UUID.fromString(value); // fail loud on malformed
        return value;
    }
}
