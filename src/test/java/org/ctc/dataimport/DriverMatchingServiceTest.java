package org.ctc.dataimport;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.Driver;
import org.ctc.domain.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
		void givenKnownPsnId_whenFindDriver_thenReturnsExactMatch() {
			// given
			when(driverRepository.findByPsnId("AHR_Hills_93")).thenReturn(Optional.of(hills));

			// when
			var result = matchingService.findDriver("AHR_Hills_93");

			// then
			assertEquals(DriverMatchingService.MatchType.EXACT, result.type());
			assertEquals(hills, result.driver());
			assertFalse(result.needsConfirmation());
		}

		@Test
		void givenPsnIdWithDifferentCase_whenFindDriver_thenReturnsExactMatch() {
			// given
			when(driverRepository.findByPsnId("ahr_hills_93")).thenReturn(Optional.empty());
			when(driverRepository.findByPsnIdIgnoreCase("ahr_hills_93")).thenReturn(Optional.of(hills));

			// when
			var result = matchingService.findDriver("ahr_hills_93");

			// then
			assertEquals(DriverMatchingService.MatchType.EXACT, result.type());
			assertEquals(hills, result.driver());
		}
	}

	@Nested
	class AliasMatchTest {

		@Test
		void givenKnownAlias_whenFindDriver_thenReturnsExactMatch() {
			// given
			when(driverRepository.findByPsnId("OldPsnId_93")).thenReturn(Optional.empty());
			when(driverRepository.findByPsnIdIgnoreCase("OldPsnId_93")).thenReturn(Optional.empty());
			when(driverRepository.findByAliasIgnoreCase("OldPsnId_93")).thenReturn(Optional.of(hills));

			// when
			var result = matchingService.findDriver("OldPsnId_93");

			// then
			assertEquals(DriverMatchingService.MatchType.EXACT, result.type());
			assertEquals(hills, result.driver());
			assertFalse(result.needsConfirmation());
		}

		@Test
		void givenAliasDifferentCase_whenFindDriver_thenReturnsExactMatch() {
			// given
			when(driverRepository.findByPsnId("oldpsnid_93")).thenReturn(Optional.empty());
			when(driverRepository.findByPsnIdIgnoreCase("oldpsnid_93")).thenReturn(Optional.empty());
			when(driverRepository.findByAliasIgnoreCase("oldpsnid_93")).thenReturn(Optional.of(hills));

			// when
			var result = matchingService.findDriver("oldpsnid_93");

			// then
			assertEquals(DriverMatchingService.MatchType.EXACT, result.type());
			assertEquals(hills, result.driver());
		}

		@Test
		void givenNoAliasMatch_whenFindDriver_thenFallsToFuzzy() {
			// given
			when(driverRepository.findByPsnId("AHR_Hils_93")).thenReturn(Optional.empty());
			when(driverRepository.findByPsnIdIgnoreCase("AHR_Hils_93")).thenReturn(Optional.empty());
			when(driverRepository.findByAliasIgnoreCase("AHR_Hils_93")).thenReturn(Optional.empty());
			when(driverRepository.findAll()).thenReturn(List.of(hills, micky));

			// when
			var result = matchingService.findDriver("AHR_Hils_93");

			// then
			assertEquals(DriverMatchingService.MatchType.FUZZY, result.type());
			assertEquals(hills, result.driver());
		}

		@Test
		void givenFuzzyMatchOnAlias_whenFindDriver_thenReturnsFuzzyMatch() {
			// given
			hills.addAlias("AHR_Mountain_93");
			when(driverRepository.findByPsnId("AHR_Mountan_93")).thenReturn(Optional.empty());
			when(driverRepository.findByPsnIdIgnoreCase("AHR_Mountan_93")).thenReturn(Optional.empty());
			when(driverRepository.findByAliasIgnoreCase("AHR_Mountan_93")).thenReturn(Optional.empty());
			when(driverRepository.findAll()).thenReturn(List.of(hills, micky));

			// when
			var result = matchingService.findDriver("AHR_Mountan_93");

			// then
			assertEquals(DriverMatchingService.MatchType.FUZZY, result.type());
			assertEquals(hills, result.driver());
			assertTrue(result.similarity() >= 0.8);
		}
	}

	@Nested
	class FuzzyMatchTest {

		@Test
		void givenSlightlyMisspelledPsnId_whenFindDriver_thenReturnsFuzzyMatchOnPsnId() {
			// given
			when(driverRepository.findByPsnId("AHR_Hils_93")).thenReturn(Optional.empty());
			when(driverRepository.findByPsnIdIgnoreCase("AHR_Hils_93")).thenReturn(Optional.empty());
			when(driverRepository.findByAliasIgnoreCase("AHR_Hils_93")).thenReturn(Optional.empty());
			when(driverRepository.findAll()).thenReturn(List.of(hills, micky));

			// when
			var result = matchingService.findDriver("AHR_Hils_93");

			// then
			assertEquals(DriverMatchingService.MatchType.FUZZY, result.type());
			assertEquals(hills, result.driver());
			assertTrue(result.needsConfirmation());
			assertTrue(result.similarity() >= 0.8);
		}

		@Test
		void givenSlightlyMisspelledNickname_whenFindDriver_thenReturnsFuzzyMatchOnNickname() {
			// given
			when(driverRepository.findByPsnId("Micky D Higins")).thenReturn(Optional.empty());
			when(driverRepository.findByPsnIdIgnoreCase("Micky D Higins")).thenReturn(Optional.empty());
			when(driverRepository.findByAliasIgnoreCase("Micky D Higins")).thenReturn(Optional.empty());
			when(driverRepository.findAll()).thenReturn(List.of(hills, micky));

			// when
			var result = matchingService.findDriver("Micky D Higins");

			// then
			assertEquals(DriverMatchingService.MatchType.FUZZY, result.type());
			assertEquals(micky, result.driver());
			assertTrue(result.similarity() >= 0.8);
		}

		@Test
		void givenPsnIdBelowSimilarityThreshold_whenFindDriver_thenReturnsNoMatch() {
			// given
			when(driverRepository.findByPsnId("completely_different")).thenReturn(Optional.empty());
			when(driverRepository.findByPsnIdIgnoreCase("completely_different")).thenReturn(Optional.empty());
			when(driverRepository.findByAliasIgnoreCase("completely_different")).thenReturn(Optional.empty());
			when(driverRepository.findAll()).thenReturn(List.of(hills, micky));

			// when
			var result = matchingService.findDriver("completely_different");

			// then
			assertEquals(DriverMatchingService.MatchType.NONE, result.type());
			assertNull(result.driver());
		}
	}

	@Nested
	class NoMatchTest {

		@Test
		void givenUnknownPsnId_whenFindDriver_thenReturnsNoMatch() {
			// given
			when(driverRepository.findByPsnId("unknown_driver")).thenReturn(Optional.empty());
			when(driverRepository.findByPsnIdIgnoreCase("unknown_driver")).thenReturn(Optional.empty());
			when(driverRepository.findByAliasIgnoreCase("unknown_driver")).thenReturn(Optional.empty());
			when(driverRepository.findAll()).thenReturn(List.of(hills, micky));

			// when
			var result = matchingService.findDriver("unknown_driver");

			// then
			assertEquals(DriverMatchingService.MatchType.NONE, result.type());
			assertFalse(result.isMatch());
		}

		@Test
		void givenBlankInput_whenFindDriver_thenReturnsNoMatch() {
			// when
			var result = matchingService.findDriver("");

			// then
			assertEquals(DriverMatchingService.MatchType.NONE, result.type());
		}

		@Test
		void givenNullInput_whenFindDriver_thenReturnsNoMatch() {
			// when
			var result = matchingService.findDriver(null);

			// then
			assertEquals(DriverMatchingService.MatchType.NONE, result.type());
		}
	}

	@Nested
	class SimilarityTest {

		@Test
		void givenIdenticalStrings_whenCalculateSimilarity_thenReturnsOne() {
			// when / then
			assertEquals(1.0, matchingService.calculateSimilarity("abc", "abc"));
		}

		@Test
		void givenSingleCharDifference_whenCalculateSimilarity_thenReturnsHighSimilarity() {
			// when
			double sim = matchingService.calculateSimilarity("AHR_Hills_93", "AHR_Hils_93");

			// then
			assertTrue(sim > 0.9, "Expected > 0.9 but was " + sim);
		}

		@Test
		void givenDifferentCases_whenCalculateSimilarity_thenIsCaseInsensitive() {
			// when / then
			assertEquals(
					matchingService.calculateSimilarity("ABC", "abc"),
					matchingService.calculateSimilarity("abc", "abc"));
		}

		@Test
		void givenCompletelyDifferentStrings_whenCalculateSimilarity_thenReturnsLowSimilarity() {
			// when
			double sim = matchingService.calculateSimilarity("aaaa", "zzzz");

			// then
			assertTrue(sim < 0.5);
		}
	}
}
