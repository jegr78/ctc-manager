package org.ctc.sitegen.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.ctc.integration.OutboundPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class SitePublicationServiceTest {

    @TempDir
    Path temporary;

    private SitePublicationService service;
    private Path directory;
    private Path source;

    @BeforeEach
    void setUp() throws Exception {
        directory = temporary.resolve("publication");
        source = Files.createDirectory(temporary.resolve("site"));
        Files.writeString(source.resolve("index.html"), "<html><body>snapshot A</body></html>");
        Files.createDirectory(source.resolve("assets"));
        Files.writeString(source.resolve("assets/site.css"), "body { color: black; }");
        service = create(true, "prod", true);
    }

    private SitePublicationService create(boolean enabled, String profile, boolean outbound) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return new SitePublicationService(directory.toString(), "test-version", enabled,
                environment, new OutboundPolicy(outbound, environment), new ObjectMapper());
    }

    @Test
    void givenSnapshotA_whenBIsGenerated_thenAStaysImmutableAndCanBeApprovedExactly() throws Exception {
        var first = service.capture(source);
        assertThat(first).isNotNull();
        Files.writeString(source.resolve("index.html"), "<html><body>snapshot B</body></html>");
        var second = service.capture(source);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(service.asset(first.id(), "index.html")).hasContent("<html><body>snapshot A</body></html>");
        var request = service.submit(first.id(), first.manifestSha256());
        assertThat(request.snapshotId()).isEqualTo(first.id());
        assertThat(request.manifestSha256()).isEqualTo(first.manifestSha256());
        assertThat(request.approved()).isTrue();
    }

    @Test
    void givenChangedSnapshotBytes_whenPublished_thenNoRequestIsCreated() throws Exception {
        var snapshot = service.capture(source);
        assertThat(snapshot).isNotNull();
        Files.writeString(directory.resolve("snapshots").resolve(snapshot.id()).resolve("site/index.html"), "changed");

        assertThatThrownBy(() -> service.submit(snapshot.id(), snapshot.manifestSha256())).isInstanceOf(Exception.class);
        assertThat(directory.resolve("request.json")).doesNotExist();
    }

    @Test
    void givenPrivateFileOrLink_whenCaptured_thenNoSnapshotIsAccepted() throws Exception {
        Files.writeString(source.resolve(".env"), "PRIVATE=never-publish");
        assertThatThrownBy(() -> service.capture(source)).isInstanceOf(Exception.class);
        Files.delete(source.resolve(".env"));
        Files.createSymbolicLink(source.resolve("linked.html"), source.resolve("index.html"));
        assertThatThrownBy(() -> service.capture(source)).isInstanceOf(Exception.class);
    }

    @Test
    void givenSnapshot_whenPathEscapesOrIsNotListed_thenPreviewRefuses() throws Exception {
        var snapshot = service.capture(source);
        assertThat(snapshot).isNotNull();
        assertThatThrownBy(() -> service.asset(snapshot.id(), "../request.json")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> service.asset(snapshot.id(), "not-generated.html")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> service.asset("../../private", "index.html")).isInstanceOf(Exception.class);
    }

    @Test
    void givenDisabledDevOrRecovery_whenSubmitted_thenNoRealPublishCanBeQueued() throws Exception {
        var snapshot = service.capture(source);
        assertThat(snapshot).isNotNull();
        for (var blocked : new SitePublicationService[]{create(false, "prod", true), create(true, "dev", true),
                create(true, "prod", false), create(true, "recovery-check", true)}) {
            assertThat(blocked.isPublicationEnabled()).isFalse();
            assertThatThrownBy(() -> blocked.submit(snapshot.id(), snapshot.manifestSha256())).isInstanceOf(IllegalStateException.class);
        }
        assertThat(directory.resolve("request.json")).doesNotExist();
    }

    @Test
    void givenActiveRequest_whenClickedAgain_thenSameRequestIsReturnedAndBWaits() throws Exception {
        var snapshot = service.capture(source);
        assertThat(snapshot).isNotNull();
        var first = service.submit(snapshot.id(), snapshot.manifestSha256());
        var duplicate = service.submit(snapshot.id(), snapshot.manifestSha256());
        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(duplicate.attempt()).isEqualTo(first.attempt());
        Files.writeString(source.resolve("index.html"), "newer snapshot");
        var newer = service.capture(source);
        assertThatThrownBy(() -> service.submit(newer.id(), newer.manifestSha256())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givenRestoredPendingApproval_whenStarted_thenItStaysSuspendedUntilExplicitSameSnapshotApproval() throws Exception {
        var snapshot = service.capture(source);
        assertThat(snapshot).isNotNull();
        var request = service.submit(snapshot.id(), snapshot.manifestSha256());
        var restored = create(true, "prod", true);
        restored.suspendPending();

        assertThat(restored.publication().stage()).isEqualTo("suspended");
        var reapproved = restored.submit(snapshot.id(), snapshot.manifestSha256());
        assertThat(reapproved.id()).isEqualTo(request.id());
        assertThat(reapproved.attempt()).isEqualTo(request.attempt() + 1);
        assertThat(reapproved.approved()).isTrue();
    }

    @Test
    void givenWrongManifestHash_whenSubmitted_thenNoRequestIsCreated() throws Exception {
        var snapshot = service.capture(source);
        assertThat(snapshot).isNotNull();
        assertThatThrownBy(() -> service.submit(snapshot.id(), "0".repeat(64))).isInstanceOf(IllegalArgumentException.class);
        assertThat(directory.resolve("request.json")).doesNotExist();
    }
    @Test
    void givenExtraSnapshotFile_whenApproved_thenNoRequestIsCreated() throws Exception {
        var snapshot = service.capture(source);
        Files.writeString(directory.resolve("snapshots").resolve(snapshot.id()).resolve("site/extra.html"), "unexpected");
        assertThatThrownBy(() -> service.submit(snapshot.id(), snapshot.manifestSha256())).isInstanceOf(java.io.IOException.class);
        assertThat(directory.resolve("request.json")).doesNotExist();
    }

    @Test
    void givenCorruptStatus_whenRestarted_thenApprovalIsRevokedWithoutBlockingApplicationStartup() throws Exception {
        var snapshot = service.capture(source);
        service.submit(snapshot.id(), snapshot.manifestSha256());
        Files.writeString(directory.resolve("status.json"), "invalid json");
        service.suspendPending();
        assertThat(service.publication().stage()).isEqualTo("suspended");
    }

    @Test
    void givenCorruptRequest_whenStarted_thenCoreStartupContinuesButPublisherIsDisabled() throws Exception {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("request.json"), "broken");
        service.suspendPending();
        assertThat(service.isPublicationEnabled()).isFalse();
        assertThat(directory.resolve("runtime.json")).doesNotExist();
    }

    @Test
    void givenManySnapshots_whenCaptured_thenRecentAndRequestedSnapshotsAreRetained() throws Exception {
        var first = service.capture(source);
        service.submit(first.id(), first.manifestSha256());
        for (int i = 0; i < 5; i++) service.capture(source);
        assertThat(service.snapshot(first.id()).id()).isEqualTo(first.id());
        try (var snapshots = Files.list(directory.resolve("snapshots"))) {
            assertThat(snapshots.count()).isEqualTo(4);
        }
    }

    @Test
    void givenCallerOwnedManifestMap_whenChanged_thenStoredManifestRemainsImmutable() {
        var files = new java.util.HashMap<String, SitePublicationService.Artifact>();
        files.put("index.html", new SitePublicationService.Artifact(1, "0".repeat(64)));
        var manifest = new SitePublicationService.Manifest(1, "fixture", "fixture", "fixture", files);
        files.put("extra.html", new SitePublicationService.Artifact(2, "1".repeat(64)));
        assertThat(manifest.files()).containsOnlyKeys("index.html");
        assertThatThrownBy(() -> manifest.files().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void givenCompletedPublication_whenRestarted_thenApprovalIsRevokedButCompletionDoesNotBlockNextSnapshot() throws Exception {
        var snapshot = service.capture(source);
        var request = service.submit(snapshot.id(), snapshot.manifestSha256());
        var status = new SitePublicationService.Status(1, request.id(), snapshot.id(), snapshot.manifestSha256(),
                request.attempt(), "published", "Completed", "site/test", "a".repeat(40), 12L, "b".repeat(40), 34L, "2026-11-01T10:00:00Z");
        new ObjectMapper().writeValue(directory.resolve("status.json").toFile(), status);
        service.suspendPending();
        assertThat(new ObjectMapper().readTree(directory.resolve("request.json").toFile()).get("approved").asBoolean()).isFalse();
        assertThat(service.publication().stage()).isEqualTo("published");
        assertThat(service.publication().pr()).isEqualTo(12L);
        Files.writeString(source.resolve("index.html"), "next site");
        var next = service.capture(source);
        assertThat(service.submit(next.id(), next.manifestSha256()).id()).isNotEqualTo(request.id());
    }

}
