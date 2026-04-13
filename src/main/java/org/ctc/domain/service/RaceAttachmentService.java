package org.ctc.domain.service;

import org.ctc.domain.model.AttachmentType;
import org.ctc.domain.model.RaceAttachment;
import org.ctc.domain.repository.RaceAttachmentRepository;
import org.ctc.domain.repository.RaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaceAttachmentService {

    private final RaceRepository raceRepository;
    private final RaceAttachmentRepository raceAttachmentRepository;
    private final FileStorageService fileStorageService;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Transactional
    public String uploadAttachment(UUID raceId, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Filename is required");
        }
        var race = raceRepository.findById(raceId).orElseThrow();
        try {
            String url = fileStorageService.store(raceId, file);
            var attachment = new RaceAttachment(race, AttachmentType.FILE, originalFilename, url);
            raceAttachmentRepository.save(attachment);
            return originalFilename;
        } catch (IOException e) {
            log.error("Upload failed for race {}", raceId, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Transactional
    public String addLink(UUID raceId, String name, String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("Link must start with http:// or https://");
        }
        var race = raceRepository.findById(raceId).orElseThrow();
        var attachment = new RaceAttachment(race, AttachmentType.LINK, name, url);
        raceAttachmentRepository.save(attachment);
        return name;
    }

    @Transactional
    public UUID deleteAttachment(UUID attachmentId) {
        var attachment = raceAttachmentRepository.findById(attachmentId).orElseThrow();
        UUID raceId = attachment.getRace().getId();
        if (attachment.getType() == AttachmentType.FILE) {
            fileStorageService.delete(attachment.getUrl());
        }
        raceAttachmentRepository.delete(attachment);
        return raceId;
    }

    public ResponseEntity<Resource> downloadAttachment(UUID attachmentId) {
        var attachment = raceAttachmentRepository.findById(attachmentId).orElseThrow();
        if (attachment.getType() != AttachmentType.FILE) {
            return ResponseEntity.badRequest().build();
        }

        // SECU-02: resolve path and enforce upload-dir boundary
        String url = attachment.getUrl();
        if (!url.startsWith("/uploads/")) {
            log.warn("Attachment URL has unexpected format, attachmentId={}", attachmentId);
            return ResponseEntity.badRequest().build();
        }
        Path uploadDirPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path file = uploadDirPath.resolve(url.substring("/uploads/".length())).normalize();
        if (!file.startsWith(uploadDirPath)) {
            log.warn("Path traversal attempt in download, attachmentId={}", attachmentId);
            return ResponseEntity.badRequest().build();
        }

        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        // DATA-02: null-safe content type
        String probed = null;
        try {
            probed = Files.probeContentType(file);
        } catch (IOException e) {
            log.debug("Could not probe content type for {}", file, e);
        }
        String contentType = (probed != null) ? probed : "application/octet-stream";

        // SECU-05: sanitize filename to prevent header injection
        String safeName = attachment.getName().replaceAll("[\\r\\n\";]", "_");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeName + getExtension(file) + "\"")
                .body(new FileSystemResource(file));
    }

    private String getExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }
}
