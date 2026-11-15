package org.ctc.admin.service;

import static org.ctc.admin.dto.DataAuditFinding.Category.CATALOG_IMAGES;
import static org.ctc.admin.dto.DataAuditFinding.Resolution.UNDETERMINABLE;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.DataAuditFinding;
import org.ctc.domain.repository.CarImageRepository;
import org.ctc.domain.repository.TrackImageRepository;
import org.ctc.domain.service.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Reports gallery images whose stored file is missing, without deleting the rows or inventing a file. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogImageAuditService {
    private final CarImageRepository carImages;
    private final TrackImageRepository trackImages;
    private final FileStorageService files;

    public List<DataAuditFinding> inspect() {
        var findings = new ArrayList<DataAuditFinding>();
        for (var image : carImages.findAllForBackup()) {
            if (!files.exists(image.getImagePath())) findings.add(missing("Car " + image.getCar().getName(), image.getId(), image.getImagePath()));
        }
        for (var image : trackImages.findAllForBackup()) {
            if (!files.exists(image.getImagePath())) findings.add(missing("Track " + image.getTrack().getName(), image.getId(), image.getImagePath()));
        }
        return findings;
    }

    private static DataAuditFinding missing(String owner, UUID id, String path) {
        return new DataAuditFinding(CATALOG_IMAGES, UNDETERMINABLE, owner, "Image " + id + " points to " + path + ", which is not stored",
                "Upload the picture again and delete the empty entry in the car or track editor");
    }
}
