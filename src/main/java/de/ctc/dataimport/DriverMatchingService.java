package de.ctc.dataimport;

import de.ctc.domain.model.Driver;
import de.ctc.domain.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverMatchingService {

    private static final double FUZZY_THRESHOLD = 0.8;
    private static final LevenshteinDistance LEVENSHTEIN = new LevenshteinDistance();

    private final DriverRepository driverRepository;

    public MatchResult findDriver(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return MatchResult.noMatch(searchTerm);
        }

        // Stage 1: Exact match on PSN ID
        var exact = driverRepository.findByPsnId(searchTerm);
        if (exact.isPresent()) {
            log.debug("Exact match for '{}': {}", searchTerm, exact.get().getPsnId());
            return MatchResult.exact(searchTerm, exact.get());
        }

        // Stage 2: Case-insensitive match on PSN ID
        var caseInsensitive = driverRepository.findByPsnIdIgnoreCase(searchTerm);
        if (caseInsensitive.isPresent()) {
            log.debug("Case-insensitive match for '{}': {}", searchTerm, caseInsensitive.get().getPsnId());
            return MatchResult.exact(searchTerm, caseInsensitive.get());
        }

        // Stage 3: Fuzzy match on PSN ID and Nickname
        var allDrivers = driverRepository.findAll();
        var bestFuzzy = findBestFuzzyMatch(searchTerm, allDrivers);
        if (bestFuzzy.isPresent()) {
            var match = bestFuzzy.get();
            log.debug("Fuzzy match for '{}': {} (similarity: {})",
                    searchTerm, match.driver().getPsnId(), match.similarity());
            return MatchResult.fuzzy(searchTerm, match.driver(), match.similarity());
        }

        // Stage 4: No match
        log.debug("No match found for '{}'", searchTerm);
        return MatchResult.noMatch(searchTerm);
    }

    private Optional<FuzzyMatch> findBestFuzzyMatch(String searchTerm, List<Driver> drivers) {
        return drivers.stream()
                .map(driver -> {
                    double psnSimilarity = calculateSimilarity(searchTerm, driver.getPsnId());
                    double nickSimilarity = calculateSimilarity(searchTerm, driver.getNickname());
                    double bestSimilarity = Math.max(psnSimilarity, nickSimilarity);
                    return new FuzzyMatch(driver, bestSimilarity);
                })
                .filter(m -> m.similarity() >= FUZZY_THRESHOLD)
                .max(Comparator.comparingDouble(FuzzyMatch::similarity));
    }

    double calculateSimilarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        String lowerA = a.toLowerCase();
        String lowerB = b.toLowerCase();
        int maxLen = Math.max(lowerA.length(), lowerB.length());
        if (maxLen == 0) return 1.0;
        int distance = LEVENSHTEIN.apply(lowerA, lowerB);
        return 1.0 - ((double) distance / maxLen);
    }

    public record FuzzyMatch(Driver driver, double similarity) {}

    public record MatchResult(String searchTerm, Driver driver, MatchType type, double similarity) {

        public static MatchResult exact(String searchTerm, Driver driver) {
            return new MatchResult(searchTerm, driver, MatchType.EXACT, 1.0);
        }

        public static MatchResult fuzzy(String searchTerm, Driver driver, double similarity) {
            return new MatchResult(searchTerm, driver, MatchType.FUZZY, similarity);
        }

        public static MatchResult noMatch(String searchTerm) {
            return new MatchResult(searchTerm, null, MatchType.NONE, 0.0);
        }

        public boolean isMatch() {
            return type != MatchType.NONE;
        }

        public boolean needsConfirmation() {
            return type == MatchType.FUZZY;
        }
    }

    public enum MatchType {
        EXACT, FUZZY, NONE
    }
}
