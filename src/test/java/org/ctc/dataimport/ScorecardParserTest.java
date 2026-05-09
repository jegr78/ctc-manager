package org.ctc.dataimport;

import org.ctc.dataimport.CsvImportService.ImportMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScorecardParserTest {

	@Mock
	private DriverMatchingService driverMatchingService;

	@InjectMocks
	private ScorecardParser parser;

	private ImportMetadata metadata;

	@BeforeEach
	void setUp() {
		metadata = new ImportMetadata(UUID.randomUUID(), "Matchday 1", null, null);
	}

	/**
	 * Mocks findDriver to return NONE for any PSN ID. Call in @BeforeEach of nested classes that parse driver rows.
	 */
	private void mockDriverMatchingNone() {
		when(driverMatchingService.findDriver(anyString()))
				.thenAnswer(inv -> DriverMatchingService.MatchResult.noMatch(inv.getArgument(0)));
	}


	/**
	 * Creates a team header row: [teamName, "Position", "Quali", "FL"]
	 */
	private List<Object> headerRow(String teamName) {
		return List.of(teamName, "Position", "Quali", "FL");
	}

	/**
	 * Creates a driver row: [psnId, position, quali, fastestLap]
	 */
	private List<Object> driverRow(String psnId, Object position, Object quali, Object fastestLap) {
		return List.of(psnId, position, quali, fastestLap);
	}

	/**
	 * Creates the "Overall" summary row
	 */
	private List<Object> overallRow() {
		return List.of("Overall", "", "", "");
	}

	/**
	 * Creates an empty row
	 */
	private List<Object> emptyRow() {
		return List.of();
	}

	/**
	 * Builds a standard scorecard with two teams having the given number of drivers each.
	 */
	private List<List<Object>> buildTwoTeamScorecard(String team1, int drivers1, String team2, int drivers2) {
		var sheet = new ArrayList<List<Object>>();

		// Team 1 block
		sheet.add(headerRow(team1));
		for (int i = 1; i <= drivers1; i++) {
			sheet.add(driverRow("driver_" + team1.replace(" ", "_") + "_" + i, i, i, Boolean.FALSE));
		}
		sheet.add(overallRow());

		// Empty separator
		sheet.add(emptyRow());

		// Team 2 block
		sheet.add(headerRow(team2));
		for (int i = 1; i <= drivers2; i++) {
			sheet.add(driverRow("driver_" + team2.replace(" ", "_") + "_" + i, i, i, Boolean.FALSE));
		}
		sheet.add(overallRow());

		return sheet;
	}

	@Nested
	class FullScorecardTest {

		@BeforeEach
		void setUp() {
			mockDriverMatchingNone();
		}

		@Test
		void givenTwoTeamsWithSixDriversEach_whenParse_thenReturnsTwelveRows() {
			// given
			var sheetData = buildTwoTeamScorecard("AHR 1", 6, "TCR", 6);

			// when
			var preview = parser.parse(sheetData, metadata);

			// then
			assertEquals(12, preview.getRows().size());
			assertFalse(preview.hasErrors());

			// Verify team assignment
			var team1Rows = preview.getRows().stream()
					.filter(r -> "AHR 1".equals(r.teamShortName())).toList();
			var team2Rows = preview.getRows().stream()
					.filter(r -> "TCR".equals(r.teamShortName())).toList();
			assertEquals(6, team1Rows.size());
			assertEquals(6, team2Rows.size());

			// Verify positions are sequential
			for (int i = 0; i < 6; i++) {
				assertEquals(i + 1, team1Rows.get(i).position());
				assertEquals(i + 1, team2Rows.get(i).position());
			}
		}

		@Test
		void givenTwoTeamsWithDifferentDriverCounts_whenParse_thenReturnsCombinedRows() {
			// given
			var sheetData = buildTwoTeamScorecard("AHR 1", 4, "TCR", 6);

			// when
			var preview = parser.parse(sheetData, metadata);

			// then
			assertEquals(10, preview.getRows().size());
			assertFalse(preview.hasErrors());

			var team1Rows = preview.getRows().stream()
					.filter(r -> "AHR 1".equals(r.teamShortName())).toList();
			var team2Rows = preview.getRows().stream()
					.filter(r -> "TCR".equals(r.teamShortName())).toList();
			assertEquals(4, team1Rows.size());
			assertEquals(6, team2Rows.size());
		}
	}

	@Nested
	class TeamNameNormalizationTest {

		@BeforeEach
		void setUp() {
			mockDriverMatchingNone();
		}

		@Test
		void givenTeamNameWithSpaces_whenParse_thenPreservesSpaces() {
			// given
			var sheetData = new ArrayList<List<Object>>();
			sheetData.add(headerRow("AHR 1"));
			sheetData.add(driverRow("driver1", 1, 1, Boolean.FALSE));
			sheetData.add(overallRow());

			// when
			var preview = parser.parse(sheetData, metadata);

			// then
			assertEquals(1, preview.getRows().size());
			assertEquals("AHR 1", preview.getRows().getFirst().teamShortName());
		}

		@Test
		void givenAlreadyNormalizedTeamName_whenParse_thenPreservesName() {
			// given
			var sheetData = new ArrayList<List<Object>>();
			sheetData.add(headerRow("TCR"));
			sheetData.add(driverRow("driver1", 1, 1, Boolean.FALSE));
			sheetData.add(overallRow());

			// when
			var preview = parser.parse(sheetData, metadata);

			// then
			assertEquals(1, preview.getRows().size());
			assertEquals("TCR", preview.getRows().getFirst().teamShortName());
		}
	}

	@Nested
	class FastestLapParsingTest {

		@BeforeEach
		void setUp() {
			mockDriverMatchingNone();
		}

		@Test
		void givenBooleanTrue_whenParse_thenFastestLapIsTrue() {
			// given
			var sheetData = new ArrayList<List<Object>>();
			sheetData.add(headerRow("TCR"));
			sheetData.add(driverRow("driver1", 1, 1, Boolean.TRUE));
			sheetData.add(overallRow());

			// when
			var preview = parser.parse(sheetData, metadata);

			// then
			assertEquals(1, preview.getRows().size());
			assertTrue(preview.getRows().getFirst().fastestLap());
		}

		@Test
		void givenBooleanFalse_whenParse_thenFastestLapIsFalse() {
			// given
			var sheetData = new ArrayList<List<Object>>();
			sheetData.add(headerRow("TCR"));
			sheetData.add(driverRow("driver1", 1, 1, Boolean.FALSE));
			sheetData.add(overallRow());

			// when
			var preview = parser.parse(sheetData, metadata);

			// then
			assertEquals(1, preview.getRows().size());
			assertFalse(preview.getRows().getFirst().fastestLap());
		}

		@Test
		void givenStringTrue_whenParse_thenFastestLapIsTrue() {
			// given
			var sheetData = new ArrayList<List<Object>>();
			sheetData.add(headerRow("TCR"));
			sheetData.add(driverRow("driver1", 1, 1, "TRUE"));
			sheetData.add(overallRow());

			// when
			var preview = parser.parse(sheetData, metadata);

			// then
			assertEquals(1, preview.getRows().size());
			assertTrue(preview.getRows().getFirst().fastestLap());
		}

		@Test
		void givenStringFalse_whenParse_thenFastestLapIsFalse() {
			// given
			var sheetData = new ArrayList<List<Object>>();
			sheetData.add(headerRow("TCR"));
			sheetData.add(driverRow("driver1", 1, 1, "FALSE"));
			sheetData.add(overallRow());

			// when
			var preview = parser.parse(sheetData, metadata);

			// then
			assertEquals(1, preview.getRows().size());
			assertFalse(preview.getRows().getFirst().fastestLap());
		}
	}

	@Nested
	class ErrorHandlingTest {

		@Test
		void givenRowWithInvalidPosition_whenParse_thenSkipsRowAndAddsError() {
			// given
			var sheetData = new ArrayList<List<Object>>();
			sheetData.add(headerRow("TCR"));
			sheetData.add(driverRow("driver1", "abc", 1, Boolean.FALSE));
			sheetData.add(overallRow());

			// when
			var preview = parser.parse(sheetData, metadata);

			// then
			assertEquals(0, preview.getRows().size());
			assertTrue(preview.hasErrors());
			assertTrue(preview.getErrors().getFirst().contains("Invalid value for"));
		}

		@Test
		void givenRowWithTooFewColumns_whenParse_thenSkipsRowAndAddsError() {
			// given
			var sheetData = new ArrayList<List<Object>>();
			sheetData.add(headerRow("TCR"));
			sheetData.add(List.of("driver1", 1)); // only 2 columns
			sheetData.add(overallRow());

			// when
			var preview = parser.parse(sheetData, metadata);

			// then
			assertEquals(0, preview.getRows().size());
			assertTrue(preview.hasErrors());
			assertTrue(preview.getErrors().getFirst().contains("Too few columns"));
		}

		@Test
		void givenEmptySheetData_whenParse_thenAddsEmptyError() {
			// when
			var preview = parser.parse(List.of(), metadata);

			// then
			assertEquals(0, preview.getRows().size());
			assertTrue(preview.hasErrors());
			assertTrue(preview.getErrors().getFirst().contains("empty"));
		}

		@Test
		void givenNullSheetData_whenParse_thenAddsEmptyError() {
			// when
			var preview = parser.parse(null, metadata);

			// then
			assertEquals(0, preview.getRows().size());
			assertTrue(preview.hasErrors());
			assertTrue(preview.getErrors().getFirst().contains("empty"));
		}

		@Test
		void givenRowWithEmptyPsnId_whenParse_thenSkipsRowAndAddsError() {
			// given
			var sheetData = new ArrayList<List<Object>>();
			sheetData.add(headerRow("TCR"));
			sheetData.add(driverRow("", 1, 1, Boolean.FALSE));
			sheetData.add(overallRow());

			// when
			var preview = parser.parse(sheetData, metadata);

			// then
			assertEquals(0, preview.getRows().size());
			assertTrue(preview.hasErrors());
			assertTrue(preview.getErrors().getFirst().contains("PSN ID is empty"));
		}
	}

	@Nested
	class DecimalParsingTest {

		@BeforeEach
		void setUp() {
			mockDriverMatchingNone();
		}

		@Test
		void givenDecimalStringPositions_whenParse_thenParsesAsIntegers() {
			// given
			var sheetData = new ArrayList<List<Object>>();
			sheetData.add(headerRow("TCR"));
			sheetData.add(driverRow("driver1", "2.0", "3.0", Boolean.FALSE));
			sheetData.add(overallRow());

			// when
			var preview = parser.parse(sheetData, metadata);

			// then
			assertEquals(1, preview.getRows().size());
			assertFalse(preview.hasErrors());
			assertEquals(2, preview.getRows().getFirst().position());
			assertEquals(3, preview.getRows().getFirst().qualiPosition());
		}
	}

	@Nested
	class MultipleRacesParsingTest {

		@BeforeEach
		void setUp() {
			mockDriverMatchingNone();
		}

		@Test
		void givenTwoRaceSheets_whenParseMultipleRaces_thenReturnsListWithTwoPreviewsSameSizeEach() {
			// given
			var race1Data = buildTwoTeamScorecard("AHR 1", 3, "TCR", 3);
			var race2Data = buildTwoTeamScorecard("AHR 1", 3, "TCR", 3);
			var sheetNames = List.of("Race 1", "Race 2");

			// when
			var previews = parser.parseMultipleRaces(
					Map.of("Race 1", race1Data, "Race 2", race2Data),
					sheetNames,
					metadata
			);

			// then
			assertEquals(2, previews.size());
			assertEquals(6, previews.get(0).getRows().size());
			assertEquals(6, previews.get(1).getRows().size());
			assertFalse(previews.get(0).hasErrors());
			assertFalse(previews.get(1).hasErrors());
		}

		@Test
		void givenSingleRaceSheet_whenParseMultipleRaces_thenReturnsListWithOnePreview() {
			// given
			var raceData = buildTwoTeamScorecard("AHR 1", 3, "TCR", 3);
			var sheetNames = List.of("Race 1");

			// when
			var previews = parser.parseMultipleRaces(
					Map.of("Race 1", raceData),
					sheetNames,
					metadata
			);

			// then
			assertEquals(1, previews.size());
			assertEquals(6, previews.get(0).getRows().size());
			assertFalse(previews.get(0).hasErrors());
		}

		@Test
		void givenRaceSheetsWithDifferentDriverCounts_whenParseMultipleRaces_thenEachPreviewReflectsDifferentCounts() {
			// given
			var race1Data = buildTwoTeamScorecard("AHR 1", 3, "TCR", 3);
			var race2Data = buildTwoTeamScorecard("AHR 1", 4, "TCR", 2);
			var sheetNames = List.of("Race 1", "Race 2");

			// when
			var previews = parser.parseMultipleRaces(
					Map.of("Race 1", race1Data, "Race 2", race2Data),
					sheetNames,
					metadata
			);

			// then
			assertEquals(2, previews.size());
			assertEquals(6, previews.get(0).getRows().size());
			assertEquals(6, previews.get(1).getRows().size());
		}

		@Test
		void givenMissingSheetInDataMap_whenParseMultipleRaces_thenSkipsAndLogsWarning() {
			// given
			var race1Data = buildTwoTeamScorecard("AHR 1", 3, "TCR", 3);
			var sheetNames = List.of("Race 1", "Race 2");

			// when
			var previews = parser.parseMultipleRaces(
					Map.of("Race 1", race1Data),
					sheetNames,
					metadata
			);

			// then
			assertEquals(1, previews.size());
			assertEquals(6, previews.get(0).getRows().size());
		}
	}
}
