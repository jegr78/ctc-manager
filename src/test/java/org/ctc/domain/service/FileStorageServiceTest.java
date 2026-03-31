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
    void givenValidFile_whenStore_thenFileStoredAndUrlReturned() throws IOException {
        // given
        var file = new MockMultipartFile("file", "test.png", "image/png", new byte[]{1, 2, 3});
        var raceId = UUID.randomUUID();

        // when
        String url = fileStorageService.store(raceId, file);

        // then
        assertTrue(url.startsWith("/uploads/races/" + raceId + "/"));
        assertTrue(url.endsWith("_test.png"));

        // Verify file exists on disk
        Path storedFile = tempDir.resolve(url.substring("/uploads/".length()));
        assertTrue(Files.exists(storedFile));
        assertEquals(3, Files.size(storedFile));
    }

    @Test
    void givenEmptyFile_whenStore_thenThrowsException() {
        // given
        var file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        // when / then
        assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.store(UUID.randomUUID(), file));
    }

    @Test
    void givenOversizedFile_whenStore_thenThrowsException() {
        // given
        byte[] bigContent = new byte[11 * 1024 * 1024]; // 11 MB
        var file = new MockMultipartFile("file", "big.png", "image/png", bigContent);

        // when / then
        var ex = assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.store(UUID.randomUUID(), file));
        assertTrue(ex.getMessage().contains("max 10 MB"));
    }

    @Test
    void givenDisallowedContentType_whenStore_thenThrowsException() {
        // given
        var file = new MockMultipartFile("file", "script.js", "application/javascript", new byte[]{1});

        // when / then
        var ex = assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.store(UUID.randomUUID(), file));
        assertTrue(ex.getMessage().contains("not allowed"));
    }

    @Test
    void givenDisallowedExtension_whenStore_thenThrowsException() {
        // given
        // Spoofed content type but wrong extension
        var file = new MockMultipartFile("file", "malicious.exe", "image/png", new byte[]{1});

        // when / then
        var ex = assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.store(UUID.randomUUID(), file));
        assertTrue(ex.getMessage().contains("extension not allowed"));
    }

    @Test
    void givenStoredFile_whenDelete_thenFileRemoved() throws IOException {
        // given
        var file = new MockMultipartFile("file", "delete-me.png", "image/png", new byte[]{1, 2});
        var raceId = UUID.randomUUID();
        String url = fileStorageService.store(raceId, file);

        Path storedFile = tempDir.resolve(url.substring("/uploads/".length()));
        assertTrue(Files.exists(storedFile));

        // when
        fileStorageService.delete(url);

        // then
        assertFalse(Files.exists(storedFile));
    }

    @Test
    void givenNonExistentPath_whenDelete_thenNoExceptionThrown() {
        // when / then
        assertDoesNotThrow(() -> fileStorageService.delete("/uploads/races/nonexistent/file.png"));
    }

    @Test
    void givenPathTraversalUrl_whenDelete_thenTraversalBlocked() throws IOException {
        // given
        // Create a file outside the upload dir
        Path outsideFile = tempDir.getParent().resolve("sensitive.txt");
        Files.writeString(outsideFile, "secret");

        // when
        fileStorageService.delete("/uploads/../sensitive.txt");

        // then
        // File should still exist — traversal was blocked
        assertTrue(Files.exists(outsideFile));
        Files.deleteIfExists(outsideFile);
    }

    @Test
    void givenFilenameWithSpecialChars_whenStore_thenFilenameIsSanitized() throws IOException {
        // given
        var file = new MockMultipartFile("file", "my file (1).png", "image/png", new byte[]{1});

        // when
        String url = fileStorageService.store(UUID.randomUUID(), file);

        // then
        // Spaces and parentheses should be replaced with underscores
        assertTrue(url.contains("my_file__1_.png"));
    }

    @Test
    void givenValidImageFile_whenStoreImage_thenFileStoredAndUrlReturned() throws IOException {
        // given
        var file = new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1, 2, 3});
        var entityId = UUID.randomUUID();

        // when
        String url = fileStorageService.storeImage("season-teams", entityId, file);

        // then
        assertTrue(url.startsWith("/uploads/season-teams/" + entityId + "/"));
        assertTrue(url.endsWith("_logo.png"));

        Path storedFile = tempDir.resolve(url.substring("/uploads/".length()));
        assertTrue(Files.exists(storedFile));
        assertEquals(3, Files.size(storedFile));
    }

    @Test
    void givenNullFilename_whenStore_thenThrowsException() {
        // given
        var file = new MockMultipartFile("file", null, "image/png", new byte[]{1});

        // when / then
        assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.store(UUID.randomUUID(), file));
    }

    @Test
    void givenBlankFilename_whenStore_thenThrowsException() {
        // given
        var file = new MockMultipartFile("file", "   ", "image/png", new byte[]{1});

        // when / then
        var ex = assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.store(UUID.randomUUID(), file));
        assertTrue(ex.getMessage().contains("Filename is required"));
    }

    @Test
    void givenFilenameWithoutExtension_whenStore_thenThrowsException() {
        // given
        var file = new MockMultipartFile("file", "testfile", "image/png", new byte[]{1});

        // when / then
        var ex = assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.store(UUID.randomUUID(), file));
        assertTrue(ex.getMessage().contains("extension not allowed"));
    }

    @Test
    void givenSourceUrl_whenStoreFromUrl_thenFileStoredAndUrlReturned() throws IOException {
        // given
        Path sourceFile = tempDir.resolve("source-image.png");
        Files.write(sourceFile, new byte[]{10, 20, 30});

        var entityId = UUID.randomUUID();

        // when
        String url = fileStorageService.storeFromUrl("cars", entityId,
                sourceFile.toUri().toString(), "car-photo.png");

        // then
        assertTrue(url.startsWith("/uploads/cars/" + entityId + "/"));
        assertTrue(url.endsWith("_car-photo.png"));

        Path storedFile = tempDir.resolve(url.substring("/uploads/".length()));
        assertTrue(Files.exists(storedFile));
        assertEquals(3, Files.size(storedFile));
    }
}
