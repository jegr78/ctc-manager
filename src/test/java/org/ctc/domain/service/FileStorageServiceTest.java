package org.ctc.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService(tempDir.toString());
    }

    @Test
    void shouldStoreFileAndReturnUrl() throws IOException {
        var file = new MockMultipartFile("file", "test.png", "image/png", new byte[]{1, 2, 3});
        var raceId = UUID.randomUUID();

        String url = fileStorageService.store(raceId, file);

        assertTrue(url.startsWith("/uploads/races/" + raceId + "/"));
        assertTrue(url.endsWith("_test.png"));

        // Verify file exists on disk
        Path storedFile = tempDir.resolve(url.substring("/uploads/".length()));
        assertTrue(Files.exists(storedFile));
        assertEquals(3, Files.size(storedFile));
    }

    @Test
    void shouldRejectEmptyFile() {
        var file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.store(UUID.randomUUID(), file));
    }

    @Test
    void shouldRejectOversizedFile() {
        byte[] bigContent = new byte[11 * 1024 * 1024]; // 11 MB
        var file = new MockMultipartFile("file", "big.png", "image/png", bigContent);

        var ex = assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.store(UUID.randomUUID(), file));
        assertTrue(ex.getMessage().contains("max 10 MB"));
    }

    @Test
    void shouldRejectDisallowedContentType() {
        var file = new MockMultipartFile("file", "script.js", "application/javascript", new byte[]{1});

        var ex = assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.store(UUID.randomUUID(), file));
        assertTrue(ex.getMessage().contains("not allowed"));
    }

    @Test
    void shouldRejectDisallowedExtension() {
        // Spoofed content type but wrong extension
        var file = new MockMultipartFile("file", "malicious.exe", "image/png", new byte[]{1});

        var ex = assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.store(UUID.randomUUID(), file));
        assertTrue(ex.getMessage().contains("extension not allowed"));
    }

    @Test
    void shouldDeleteExistingFile() throws IOException {
        var file = new MockMultipartFile("file", "delete-me.png", "image/png", new byte[]{1, 2});
        var raceId = UUID.randomUUID();
        String url = fileStorageService.store(raceId, file);

        Path storedFile = tempDir.resolve(url.substring("/uploads/".length()));
        assertTrue(Files.exists(storedFile));

        fileStorageService.delete(url);
        assertFalse(Files.exists(storedFile));
    }

    @Test
    void shouldIgnoreDeleteOfNonExistentFile() {
        assertDoesNotThrow(() -> fileStorageService.delete("/uploads/races/nonexistent/file.png"));
    }

    @Test
    void shouldBlockPathTraversalInDelete() throws IOException {
        // Create a file outside the upload dir
        Path outsideFile = tempDir.getParent().resolve("sensitive.txt");
        Files.writeString(outsideFile, "secret");

        fileStorageService.delete("/uploads/../sensitive.txt");

        // File should still exist — traversal was blocked
        assertTrue(Files.exists(outsideFile));
        Files.deleteIfExists(outsideFile);
    }

    @Test
    void shouldSanitizeFilename() throws IOException {
        var file = new MockMultipartFile("file", "my file (1).png", "image/png", new byte[]{1});
        String url = fileStorageService.store(UUID.randomUUID(), file);

        // Spaces and parentheses should be replaced with underscores
        assertTrue(url.contains("my_file__1_.png"));
    }
}
