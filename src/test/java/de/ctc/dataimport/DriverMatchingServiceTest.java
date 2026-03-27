package de.ctc.dataimport;

import de.ctc.domain.model.Driver;
import de.ctc.domain.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverMatchingServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @InjectMocks
    private DriverMatchingService matchingService;

    private Driver hills;
    private Driver micky;

    @BeforeEach
    void setUp() {
        hills = new Driver("AHR_Hills_93", "AHR_Hills_93");
        hills.setId(UUID.randomUUID());

        micky = new Driver("miggldeehiggins", "Micky D Higgins");
        micky.setId(UUID.randomUUID());
    }

    @Nested
    class ExactMatchTest {

        @Test
        void shouldFindExactMatch() {
            when(driverRepository.findByPsnId("AHR_Hills_93")).thenReturn(Optional.of(hills));

            var result = matchingService.findDriver("AHR_Hills_93");

            assertEquals(DriverMatchingService.MatchType.EXACT, result.type());
            assertEquals(hills, result.driver());
            assertFalse(result.needsConfirmation());
        }

        @Test
        void shouldFindCaseInsensitiveMatch() {
            when(driverRepository.findByPsnId("ahr_hills_93")).thenReturn(Optional.empty());
            when(driverRepository.findByPsnIdIgnoreCase("ahr_hills_93")).thenReturn(Optional.of(hills));

            var result = matchingService.findDriver("ahr_hills_93");

            assertEquals(DriverMatchingService.MatchType.EXACT, result.type());
            assertEquals(hills, result.driver());
        }
    }

    @Nested
    class FuzzyMatchTest {

        @Test
        void shouldFindFuzzyMatchOnPsnId() {
            when(driverRepository.findByPsnId("AHR_Hils_93")).thenReturn(Optional.empty());
            when(driverRepository.findByPsnIdIgnoreCase("AHR_Hils_93")).thenReturn(Optional.empty());
            when(driverRepository.findAll()).thenReturn(List.of(hills, micky));

            var result = matchingService.findDriver("AHR_Hils_93");

            assertEquals(DriverMatchingService.MatchType.FUZZY, result.type());
            assertEquals(hills, result.driver());
            assertTrue(result.needsConfirmation());
            assertTrue(result.similarity() >= 0.8);
        }

        @Test
        void shouldFindFuzzyMatchOnNickname() {
            when(driverRepository.findByPsnId("Micky D Higins")).thenReturn(Optional.empty());
            when(driverRepository.findByPsnIdIgnoreCase("Micky D Higins")).thenReturn(Optional.empty());
            when(driverRepository.findAll()).thenReturn(List.of(hills, micky));

            var result = matchingService.findDriver("Micky D Higins");

            assertEquals(DriverMatchingService.MatchType.FUZZY, result.type());
            assertEquals(micky, result.driver());
            assertTrue(result.similarity() >= 0.8);
        }

        @Test
        void shouldNotMatchBelowThreshold() {
            when(driverRepository.findByPsnId("completely_different")).thenReturn(Optional.empty());
            when(driverRepository.findByPsnIdIgnoreCase("completely_different")).thenReturn(Optional.empty());
            when(driverRepository.findAll()).thenReturn(List.of(hills, micky));

            var result = matchingService.findDriver("completely_different");

            assertEquals(DriverMatchingService.MatchType.NONE, result.type());
            assertNull(result.driver());
        }
    }

    @Nested
    class NoMatchTest {

        @Test
        void shouldReturnNoMatchForUnknownDriver() {
            when(driverRepository.findByPsnId("unknown_driver")).thenReturn(Optional.empty());
            when(driverRepository.findByPsnIdIgnoreCase("unknown_driver")).thenReturn(Optional.empty());
            when(driverRepository.findAll()).thenReturn(List.of(hills, micky));

            var result = matchingService.findDriver("unknown_driver");

            assertEquals(DriverMatchingService.MatchType.NONE, result.type());
            assertFalse(result.isMatch());
        }

        @Test
        void shouldReturnNoMatchForBlankInput() {
            var result = matchingService.findDriver("");
            assertEquals(DriverMatchingService.MatchType.NONE, result.type());
        }

        @Test
        void shouldReturnNoMatchForNull() {
            var result = matchingService.findDriver(null);
            assertEquals(DriverMatchingService.MatchType.NONE, result.type());
        }
    }

    @Nested
    class SimilarityTest {

        @Test
        void shouldCalculateIdenticalStrings() {
            assertEquals(1.0, matchingService.calculateSimilarity("abc", "abc"));
        }

        @Test
        void shouldCalculateSingleCharDifference() {
            double sim = matchingService.calculateSimilarity("AHR_Hills_93", "AHR_Hils_93");
            assertTrue(sim > 0.9, "Expected > 0.9 but was " + sim);
        }

        @Test
        void shouldBeCaseInsensitive() {
            assertEquals(
                    matchingService.calculateSimilarity("ABC", "abc"),
                    matchingService.calculateSimilarity("abc", "abc"));
        }

        @Test
        void shouldReturn0ForCompletelyDifferent() {
            double sim = matchingService.calculateSimilarity("aaaa", "zzzz");
            assertTrue(sim < 0.5);
        }
    }
}
