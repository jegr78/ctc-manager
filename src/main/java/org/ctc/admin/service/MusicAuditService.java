package org.ctc.admin.service;

import static org.ctc.admin.dto.DataAuditFinding.Category.MUSIC_LIBRARY;
import static org.ctc.admin.dto.DataAuditFinding.Resolution.UNDETERMINABLE;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.DataAuditFinding;
import org.ctc.domain.repository.MusicTrackRepository;
import org.ctc.domain.service.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reports music tracks whose stored file is missing, without deleting rows. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MusicAuditService {
    private final MusicTrackRepository musicTracks;
    private final FileStorageService files;

    public List<DataAuditFinding> inspect() {
        var findings = new ArrayList<DataAuditFinding>();
        for (var track : musicTracks.findAllForBackup()) {
            if (!files.exists(track.getFilePath())) {
                findings.add(new DataAuditFinding(MUSIC_LIBRARY, UNDETERMINABLE, "Music " + track.getTitle(),
                        "Music track " + track.getId() + " points to " + track.getFilePath() + ", which is not stored",
                        "Delete the entry on the Music page and upload the MP3 again"));
            }
        }
        return findings;
    }
}
