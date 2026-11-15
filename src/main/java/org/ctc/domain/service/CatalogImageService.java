package org.ctc.domain.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.CarImage;
import org.ctc.domain.model.CarImageKind;
import org.ctc.domain.model.GalleryImage;
import org.ctc.domain.model.ImageSource;
import org.ctc.domain.model.Track;
import org.ctc.domain.model.TrackImage;
import org.ctc.domain.model.TrackImageKind;
import org.ctc.domain.repository.CarImageRepository;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.TrackImageRepository;
import org.ctc.domain.repository.TrackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Car and track image galleries. Uploads accept PNG, JPEG and WebP up to 10 MB each and validate every file before
 * storing any; removed files are deleted after commit and new files on rollback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CatalogImageService {
    public static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    public static final int MAX_LABEL_LENGTH = 100;
    public static final String GT_ENGINE_KEY = "map";
    public static final String GT_ENGINE_LABEL = "Layout map by GT-Engine (gt-engine.com)";
    private final CarRepository cars;
    private final TrackRepository tracks;
    private final CarImageRepository carImages;
    private final TrackImageRepository trackImages;
    private final FileStorageService files;

    @Transactional(readOnly = true)
    public List<CarImage> carImages(UUID carId) { return carImages.findByCarIdOrderBySortIndexAscIdAsc(carId); }

    @Transactional(readOnly = true)
    public List<TrackImage> trackImages(UUID trackId) { return trackImages.findByTrackIdOrderBySortIndexAscIdAsc(trackId); }

    /** The first image of a kind by sort order, for graphics that need one picture of a car. */
    @Transactional(readOnly = true)
    public Optional<CarImage> firstCarImage(UUID carId, CarImageKind kind) {
        return carImages.findFirstByCarIdAndKindOrderBySortIndexAscIdAsc(carId, kind);
    }

    @Transactional(readOnly = true)
    public Optional<TrackImage> firstTrackImage(UUID trackId, TrackImageKind kind) {
        return trackImages.findFirstByTrackIdAndKindOrderBySortIndexAscIdAsc(trackId, kind);
    }

    /** Stores every file as a manual image of the kind and returns the number added. */
    public int uploadCarImages(UUID carId, List<MultipartFile> uploads, CarImageKind kind, String label) {
        var car = lockCar(carId);
        if (kind == null) throw new BusinessRuleException("Choose an image kind");
        var checked = check(uploads, label);
        int next = next(carImages(carId));
        for (var upload : checked) {
            var image = new CarImage();
            image.setCar(car);
            image.setKind(kind);
            fill(image, upload, store("car-images", carId, upload), next++);
            carImages.save(image);
        }
        log.info("Added {} images to car {}", checked.size(), carId);
        return checked.size();
    }

    public int uploadTrackImages(UUID trackId, List<MultipartFile> uploads, TrackImageKind kind, String label) {
        var track = lockTrack(trackId);
        if (kind == null) throw new BusinessRuleException("Choose an image kind");
        var checked = check(uploads, label);
        int next = next(trackImages(trackId));
        for (var upload : checked) {
            var image = new TrackImage();
            image.setTrack(track);
            image.setKind(kind);
            fill(image, upload, store("track-images", trackId, upload), next++);
            trackImages.save(image);
        }
        log.info("Added {} images to track {}", checked.size(), trackId);
        return checked.size();
    }

    /**
     * Applies downloaded GT7 car shots: a slot that already has a GT7 image gets the new file, a new slot is appended
     * and manual images stay untouched. Replaced files are deleted after commit.
     */
    public Gt7Applied applyGt7CarImages(UUID carId, List<Gt7Shot> shots) {
        shots.forEach(shot -> files.deleteOnRollback(shot.imagePath()));
        var car = lockCar(carId);
        var existing = carImages(carId);
        int next = next(existing);
        var added = new ArrayList<String>();
        var replaced = new ArrayList<String>();
        for (var shot : shots) {
            var current = existing.stream().filter(image -> image.getSource() == ImageSource.GT7 && shot.slot().equals(image.getSourceKey())).findFirst();
            var image = current.orElseGet(CarImage::new);
            if (current.isPresent()) {
                files.deleteAfterCommit(image.getImagePath());
                replaced.add(shot.slot());
            } else {
                image.setCar(car);
                image.setSource(ImageSource.GT7);
                image.setSourceKey(shot.slot());
                image.setSortIndex(next++);
                added.add(shot.slot());
            }
            image.setKind(CarImageKind.forGt7Slot(shot.slot()));
            image.setImagePath(shot.imagePath());
            image.setWidth(shot.width());
            image.setHeight(shot.height());
            carImages.save(image);
        }
        log.info("Applied GT7 images to car {}: added {}, replaced {}", carId, added, replaced);
        return new Gt7Applied(List.copyOf(added), List.copyOf(replaced));
    }

    /**
     * Stores the track's GT-Engine layout map: an existing GT-Engine map gets the new file, otherwise the map is
     * appended. Other images stay untouched; the replaced file is deleted after commit.
     *
     * @return true when an earlier GT-Engine map was replaced
     */
    public boolean applyGtEngineMap(UUID trackId, String imagePath, int width, int height) {
        files.deleteOnRollback(imagePath);
        var track = lockTrack(trackId);
        var existing = trackImages(trackId);
        var current = existing.stream().filter(image -> image.getSource() == ImageSource.GTENGINE).findFirst();
        var image = current.orElseGet(TrackImage::new);
        if (current.isPresent()) {
            files.deleteAfterCommit(image.getImagePath());
        } else {
            image.setTrack(track);
            image.setKind(TrackImageKind.LAYOUT);
            image.setSource(ImageSource.GTENGINE);
            image.setSourceKey(GT_ENGINE_KEY);
            image.setSortIndex(next(existing));
        }
        image.setLabel(GT_ENGINE_LABEL);
        image.setImagePath(imagePath);
        image.setWidth(width);
        image.setHeight(height);
        trackImages.save(image);
        log.info("{} GT-Engine map of track {}", current.isPresent() ? "Replaced" : "Added", trackId);
        return current.isPresent();
    }

    /** Stored path of the track's GT-Engine map, if it has one. */
    @Transactional(readOnly = true)
    public Optional<String> gtEngineMapPath(UUID trackId) {
        return trackImages(trackId).stream().filter(image -> image.getSource() == ImageSource.GTENGINE).map(TrackImage::getImagePath).findFirst();
    }

    /** Slot and stored path of the car's GT7 images, without loading entities. */
    @Transactional(readOnly = true)
    public Map<String, String> gt7Slots(UUID carId) {
        var slots = new HashMap<String, String>();
        carImages.findGt7Slots(carId).forEach(row -> slots.put(row.getSlot(), row.getImagePath()));
        return slots;
    }

    public void deleteCarImage(UUID carId, UUID imageId) {
        var image = carImages.findById(imageId).filter(row -> row.getCar().getId().equals(carId))
                .orElseThrow(() -> new EntityNotFoundException("Car image", imageId));
        carImages.delete(image);
        files.deleteAfterCommit(image.getImagePath());
    }

    public void deleteTrackImage(UUID trackId, UUID imageId) {
        var image = trackImages.findById(imageId).filter(row -> row.getTrack().getId().equals(trackId))
                .orElseThrow(() -> new EntityNotFoundException("Track image", imageId));
        trackImages.delete(image);
        files.deleteAfterCommit(image.getImagePath());
    }

    /** Moves the image one place and returns its new 1-based position. */
    public int moveCarImage(UUID carId, UUID imageId, boolean up) {
        lockCar(carId);
        return move(carImages(carId), imageId, up, "Car image");
    }

    public int moveTrackImage(UUID trackId, UUID imageId, boolean up) {
        lockTrack(trackId);
        return move(trackImages(trackId), imageId, up, "Track image");
    }

    /** Removes every gallery image of the car, with its files after commit. */
    public void deleteAllForCar(UUID carId) {
        var rows = carImages(carId);
        rows.forEach(image -> files.deleteAfterCommit(image.getImagePath()));
        carImages.deleteAll(rows);
    }

    public void deleteAllForTrack(UUID trackId) {
        var rows = trackImages(trackId);
        rows.forEach(image -> files.deleteAfterCommit(image.getImagePath()));
        trackImages.deleteAll(rows);
    }

    private static int move(List<? extends GalleryImage> ordered, UUID imageId, boolean up, String kind) {
        int index = -1;
        for (int position = 0; position < ordered.size(); position++) if (ordered.get(position).getId().equals(imageId)) index = position;
        if (index < 0) throw new EntityNotFoundException(kind, imageId);
        int target = up ? index - 1 : index + 1;
        if (target < 0 || target >= ordered.size()) return index + 1;
        for (int position = 0; position < ordered.size(); position++) ordered.get(position).setSortIndex(position);
        ordered.get(index).setSortIndex(target);
        ordered.get(target).setSortIndex(index);
        return target + 1;
    }

    private static int next(List<? extends GalleryImage> existing) {
        return existing.stream().mapToInt(GalleryImage::getSortIndex).max().orElse(-1) + 1;
    }

    private List<Checked> check(List<MultipartFile> uploads, String label) {
        var present = uploads == null ? List.<MultipartFile>of() : uploads.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (present.isEmpty()) throw new BusinessRuleException("Choose at least one image file");
        String cleanLabel = label == null || label.isBlank() ? null : label.strip();
        if (cleanLabel != null && cleanLabel.length() > MAX_LABEL_LENGTH) {
            throw new BusinessRuleException("The label can have at most " + MAX_LABEL_LENGTH + " characters");
        }
        var checked = new ArrayList<Checked>();
        for (var file : present) {
            if (file.getSize() > MAX_UPLOAD_BYTES) throw new BusinessRuleException(name(file) + " is larger than 10 MB");
            byte[] data;
            try {
                data = file.getBytes();
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            var format = ImageContent.detect(data);
            if (format == null) throw new BusinessRuleException(name(file) + " is not a PNG, JPEG or WebP image");
            if (!format.equals(declared(file))) {
                throw new BusinessRuleException(name(file) + " is a " + format + " image; its file extension must be " + format.extension());
            }
            ImageContent.Dimensions size;
            try {
                size = ImageContent.dimensions(data);
            } catch (IllegalArgumentException unreadable) {
                throw new BusinessRuleException(name(file) + ": " + unreadable.getMessage());
            }
            checked.add(new Checked(file, cleanLabel, size, data, format));
        }
        return checked;
    }

    private static ImageContent.Format declared(MultipartFile file) {
        try {
            return ImageContent.Format.ofFilename(file.getOriginalFilename());
        } catch (IllegalArgumentException unsupported) {
            return null;
        }
    }

    /** Writes the checked bytes; uploads pass only the content checks above, never the browser's content type. */
    private String store(String folder, UUID ownerId, Checked upload) {
        try {
            String path = files.storeBytes(folder, ownerId, "image" + upload.format().extension(), upload.data());
            files.deleteOnRollback(path);
            return path;
        } catch (IOException failure) {
            log.warn("Could not store gallery image for {}", ownerId, failure);
            throw new BusinessRuleException(name(upload.file()) + " could not be stored; no image was added");
        }
    }

    private Car lockCar(UUID carId) {
        return cars.findByIdForUpdate(carId).orElseThrow(() -> new EntityNotFoundException("Car", carId));
    }

    private Track lockTrack(UUID trackId) {
        return tracks.findByIdForEnvironmentUpdate(trackId).orElseThrow(() -> new EntityNotFoundException("Track", trackId));
    }

    private static void fill(GalleryImage image, Checked upload, String storedPath, int position) {
        image.setSource(ImageSource.MANUAL);
        image.setLabel(upload.label());
        image.setImagePath(storedPath);
        image.setWidth(upload.size().width());
        image.setHeight(upload.size().height());
        image.setSortIndex(position);
    }

    private static String name(MultipartFile file) {
        String original = file.getOriginalFilename();
        return original == null || original.isBlank() ? "The file" : original.replaceAll("\\p{Cntrl}", "");
    }


    /** A downloaded GT7 car shot already stored under {@code imagePath}. */
    public record Gt7Shot(String slot, String imagePath, int width, int height) { }

    public record Gt7Applied(List<String> added, List<String> replaced) { }

    private record Checked(MultipartFile file, String label, ImageContent.Dimensions size, byte[] data, ImageContent.Format format) { }
}
