package org.ctc.domain.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.MusicTrack;
import org.ctc.domain.repository.MusicTrackRepository;
import org.ctc.media.MediaTool;
import org.ctc.media.MediaToolException;
import org.ctc.util.LogSanitizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class MusicLibraryService {

    public static final int MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
    private static final String FOLDER = "music";
    private static final String STORED_NAME = "track.mp3";

    private final MusicTrackRepository repository;
    private final FileStorageService files;
    private final MediaTool media;

    @Transactional(readOnly = true)
    public List<MusicTrack> list() {
        return repository.findAllByOrderByTitleAscIdAsc();
    }

    @Transactional
    public MusicTrack upload(MultipartFile file, String title, String artist, String credit) {
        byte[] data = readMp3(file);
        UUID id = UUID.randomUUID();
        String path;
        try {
            path = files.storeBytes(FOLDER, id, STORED_NAME, data);
        } catch (IOException e) {
            throw new BusinessRuleException("The MP3 file could not be stored.");
        }
        files.deleteOnRollback(path);
        long millis;
        try {
            millis = media.probeDuration(files.resolve(path)).toMillis();
        } catch (MediaToolException e) {
            log.warn("Rejected music upload {}: {}", LogSanitizer.sanitize(file.getOriginalFilename()), e.getMessage());
            throw new BusinessRuleException("The MP3 file could not be read.");
        }
        var track = new MusicTrack();
        track.setTitle(title.trim());
        track.setArtist(artist.trim());
        track.setCredit(credit == null || credit.isBlank() ? null : credit.trim());
        track.setFilePath(path);
        track.setDurationSeconds((int) Math.max(1, Math.round(millis / 1000.0)));
        track = repository.saveAndFlush(track);
        log.info("Added music track {} by {}", LogSanitizer.sanitize(track.getTitle()), LogSanitizer.sanitize(track.getArtist()));
        return track;
    }

    @Transactional
    public void delete(UUID id) {
        var track = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("MusicTrack", id));
        repository.delete(track);
        files.deleteAfterCommit(track.getFilePath());
        log.info("Deleted music track {}", LogSanitizer.sanitize(track.getTitle()));
    }

    @Transactional(readOnly = true)
    public Optional<MusicTrack> pick(UUID id, RandomGenerator random) {
        if (id != null) {
            return repository.findById(id);
        }
        var all = repository.findAllByOrderByTitleAscIdAsc();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(random.nextInt(all.size())));
    }

    public Path resolveFile(MusicTrack track) {
        return files.resolve(track.getFilePath());
    }

    private static byte[] readMp3(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Choose an MP3 file.");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessRuleException("MP3 files may be at most 20 MB.");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new BusinessRuleException("The MP3 file could not be read.");
        }
        if (data.length > MAX_UPLOAD_BYTES) {
            throw new BusinessRuleException("MP3 files may be at most 20 MB.");
        }
        boolean id3 = data.length >= 3 && data[0] == 'I' && data[1] == 'D' && data[2] == '3';
        boolean frameSync = data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xE0) == 0xE0 && (data[1] & 0x06) != 0;
        if (!id3 && !frameSync) {
            throw new BusinessRuleException("Only MP3 files are accepted.");
        }
        return data;
    }
}
