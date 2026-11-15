package org.ctc.domain.service;

import static org.ctc.util.LogSanitizer.sanitize;
import static org.springframework.util.StringUtils.hasText;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.ctc.integration.ImageDownloadClient;
import org.ctc.integration.OutboundPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
public class FileStorageService {

	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
			"image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf");

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
			".png", ".jpg", ".jpeg", ".gif", ".webp", ".pdf");

	private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

	private final OutboundPolicy outboundPolicy;
	private final ImageDownloadClient downloads;
	private final Path uploadDir;

	@Autowired
	public FileStorageService(OutboundPolicy outboundPolicy, ImageDownloadClient downloads, @Value("${app.upload-dir:uploads}") String uploadDir) {
		this.outboundPolicy = outboundPolicy;
		this.downloads = downloads;
		this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
	}

	public FileStorageService(OutboundPolicy outboundPolicy, String uploadDir) {
		this(outboundPolicy, new ImageDownloadClient(), uploadDir);
	}

	public String store(UUID raceId, MultipartFile file) throws IOException {
		validate(file);
		validateNoPathTraversal(file.getOriginalFilename());

		Path raceDir = uploadDir.resolve("races").resolve(raceId.toString());
		Files.createDirectories(raceDir);

		String filename = UUID.randomUUID().toString().substring(0, 8) + "_" + sanitizeFilename(file.getOriginalFilename());
		Path target = raceDir.resolve(filename);
		validatePathWithinUploadDir(target);
		file.transferTo(target);

		log.info("Stored file: {} ({} bytes)", target, file.getSize());
		return "/uploads/races/" + raceId + "/" + filename;
	}

	/** Whether an {@code /uploads/} URL names a stored regular file inside the upload directory. */
	public boolean exists(String url) {
		if (url == null || !url.startsWith("/uploads/")) return false;
		Path file = uploadDir.resolve(url.substring("/uploads/".length())).normalize();
		return file.startsWith(uploadDir) && Files.isRegularFile(file);
	}

	public void delete(String url) {
		if (url == null || !url.startsWith("/uploads/")) {
			return;
		}

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
		if (!hasText(name)) {
			throw new IllegalArgumentException("Filename is required");
		}
		String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')).toLowerCase() : "";
		if (!ALLOWED_EXTENSIONS.contains(ext)) {
			throw new IllegalArgumentException("File extension not allowed. Allowed: PNG, JPG, GIF, WebP, PDF");
		}
	}

	/**
	 * Downloads an image through {@link ImageDownloadClient} and stores it; the filename extension names the expected
	 * image type. The caller removes the file again when its transaction rolls back.
	 */
	public String storeFromUrl(String subDir, UUID entityId, String sourceUrl, String filename) throws IOException {
		validatePathWithinUploadDir(uploadDir.resolve(subDir).resolve(entityId.toString()).resolve(sanitizeFilename(filename)));
		return storeBytes(subDir, entityId, filename, downloadImage(sourceUrl, filename));
	}

	/** Downloads an image of the type the filename extension declares, without storing it. */
	public byte[] downloadImage(String sourceUrl, String filename) throws IOException {
		outboundPolicy.requireEnabled();
		return downloads.download(sourceUrl, ImageContent.Format.ofFilename(filename));
	}

	/** Stores bytes under a fresh name in the entity's folder and returns its {@code /uploads/} URL. */
	public String storeBytes(String subDir, UUID entityId, String filename, byte[] data) throws IOException {
		Path dir = uploadDir.resolve(subDir).resolve(entityId.toString());
		validatePathWithinUploadDir(dir);
		String safeName = UUID.randomUUID().toString().substring(0, 8) + "_" + sanitizeFilename(filename);
		Path target = dir.resolve(safeName);
		validatePathWithinUploadDir(target);
		Files.createDirectories(dir);
		try {
			Files.write(target, data);
		} catch (IOException | RuntimeException failure) {
			Files.deleteIfExists(target);
			throw failure;
		}
		log.info("Stored file: {} ({} bytes)", target, data.length);
		return "/uploads/" + subDir + "/" + entityId + "/" + safeName;
	}

	/** Whether the stored file of an {@code /uploads/} URL holds exactly these bytes. */
	public boolean hasContent(String url, byte[] data) {
		if (!exists(url)) return false;
		try {
			return java.util.Arrays.equals(Files.readAllBytes(uploadDir.resolve(url.substring("/uploads/".length())).normalize()), data);
		} catch (IOException unreadable) {
			return false;
		}
	}

	public String storeImage(String subDir, UUID entityId, MultipartFile file) throws IOException {
		validate(file);
		validateNoPathTraversal(file.getOriginalFilename());
		Path dir = uploadDir.resolve(subDir).resolve(entityId.toString());
		Files.createDirectories(dir);
		String safeName = UUID.randomUUID().toString().substring(0, 8) + "_" + sanitizeFilename(file.getOriginalFilename());
		Path target = dir.resolve(safeName);
		validatePathWithinUploadDir(target);
		String url = "/uploads/" + subDir + "/" + entityId + "/" + safeName;
        try {
            file.transferTo(target);
        } catch (IOException | RuntimeException failure) {
            delete(url);
            throw failure;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) delete(url);
                }
            });
        }
		log.info("Stored image: {} ({} bytes)", target, file.getSize());
		return url;
	}

    /** Removes the file when the current transaction rolls back; an unknown outcome keeps it. */
    public void deleteOnRollback(String url) {
        if (url == null || !TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) delete(url);
            }
        });
    }

    public void deleteAfterCommit(String url) {
        if (url == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete(url);
                }
            });
        } else {
            delete(url);
        }
    }

	private String sanitizeFilename(String filename) {
		if (filename == null) {
			return "file";
		}
		return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	// PATH_TRAVERSAL defense: toAbsolutePath().normalize().startsWith() check.
	// find-sec-bugs detects unresolved path usage at call sites before this validation is
	// invoked. See config/spotbugs-exclude.xml FileStorageService PATH_TRAVERSAL_IN entry
	// for the corresponding suppression rationale.
	private void validatePathWithinUploadDir(Path target) {
		Path normalized = target.toAbsolutePath().normalize();
		if (!normalized.startsWith(uploadDir)) {
			log.warn("Attempted path traversal: {}", target);
			throw new IllegalArgumentException("Path traversal detected: " + target);
		}
	}

	private void validateNoPathTraversal(String filename) {
		if (filename != null && (filename.contains("..") || filename.startsWith("/"))) {
			log.warn("Attempted path traversal in filename: {}", sanitize(filename));
			throw new IllegalArgumentException("Path traversal detected in filename: " + filename);
		}
	}
}
