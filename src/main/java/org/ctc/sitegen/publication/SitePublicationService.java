package org.ctc.sitegen.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.ctc.integration.OutboundPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class SitePublicationService {
    private static final Set<String> EXTENSIONS = Set.of("html", "css", "js", "json", "png", "jpg", "jpeg", "webp", "gif", "svg", "ico", "woff", "woff2", "ttf");
    private static final Set<String> FINISHED = Set.of("published", "no-change", "cancelled");
    private static final Set<String> STAGES = Set.of("preparing", "checking", "deploying", "published", "no-change", "cancelled", "failed");
    private static final long MAX_FILE = 32L * 1024 * 1024;
    private static final long MAX_SITE = 512L * 1024 * 1024;
    private final Path root;
    private final String version;
    private final boolean enabled;
    private final Environment environment;
    private final OutboundPolicy policy;
    private final ObjectMapper mapper;
    private boolean storageReady = true;

    public SitePublicationService(
            @Value("${ctc.site.publication.directory:${app.upload-dir:data/dev/uploads}-site-publication}") String directory,
            @Value("${app.version:unknown}") String version,
            @Value("${ctc.site.publication.enabled:false}") boolean enabled,
            Environment environment, OutboundPolicy policy, ObjectMapper mapper) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
        this.version = version;
        this.enabled = enabled;
        this.environment = environment;
        this.policy = policy;
        this.mapper = mapper;
    }

    public synchronized Snapshot capture(Path source) throws IOException {
        source = source.toAbsolutePath().normalize();
        if (source.startsWith(root) || root.startsWith(source) || Files.isSymbolicLink(source)) {
            throw new IOException("Snapshot source overlaps private publication storage");
        }
        ensureDirectory(root);
        Path snapshots = ensureDirectory(root.resolve("snapshots"));
        String id = UUID.randomUUID().toString();
        Path partial = Files.createDirectory(snapshots.resolve(".partial-" + id));
        privateDirectory(partial);
        Path site = ensureDirectory(partial.resolve("site"));
        var files = new TreeMap<String, Artifact>();
        long[] bytes = {0};
        Path sourceRoot = source;
        try {
            Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                    if (attributes.isSymbolicLink()) throw new IOException("Linked snapshot directory");
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                    String name = sourceRoot.relativize(path).toString().replace('\\', '/');
                    validateName(name);
                    regular(path);
                    bytes[0] += attributes.size();
                    if (attributes.size() > MAX_FILE || bytes[0] > MAX_SITE || files.size() >= 100_000) {
                        throw new IOException("Snapshot exceeds its file or byte limit");
                    }
                    Path destination = site.resolve(name);
                    ensureDirectory(destination.getParent());
                    Files.copy(path, destination, LinkOption.NOFOLLOW_LINKS);
                    regular(destination);
                    privateFile(destination);
                    String digest = hash(destination);
                    if (Files.size(path) != attributes.size() || !hash(path).equals(digest)) {
                        throw new IOException("Site changed during snapshot capture");
                    }
                    files.put(name, new Artifact(Files.size(destination), digest));
                    return FileVisitResult.CONTINUE;
                }
            });
            if (!files.containsKey("index.html")) throw new IOException("A snapshot needs an index page");
            var manifest = new Manifest(1, id, Instant.now().toString(), version, files);
            write(partial.resolve("manifest.json"), manifest);
            move(partial, snapshots.resolve(id));
            pruneSnapshots();
            return snapshot(id);
        } finally {
            if (Files.exists(partial, LinkOption.NOFOLLOW_LINKS)) deletePartial(partial);
        }
    }

    private void pruneSnapshots() throws IOException {
        Request current;
        try {
            current = request();
        } catch (IOException | RuntimeException error) {
            return;
        }
        var candidates = new java.util.ArrayList<Snapshot>();
        try (var paths = Files.list(root.resolve("snapshots"))) {
            for (Path path : paths.toList()) {
                String id = path.getFileName().toString();
                if (!id.startsWith(".")) candidates.add(snapshot(id));
            }
        }
        candidates.sort(java.util.Comparator.comparing(Snapshot::createdAt).reversed());
        for (int index = 3; index < candidates.size(); index++) {
            Snapshot candidate = candidates.get(index);
            if (current == null || !candidate.id().equals(current.snapshotId())) deletePartial(snapshotPath(candidate.id()));
        }
    }

    public synchronized Snapshot snapshot(String id) throws IOException {
        Manifest manifest = manifest(id);
        long bytes = manifest.files().values().stream().mapToLong(Artifact::bytes).sum();
        return new Snapshot(id, hash(snapshotPath(id).resolve("manifest.json")), manifest.createdAt(), manifest.appVersion(), manifest.files().size(), bytes);
    }

    public synchronized Path asset(String id, String name) throws IOException {
        validateName(name);
        Manifest manifest = manifest(id);
        Artifact expected = manifest.files().get(name);
        if (expected == null) throw new IOException("File is not part of this snapshot");
        Path file = snapshotPath(id).resolve("site").resolve(name).normalize();
        plain(file);
        regular(file);
        if (Files.size(file) != expected.bytes() || !hash(file).equals(expected.sha256())) {
            throw new IOException("Snapshot file changed");
        }
        return file;
    }

    public boolean isPublicationEnabled() {
        return storageReady && enabled && environment.matchesProfiles("prod", "docker") && policy.isEnabled();
    }

    public synchronized Request submit(String id, String expectedHash) throws IOException {
        if (!isPublicationEnabled()) throw new IllegalStateException("Publication is disabled for this installation");
        Snapshot snapshot = snapshot(id);
        if (!snapshot.manifestSha256().equals(expectedHash)) throw new IllegalArgumentException("Snapshot approval does not match its manifest");
        Manifest manifest = manifest(id);
        for (String name : manifest.files().keySet()) asset(id, name);
        try (var paths = Files.walk(snapshotPath(id).resolve("site"))) {
            long count = paths.filter(path -> !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).count();
            if (count != manifest.files().size()) throw new IOException("Snapshot contains unlisted files");
        }
        Request current = request();
        int attempt = 1;
        String requestId = UUID.randomUUID().toString();
        String createdAt = Instant.now().toString();
        if (current != null) {
            Publication state = publication();
            boolean same = current.snapshotId().equals(id) && current.manifestSha256().equals(expectedHash);
            if (!FINISHED.contains(state.stage())) {
                if (!same) throw new IllegalStateException("The previous publication needs to finish or be reconciled first");
                if (current.approved() && !state.stage().equals("failed")) return current;
                requestId = current.id();
                attempt = Math.addExact(current.attempt(), 1);
                createdAt = current.createdAt();
            }
        }
        var request = new Request(1, requestId, id, expectedHash, attempt, true, createdAt, Instant.now().toString());
        write(root.resolve("request.json"), request);
        return request;
    }

    @PostConstruct
    public synchronized void suspendPending() {
        storageReady = false;
        try {
            Request current = request();
            if (current != null && current.approved()) {
                write(root.resolve("request.json"), new Request(1, current.id(), current.snapshotId(), current.manifestSha256(),
                        current.attempt(), false, current.createdAt(), current.approvedAt()));
            }
            storageReady = true;
            write(root.resolve("runtime.json"), new RuntimeState(1, UUID.randomUUID().toString(), Instant.now().toString(), isPublicationEnabled()));
        } catch (IOException | RuntimeException error) {
            storageReady = false;
        }
    }

    public synchronized Publication publication() throws IOException {
        Request request = request();
        if (request == null) return new Publication("none", null, null, 0, "", null, null);
        if (!request.approved()) return new Publication("suspended", request.id(), request.snapshotId(), request.attempt(), "Review this snapshot and approve again before continuing.", null, null);
        Path file = root.resolve("status.json");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return queued(request);
        Status status = read(file, Status.class);
        if (status.format() != 1 || !STAGES.contains(status.stage())) throw new IOException("Invalid publication status");
        if (!request.id().equals(status.requestId()) || !request.snapshotId().equals(status.snapshotId())
                || !request.manifestSha256().equals(status.manifestSha256()) || request.attempt() != status.attempt()) return queued(request);
        if ((status.pr() != null && status.pr() < 1) || (status.runId() != null && status.runId() < 1)) throw new IOException("Invalid publication reference");
        String detail = status.detail() == null ? "" : status.detail();
        if (detail.length() > 1000) throw new IOException("Publication detail exceeds limit");
        return new Publication(status.stage(), request.id(), request.snapshotId(), request.attempt(), detail, status.pr(), status.runId());
    }

    private static Publication queued(Request request) {
        return new Publication("queued", request.id(), request.snapshotId(), request.attempt(), "Waiting for the host publisher.", null, null);
    }

    private Request request() throws IOException {
        Path file = root.resolve("request.json");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null;
        Request value = read(file, Request.class);
        canonicalId(value.id());
        canonicalId(value.snapshotId());
        if (value.format() != 1 || value.attempt() < 1 || value.attempt() > 1000
                || value.manifestSha256() == null || !value.manifestSha256().matches("[a-f0-9]{64}")) throw new IOException("Invalid publication request");
        return value;
    }

    private Manifest manifest(String id) throws IOException {
        Manifest value = read(snapshotPath(id).resolve("manifest.json"), Manifest.class);
        if (value.format() != 1 || !id.equals(value.id()) || value.files() == null || value.files().isEmpty()
                || value.files().size() > 100_000 || !value.files().containsKey("index.html")) throw new IOException("Invalid snapshot manifest");
        Instant.parse(value.createdAt());
        if (value.appVersion() == null || value.appVersion().length() > 128) throw new IOException("Invalid snapshot version");
        long total = 0;
        for (var entry : value.files().entrySet()) {
            validateName(entry.getKey());
            Artifact artifact = entry.getValue();
            if (artifact == null || artifact.bytes() < 0 || artifact.bytes() > MAX_FILE
                    || artifact.sha256() == null || !artifact.sha256().matches("[a-f0-9]{64}")) throw new IOException("Invalid snapshot artifact");
            total += artifact.bytes();
        }
        if (total > MAX_SITE) throw new IOException("Snapshot exceeds byte limit");
        return value;
    }

    private Path snapshotPath(String id) throws IOException {
        canonicalId(id);
        Path path = root.resolve("snapshots").resolve(id);
        plain(path);
        return path;
    }

    private static void canonicalId(String value) {
        if (value == null || !UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException("Invalid snapshot identity");
    }

    private static void validateName(String value) throws IOException {
        if (value == null || value.isEmpty() || value.contains("\\") || value.chars().anyMatch(c -> c < 32 || c == 127)) throw new IOException("Invalid snapshot path");
        Path path = Path.of(value);
        if (path.isAbsolute() || !path.normalize().toString().replace('\\', '/').equals(value)) throw new IOException("Invalid snapshot path");
        for (Path part : path) {
            if (part.toString().startsWith(".") && !value.equals(".nojekyll")) throw new IOException("Private files cannot enter a snapshot");
        }
        String filename = path.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        if (!value.equals(".nojekyll") && (dot < 0 || !EXTENSIONS.contains(filename.substring(dot + 1).toLowerCase(java.util.Locale.ENGLISH)))) {
            throw new IOException("File type is not part of the public website");
        }
    }

    private void plain(Path path) throws IOException {
        if (!path.toAbsolutePath().normalize().startsWith(root)) throw new IOException("Path leaves publication storage");
        for (Path part = path; part != null && part.startsWith(root); part = part.getParent()) {
            if (Files.isSymbolicLink(part)) throw new IOException("Linked publication path");
        }
    }

    private Path ensureDirectory(Path path) throws IOException {
        plain(path);
        Files.createDirectories(path);
        privateDirectory(path);
        return path;
    }

    private static void regular(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || ((Number) Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS)).longValue() != 1) throw new IOException("Snapshot requires plain files");
    }

    private <T> T read(Path file, Class<T> type) throws IOException {
        plain(file);
        regular(file);
        if (Files.size(file) > 4 * 1024 * 1024) throw new IOException("Publication metadata exceeds limit");
        return mapper.readValue(file.toFile(), type);
    }

    private void write(Path file, Object value) throws IOException {
        ensureDirectory(file.getParent());
        plain(file);
        Path temporary = Files.createTempFile(file.getParent(), ".writing-", ".json");
        try {
            privateFile(temporary);
            mapper.writeValue(temporary.toFile(), value);
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            move(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void move(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        try (var channel = FileChannel.open(target.getParent(), StandardOpenOption.READ)) { channel.force(true); }
    }

    private static String hash(Path path) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void privateDirectory(Path path) throws IOException { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------")); }
    private static void privateFile(Path path) throws IOException { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------")); }
    private static void deletePartial(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException { Files.delete(directory); return FileVisitResult.CONTINUE; }
        });
    }

    public record RuntimeState(int format, String bootId, String startedAt, boolean enabled) { }
    public record Artifact(long bytes, String sha256) { }
    public record Manifest(int format, String id, String createdAt, String appVersion, Map<String, Artifact> files) { }
    public record Snapshot(String id, String manifestSha256, String createdAt, String appVersion, int fileCount, long bytes) { }
    public record Request(int format, String id, String snapshotId, String manifestSha256, int attempt, boolean approved, String createdAt, String approvedAt) { }
    public record Status(int format, String requestId, String snapshotId, String manifestSha256, int attempt, String stage, String detail, String branch, String headSha, Long pr, String mergeSha, Long runId, String updatedAt) { }
    public record Publication(String stage, String requestId, String snapshotId, int attempt, String detail, Long pr, Long runId) {
        public String prUrl() { return pr == null ? null : "https://github.com/jegr78/ctc-manager/pull/" + pr; }
        public String runUrl() { return runId == null ? null : "https://github.com/jegr78/ctc-manager/actions/runs/" + runId; }
        public String publicUrl() { return "https://jegr78.github.io/ctc-manager/"; }
    }
}
