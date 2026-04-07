package org.ctc.domain.service;

import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TrackRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackServiceTest {

    @Mock
    private TrackRepository trackRepository;

    @Mock
    private RaceRepository raceRepository;

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private TrackService trackService;

    @Nested
    class FindAllSortedTest {

        @Test
        void whenFindAllSorted_thenDelegatesToRepository() {
            // given
            var tracks = List.of(new Track("Fuji", "Japan"), new Track("Spa", "Belgium"));
            when(trackRepository.findAllByOrderByNameAsc()).thenReturn(tracks);

            // when
            var result = trackService.findAllSorted();

            // then
            assertThat(result).hasSize(2);
            verify(trackRepository).findAllByOrderByNameAsc();
        }
    }

    @Nested
    class FindByIdTest {

        @Test
        void givenExistingId_whenFindById_thenReturnsTrack() {
            // given
            var id = UUID.randomUUID();
            var track = new Track("Fuji", "Japan");
            track.setId(id);
            when(trackRepository.findById(id)).thenReturn(Optional.of(track));

            // when
            var result = trackService.findById(id);

            // then
            assertThat(result.getName()).isEqualTo("Fuji");
        }

        @Test
        void givenNonExistentId_whenFindById_thenThrowsEntityNotFoundException() {
            // given
            var id = UUID.randomUUID();
            when(trackRepository.findById(id)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> trackService.findById(id))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Track");
        }
    }

    @Nested
    class SaveTest {

        @Test
        void givenNewTrack_whenSave_thenCreatesTrack() {
            // given
            when(trackRepository.saveAndFlush(any(Track.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            var result = trackService.save(null, "Fuji Speedway", "Japan");

            // then
            assertThat(result.getName()).isEqualTo("Fuji Speedway");
            assertThat(result.getCountry()).isEqualTo("Japan");
        }

        @Test
        void givenExistingTrack_whenSave_thenUpdatesTrack() {
            // given
            var id = UUID.randomUUID();
            var existing = new Track("Old Name", "Old Country");
            existing.setId(id);

            when(trackRepository.findById(id)).thenReturn(Optional.of(existing));
            when(trackRepository.saveAndFlush(any(Track.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            var result = trackService.save(id, "New Name", "New Country");

            // then
            assertThat(result.getName()).isEqualTo("New Name");
            assertThat(result.getCountry()).isEqualTo("New Country");
        }

        @Test
        void givenDuplicateName_whenSave_thenThrowsBusinessRuleException() {
            // given
            when(trackRepository.saveAndFlush(any(Track.class)))
                    .thenThrow(new DataIntegrityViolationException("unique constraint"));

            // when / then
            assertThatThrownBy(() -> trackService.save(null, "Duplicate", "Japan"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already exists");
        }
    }

    @Nested
    class DeleteTest {

        @Test
        void givenTrackNotUsed_whenDelete_thenRemoves() {
            // given
            var id = UUID.randomUUID();
            var track = new Track("Fuji", "Japan");
            track.setId(id);
            when(trackRepository.findById(id)).thenReturn(Optional.of(track));
            when(raceRepository.existsByTrackId(id)).thenReturn(false);
            when(seasonRepository.findAll()).thenReturn(List.of());

            // when
            trackService.delete(id);

            // then
            verify(trackRepository).delete(track);
        }

        @Test
        void givenTrackUsedInRace_whenDelete_thenThrowsBusinessRuleException() {
            // given
            var id = UUID.randomUUID();
            var track = new Track("Fuji", "Japan");
            track.setId(id);
            when(trackRepository.findById(id)).thenReturn(Optional.of(track));
            when(raceRepository.existsByTrackId(id)).thenReturn(true);

            // when / then
            assertThatThrownBy(() -> trackService.delete(id))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("race");
        }

        @Test
        void givenTrackUsedInSeasonPool_whenDelete_thenThrowsBusinessRuleException() {
            // given
            var id = UUID.randomUUID();
            var track = new Track("Fuji", "Japan");
            track.setId(id);
            when(trackRepository.findById(id)).thenReturn(Optional.of(track));
            when(raceRepository.existsByTrackId(id)).thenReturn(false);

            var season = mock(Season.class);
            when(season.getTracks()).thenReturn(List.of(track));
            when(seasonRepository.findAll()).thenReturn(List.of(season));

            // when / then
            assertThatThrownBy(() -> trackService.delete(id))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("season pool");
        }

        @Test
        void givenTrackWithImage_whenDelete_thenDeletesImageFirst() {
            // given
            var id = UUID.randomUUID();
            var track = new Track("Fuji", "Japan");
            track.setId(id);
            track.setImageUrl("/uploads/tracks/image.png");
            when(trackRepository.findById(id)).thenReturn(Optional.of(track));
            when(raceRepository.existsByTrackId(id)).thenReturn(false);
            when(seasonRepository.findAll()).thenReturn(List.of());

            // when
            trackService.delete(id);

            // then
            verify(fileStorageService).delete("/uploads/tracks/image.png");
            verify(trackRepository).delete(track);
        }
    }

    @Nested
    class UploadImageTest {

        @Test
        void givenValidImage_whenUploadImage_thenStoresAndUpdatesEntity() throws IOException {
            // given
            var id = UUID.randomUUID();
            var track = new Track("Fuji", "Japan");
            track.setId(id);
            var image = mock(MultipartFile.class);

            when(trackRepository.findById(id)).thenReturn(Optional.of(track));
            when(fileStorageService.storeImage("tracks", id, image)).thenReturn("/uploads/tracks/new.png");
            when(trackRepository.save(any(Track.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            trackService.uploadImage(id, image);

            // then
            assertThat(track.getImageUrl()).isEqualTo("/uploads/tracks/new.png");
            verify(trackRepository).save(track);
        }

        @Test
        void givenExistingImage_whenUploadImage_thenDeletesOldFirst() throws IOException {
            // given
            var id = UUID.randomUUID();
            var track = new Track("Fuji", "Japan");
            track.setId(id);
            track.setImageUrl("/uploads/tracks/old.png");
            var image = mock(MultipartFile.class);

            when(trackRepository.findById(id)).thenReturn(Optional.of(track));
            when(fileStorageService.storeImage("tracks", id, image)).thenReturn("/uploads/tracks/new.png");
            when(trackRepository.save(any(Track.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            trackService.uploadImage(id, image);

            // then
            verify(fileStorageService).delete("/uploads/tracks/old.png");
            assertThat(track.getImageUrl()).isEqualTo("/uploads/tracks/new.png");
        }

        @Test
        void givenUploadFailure_whenUploadImage_thenThrowsBusinessRuleException() throws IOException {
            // given
            var id = UUID.randomUUID();
            var track = new Track("Fuji", "Japan");
            track.setId(id);
            var image = mock(MultipartFile.class);

            when(trackRepository.findById(id)).thenReturn(Optional.of(track));
            when(fileStorageService.storeImage("tracks", id, image))
                    .thenThrow(new IOException("Disk full"));

            // when / then
            assertThatThrownBy(() -> trackService.uploadImage(id, image))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Image upload failed");
        }

        @Test
        void givenRuntimeException_whenUploadImage_thenPropagates() throws IOException {
            // given
            var id = UUID.randomUUID();
            var track = new Track("Fuji", "Japan");
            track.setId(id);
            var image = mock(MultipartFile.class);

            when(trackRepository.findById(id)).thenReturn(Optional.of(track));
            when(fileStorageService.storeImage("tracks", id, image))
                    .thenThrow(new RuntimeException("unexpected error"));

            // when / then
            assertThatThrownBy(() -> trackService.uploadImage(id, image))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(BusinessRuleException.class);
        }
    }
}
