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
import java.util.HashMap;
import java.util.UUID;
import org.ctc.domain.model.ImageSource;
import org.ctc.domain.model.TrackImageKind;
import org.ctc.domain.service.CatalogImageService;

/**
 * Reports gallery images whose stored file is missing and GT-Engine maps that break the one-layout-map-per-track rule,
 * without deleting rows or inventing a file.
 */
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
        var mapsPerTrack = new HashMap<UUID, Integer>();
        for (var image : trackImages.findAllForBackup()) {
            String owner = "Track " + image.getTrack().getName();
            if (!files.exists(image.getImagePath())) findings.add(missing(owner, image.getId(), image.getImagePath()));
            if (image.getSource() != ImageSource.GTENGINE) continue;
            if (!CatalogImageService.GT_ENGINE_KEY.equals(image.getSourceKey()) || image.getKind() != TrackImageKind.LAYOUT) {
                findings.add(new DataAuditFinding(CATALOG_IMAGES, UNDETERMINABLE, owner,
                        "GT-Engine image " + image.getId() + " has source key " + image.getSourceKey() + " and kind " + image.getKind()
                                + " instead of " + CatalogImageService.GT_ENGINE_KEY + " and LAYOUT",
                        "Delete the image in the track editor and load the GT-Engine map again"));
            }
            if (mapsPerTrack.merge(image.getTrack().getId(), 1, Integer::sum) == 2) {
                findings.add(new DataAuditFinding(CATALOG_IMAGES, UNDETERMINABLE, owner, "The track has more than one GT-Engine map",
                        "Delete the extra GT-Engine maps in the track editor"));
            }
        }
        return findings;
    }

    private static DataAuditFinding missing(String owner, UUID id, String path) {
        return new DataAuditFinding(CATALOG_IMAGES, UNDETERMINABLE, owner, "Image " + id + " points to " + path + ", which is not stored",
                "Upload the picture again and delete the empty entry in the car or track editor");
    }
}
