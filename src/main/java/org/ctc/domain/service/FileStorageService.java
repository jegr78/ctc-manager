package org.ctc.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf");

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".pdf");

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    private final Path uploadDir;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public String store(UUID raceId, MultipartFile file) throws IOException {
        validate(file);

        Path raceDir = uploadDir.resolve("races").resolve(raceId.toString());
        Files.createDirectories(raceDir);

        String filename = UUID.randomUUID().toString().substring(0, 8) + "_" + sanitize(file.getOriginalFilename());
        Path target = raceDir.resolve(filename);
        file.transferTo(target);

        log.info("Stored file: {} ({} bytes)", target, file.getSize());
        return "/uploads/races/" + raceId + "/" + filename;
    }

    public void delete(String url) {
        if (url == null || !url.startsWith("/uploads/")) return;

        Path file = uploadDir.resolve(url.substring("/uploads/".length())).normalize();
        if (!file.startsWith(uploadDir)) {
            log.warn("Attempted path traversal in delete: {}", url);
            return;
        }
        try {
            Files.deleteIfExists(file);
            log.info("Deleted file: {}", file);
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", file, e);
        }
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File too large (max 10 MB)");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("File type not allowed. Allowed: PNG, JPG, GIF, WebP, PDF");
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Filename is required");
        }
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')).toLowerCase() : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("File extension not allowed. Allowed: PNG, JPG, GIF, WebP, PDF");
        }
    }

    public String storeFromUrl(String subDir, UUID entityId, String sourceUrl, String filename) throws IOException {
        if (sourceUrl == null || !sourceUrl.toLowerCase().startsWith("https://")) {
            log.warn("Rejected non-HTTPS URL: {}", sourceUrl);
            throw new IllegalArgumentException("Only HTTPS URLs allowed: " + sourceUrl);
        }
        Path dir = uploadDir.resolve(subDir).resolve(entityId.toString());
        Files.createDirectories(dir);
        String safeName = UUID.randomUUID().toString().substring(0, 8) + "_" + sanitize(filename);
        Path target = dir.resolve(safeName);
        try (var in = java.net.URI.create(sourceUrl).toURL().openStream()) {
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("Downloaded file: {} from {}", target, sourceUrl);
        return "/uploads/" + subDir + "/" + entityId + "/" + safeName;
    }

    public String storeImage(String subDir, UUID entityId, MultipartFile file) throws IOException {
        validate(file);
        Path dir = uploadDir.resolve(subDir).resolve(entityId.toString());
        Files.createDirectories(dir);
        String safeName = UUID.randomUUID().toString().substring(0, 8) + "_" + sanitize(file.getOriginalFilename());
        Path target = dir.resolve(safeName);
        file.transferTo(target);
        log.info("Stored image: {} ({} bytes)", target, file.getSize());
        return "/uploads/" + subDir + "/" + entityId + "/" + safeName;
    }

    private String sanitize(String filename) {
        if (filename == null) return "file";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
