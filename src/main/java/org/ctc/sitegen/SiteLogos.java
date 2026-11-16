package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;

/** Copies team logos from the upload directory into the site assets. */
@Slf4j
final class SiteLogos {
    private SiteLogos() { }

    /**
     * Copies the logo behind an {@code /uploads/} URL to {@code assets/img/logos/} and returns its path below
     * {@code assetsPath}; null without a logo, for a missing file or for a path outside the uploads or inside race uploads.
     */
    static String copy(String uploadDir, String logoUrl, Path outPath, String assetsPath) {
        if (uploadDir == null || logoUrl == null || !logoUrl.startsWith("/uploads/")) {
            return null;
        }
        try {
            Path uploadBase = Path.of(uploadDir).toAbsolutePath().normalize();
            Path logoFile = uploadBase.resolve(logoUrl.substring("/uploads/".length())).normalize();
            if (!logoFile.startsWith(uploadBase)) {
                log.warn("Path traversal attempt in logo URL: {}", logoUrl);
                return null;
            }
            if (!Files.exists(logoFile)) {
                log.warn("Logo file not found, skipping: {}", logoUrl);
                return null;
            }
            Path realBase = uploadBase.toRealPath();
            Path realLogo = logoFile.toRealPath();
            if (logoFile.startsWith(uploadBase.resolve("races")) || !realLogo.startsWith(realBase)
                    || realLogo.startsWith(realBase.resolve("races"))) return null;
            // The UUID-prefixed subdirectory keeps equal file names of different teams apart.
            String relativePart = logoUrl.substring("/uploads/".length());
            Path target = outPath.resolve("assets").resolve("img").resolve("logos").resolve(relativePart);
            Files.createDirectories(target.getParent());
            Files.copy(logoFile, target, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Copied logo: {} -> {}", logoFile, target);
            return assetsPath + "/img/logos/" + relativePart;
        } catch (IOException e) {
            log.warn("Failed to copy logo: {}", logoUrl, e);
            return null;
        }
    }
}
