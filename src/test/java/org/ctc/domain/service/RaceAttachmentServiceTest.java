package org.ctc.domain.service;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceAttachmentRepository;
import org.ctc.domain.repository.RaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RaceAttachmentServiceTest {

    @Mock private RaceRepository raceRepository;
    @Mock private RaceAttachmentRepository raceAttachmentRepository;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks
    private RaceAttachmentService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "uploadDir", "uploads");
    }

    // --- addLink ---

    @Test
    void givenValidUrl_whenAddLink_thenAttachmentSaved() {
        // given
        var raceId = UUID.randomUUID();
        var race = new Race();
        race.setId(raceId);

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));

        // when
        var name = service.addLink(raceId, "Replay", "https://youtube.com/watch?v=123");

        // then
        assertThat(name).isEqualTo("Replay");
        verify(raceAttachmentRepository).save(any(RaceAttachment.class));
    }

    @Test
    void givenInvalidUrl_whenAddLink_thenThrowsException() {
        // when / then
        assertThatThrownBy(() -> service.addLink(UUID.randomUUID(), "Bad", "ftp://invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http");
    }

    // --- deleteAttachment ---

    @Test
    void givenFileAttachment_whenDeleteAttachment_thenDeletesFileAndRecord() {
        // given
        var attachmentId = UUID.randomUUID();
        var raceId = UUID.randomUUID();
        var race = new Race();
        race.setId(raceId);

        var attachment = new RaceAttachment(race, AttachmentType.FILE, "screenshot.png", "/uploads/test.png");
        attachment.setId(attachmentId);

        when(raceAttachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));

        // when
        var result = service.deleteAttachment(attachmentId);

        // then
        assertThat(result).isEqualTo(raceId);
        verify(fileStorageService).delete("/uploads/test.png");
        verify(raceAttachmentRepository).delete(attachment);
    }

    @Test
    void givenLinkAttachment_whenDeleteAttachment_thenDoesNotDeleteFile() {
        // given
        var attachmentId = UUID.randomUUID();
        var raceId = UUID.randomUUID();
        var race = new Race();
        race.setId(raceId);

        var attachment = new RaceAttachment(race, AttachmentType.LINK, "Replay", "https://youtube.com");
        attachment.setId(attachmentId);

        when(raceAttachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));

        // when
        service.deleteAttachment(attachmentId);

        // then
        verify(fileStorageService, never()).delete(any());
        verify(raceAttachmentRepository).delete(attachment);
    }

    // --- uploadAttachment ---

    @Test
    void givenFile_whenUploadAttachment_thenStoresFileAndCreatesAttachment() throws Exception {
        // given
        var raceId = UUID.randomUUID();
        var race = new Race();
        race.setId(raceId);

        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("screenshot.png");

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(fileStorageService.store(eq(raceId), any())).thenReturn("/uploads/races/" + raceId + "/screenshot.png");

        // when
        var name = service.uploadAttachment(raceId, file);

        // then
        assertThat(name).isEqualTo("screenshot.png");
        verify(fileStorageService).store(eq(raceId), any());
        verify(raceAttachmentRepository).save(argThat(att ->
                att.getName().equals("screenshot.png") && att.getType() == AttachmentType.FILE));
    }

    // --- downloadAttachment ---

    @Test
    void givenLinkAttachment_whenDownloadAttachment_thenReturnsBadRequest() {
        // given
        var attachmentId = UUID.randomUUID();
        var race = new Race();
        race.setId(UUID.randomUUID());
        var attachment = new RaceAttachment(race, AttachmentType.LINK, "Replay", "https://youtube.com");
        attachment.setId(attachmentId);

        when(raceAttachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));

        // when
        var response = service.downloadAttachment(attachmentId);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
}
