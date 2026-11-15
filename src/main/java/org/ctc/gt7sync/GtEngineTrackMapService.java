package org.ctc.gt7sync;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.service.CatalogImageService;
import org.ctc.domain.service.FileStorageService;
import org.ctc.domain.service.ImageContent;
import org.ctc.integration.ExternalIntegrationDisabledException;
import org.ctc.integration.OutboundPolicy;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Loads GT-Engine layout maps into track galleries. Downloads run outside any transaction and use only reviewed slugs;
 * a new file is removed when a step before applying fails, and by the apply transaction on rollback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GtEngineTrackMapService {
    private static final int MAX_PAGE_BYTES = 2 * 1024 * 1024;
    private final TrackRepository tracks;
    private final FileStorageService files;
    private final CatalogImageService gallery;
    private final OutboundPolicy outbound;
    private final PageFetcher pages;

    public GtEngineTrackMaps reference() { return GtEngineTrackMaps.bundled(); }

    public boolean hasMap(String trackName) { return reference().layoutFor(trackName).isPresent(); }

    public Outcome load(UUID trackId) {
        var track = tracks.findById(trackId).orElseThrow(() -> new EntityNotFoundException("Track", trackId));
        return load(track);
    }

    /** Loads the map of every mapped catalog track and reports the outcome per kind. */
    public BulkResult loadAll() {
        if (!outbound.isEnabled()) throw new BusinessRuleException(ExternalIntegrationDisabledException.MESSAGE);
        var counts = new int[Outcome.values().length];
        int unmapped = 0;
        var failed = new ArrayList<String>();
        for (var track : tracks.findAll().stream().sorted(Comparator.comparing(Track::getName)).toList()) {
            if (!hasMap(track.getName())) {
                unmapped++;
                continue;
            }
            try {
                counts[load(track).ordinal()]++;
            } catch (BusinessRuleException rejected) {
                failed.add(track.getName());
            }
        }
        return new BulkResult(counts[Outcome.ADDED.ordinal()], counts[Outcome.REPLACED.ordinal()], counts[Outcome.UNCHANGED.ordinal()], unmapped,
                List.copyOf(failed));
    }

    /** Compares the live GT-Engine page with the reviewed list without changing data. */
    public GtEngineTrackMaps.Comparison compare() {
        try {
            outbound.requireEnabled();
            return reference().compare(GtEngineTrackMaps.parsePage(pages.fetch(GtEngineTrackMaps.PAGE_URL, MAX_PAGE_BYTES)));
        } catch (IOException | UncheckedIOException | IllegalArgumentException failure) {
            log.warn("Comparing the GT-Engine layout list failed", failure);
            throw new BusinessRuleException("GT-Engine could not be compared: " + failure.getMessage());
        }
    }

    private Outcome load(Track track) {
        var layout = reference().layoutFor(track.getName())
                .orElseThrow(() -> new BusinessRuleException("GT-Engine has no map for this track"));
        String stored;
        int width;
        int height;
        try {
            byte[] data = files.downloadTrackMap(reference().imageUrl(layout));
            String current = gallery.gtEngineMapPath(track.getId()).orElse(null);
            if (current != null && files.hasContent(current, data)) return Outcome.UNCHANGED;
            var size = ImageContent.dimensions(data);
            width = size.width();
            height = size.height();
            stored = files.storeBytes("track-images", track.getId(), "gtengine_" + layout.slug() + ".png", data);
        } catch (IOException | RuntimeException failure) {
            log.warn("Loading the GT-Engine map of track {} failed", track.getId(), failure);
            throw new BusinessRuleException("The GT-Engine map could not be loaded: " + switch (failure) {
                case IOException io -> io.getMessage();
                case IllegalArgumentException rejected -> rejected.getMessage();
                default -> "the file is not a readable map";
            });
        }
        try {
            return gallery.applyGtEngineMap(track.getId(), stored, width, height) ? Outcome.REPLACED : Outcome.ADDED;
        } catch (CannotCreateTransactionException notStarted) {
            files.delete(stored);
            log.warn("Saving the GT-Engine map of track {} could not start", track.getId(), notStarted);
            throw new BusinessRuleException("The GT-Engine map could not be saved; try again.");
        } catch (RuntimeException failure) {
            log.warn("Saving the GT-Engine map of track {} failed", track.getId(), failure);
            throw new BusinessRuleException("The GT-Engine map could not be saved; try again.");
        }
    }

    public enum Outcome {
        ADDED("GT-Engine map added."), REPLACED("GT-Engine map replaced."), UNCHANGED("The GT-Engine map is unchanged.");

        private final String message;

        Outcome(String message) { this.message = message; }

        public String message() { return message; }
    }

    public record BulkResult(int added, int replaced, int unchanged, int unmapped, List<String> failed) {
        private static final int LISTED_FAILURES = 10;

        public boolean failures() { return !failed.isEmpty(); }

        public String message() {
            String text = "GT-Engine maps: added " + added + ", replaced " + replaced + ", unchanged " + unchanged + ", tracks without a map " + unmapped;
            if (failed.isEmpty()) return text + ".";
            String listed = String.join(", ", failed.subList(0, Math.min(LISTED_FAILURES, failed.size())));
            String more = failed.size() > LISTED_FAILURES ? " and " + (failed.size() - LISTED_FAILURES) + " more" : "";
            return text + "; failed " + failed.size() + ": " + listed + more + ". Load a single track for its reason.";
        }
    }

    /** Fetches a page body; a separate seam so tests never reach the network. */
    @FunctionalInterface
    public interface PageFetcher {
        String fetch(String url, int maxBytes) throws IOException;
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class PageFetcherConfiguration {
        @org.springframework.context.annotation.Bean
        PageFetcher gtEnginePageFetcher() {
            return (url, maxBytes) -> {
                var response = Jsoup.connect(url).timeout(10_000).maxBodySize(maxBytes).followRedirects(false).execute();
                if (response.statusCode() != 200) throw new IOException("GT-Engine answered with HTTP " + response.statusCode());
                String body = response.body();
                if (body.length() >= maxBytes) throw new IOException("GT-Engine page exceeds its size limit");
                return body;
            };
        }
    }
}
