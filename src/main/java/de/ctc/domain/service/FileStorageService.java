package de.ctc.domain.service;

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

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf");

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
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("File type not allowed. Allowed: PNG, JPG, GIF, WebP, PDF");
        }
    }

    private String sanitize(String filename) {
        if (filename == null) return "file";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
