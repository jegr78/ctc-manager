package org.ctc.domain.service;

import org.ctc.domain.model.*;
import org.ctc.gt7sync.Gt7ScraperService.ScrapedCar;
import org.ctc.gt7sync.GtPlusCarYearSource.YearCandidate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CarMetadataServiceTest {
    private final CarMetadataService service = new CarMetadataService();

    @Test
    void givenFourDigitYearsAndSuffixes_whenProposing_thenSourceCenturyIsPreserved() {
        // given / when / then
        for (int year : new int[]{1929, 1999, 2000, 2016, 2020, 2026}) {
            var official = new ScrapedCar("car3531", "Test", "Test Car '" + String.format("%02d", year % 100), "", CarCategory.GR_3, Drivetrain.FR, "Gr.3", "FR");
            var proposal = service.propose(null, official, new YearCandidate(year, "GTPlus code 3531 year " + year, null));
            assertThat(proposal.modelYear()).isEqualTo(year);
            assertThat(proposal.category()).isEqualTo(CarCategory.GR_3);
            assertThat(proposal.modelYearSource()).contains("GTPLUS");
        }
    }

    @Test
    void givenUnknownOrUnreviewedSource_whenMerging_thenTrustedAndExplicitUnknownValuesSurvive() {
        // given
        var car = new Car("Test", "Test Car");
        car.setModelYear(1999);
        var official = new ScrapedCar("car1", "Test", "Test Car '99", "");
        // when / then
        assertThat(service.propose(car, official, new YearCandidate(2099, "changed source", null)).modelYear()).isEqualTo(1999);
        car.setModelYear(null);
        car.setModelYearOverride(true);
        assertThat(service.propose(car, official, new YearCandidate(1999, "source", null)).modelYear()).isNull();
        var concept = new ScrapedCar("car2", "Test", "Tomahawk X", "");
        assertThat(service.propose(null, concept, new YearCandidate(2035, "concept year", null)).modelYear()).isNull();
        assertThat(service.propose(null, concept, new YearCandidate(2035, "concept year", null)).proposedModelYear()).isEqualTo(2035);
        assertThat(service.propose(null, new ScrapedCar("car3599", "Xiaomi", "SU7 Ultra '25", ""), new YearCandidate(null, "missing", null)).modelYear()).isEqualTo(2025);
    }
    @Test
    void givenManualUnknownAndOtherOverrides_whenSyncing_thenIdentityAndDecisionsRemain() {
        // given
        var car = new Car("Test", "Manual Car");
        car.setId(java.util.UUID.randomUUID());
        car.setImageUrl("/uploads/test-car.png");
        service.applyManual(car, CarCategory.GR_4, Drivetrain.RR, null, true, true, true);
        var official = new ScrapedCar("car1", "Other", "Different '20", "remote.png", CarCategory.GR_3, Drivetrain.FR, "Gr.3", "FR");
        // when
        service.applyImported(car, service.propose(car, official, new YearCandidate(2020, "GTPlus evidence", null)));
        // then
        assertThat(car.getName()).isEqualTo("Manual Car");
        assertThat(car.getImageUrl()).isEqualTo("/uploads/test-car.png");
        assertThat(car.getCategory()).isEqualTo(CarCategory.GR_4);
        assertThat(car.getDrivetrain()).isEqualTo(Drivetrain.RR);
        assertThat(car.getModelYear()).isNull();
        assertThat(car.isModelYearOverride()).isTrue();
        assertThat(car.getModelYearSource()).isEqualTo("MANUAL");
        assertThat(car.getProposedModelYear()).isEqualTo(2020);
        assertThat(car.getProposedModelYearEvidence()).isEqualTo("GTPlus evidence");
        assertThatThrownBy(() -> service.applyManual(car, null, null, 99, false, false, false))
                .isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);
        assertThat(car.getCategory()).isEqualTo(CarCategory.GR_4);
    }

    @Test
    void givenReviewedIdWithConflictingCandidate_whenProposing_thenReviewedYearWins() {
        // given / when
        var proposal = service.propose(null, new ScrapedCar("car3599", "Xiaomi", "SU7 Ultra '25", ""),
                new YearCandidate(1925, "conflicting GTPlus evidence", null));
        // then
        assertThat(proposal.modelYear()).isEqualTo(2025);
        assertThat(proposal.proposedModelYear()).isEqualTo(1925);
        assertThat(proposal.modelYearSource()).isEqualTo("REVIEWED_GT7_ID");
        assertThat(proposal.notices()).isNotEmpty();
    }

    @Test
    void givenAcceptedYear_whenAdministratorClearsIt_thenUnknownBecomesAnExplicitOverride() {
        // given
        var car = new Car("Test", "Test Car '20");
        car.setModelYear(2020);
        // when
        service.applyManual(car, null, null, null, false, false, false);
        // then
        assertThat(car.isModelYearOverride()).isTrue();
        var proposal = service.propose(car, new ScrapedCar("car1", "Test", "Test Car '20", ""),
                new YearCandidate(2020, "source", null));
        assertThat(proposal.modelYear()).isNull();
    }

    @Test
    void givenUnknownClassificationAndUnreviewedYears_whenProposing_thenNamesNeverInventAcceptedMetadata() {
        // given
        var roadCar = new ScrapedCar("car2", "Test", "Gr.3 Road Car", "", null, null, "---", "---");
        // when / then
        var proposal = service.propose(null, roadCar, new YearCandidate(1963, "Ford Roadster candidate", null));
        assertThat(proposal.category()).isNull();
        assertThat(proposal.drivetrain()).isNull();
        assertThat(proposal.modelYear()).isNull();
        assertThat(proposal.proposedModelYear()).isEqualTo(1963);
        assertThat(proposal.notices()).isNotEmpty();
        var reviewed = service.propose(null, new ScrapedCar("car3606", "Peugeot", "9X8 '25", ""), null);
        assertThat(reviewed.modelYear()).isEqualTo(2025);
        assertThat(reviewed.modelYearEvidence()).contains("car3606");
    }

    @Test
    void givenInvalidCandidateAndMissingYear_whenProposing_thenUnknownStaysUnknownAndEvidenceRemainsReviewable() {
        // given
        var official = new ScrapedCar("car3", "Test", "Test Car '00", "");
        // when / then
        var invalid = service.propose(null, official, new YearCandidate(null, "Bad source record", "Conflicting records"));
        assertThat(invalid.modelYear()).isNull();
        assertThat(invalid.proposedModelYearEvidence()).contains("Bad source", "Conflicting");
        assertThat(service.propose(null, official, null).modelYear()).isNull();
    }

    @Test
    void givenPreviousCandidateAndNowConflictingRecords_whenProposing_thenEvidenceCannotDescribeTheOldCandidate() {
        // given
        var car = new Car("Test", "Test Car '20");
        car.setProposedModelYear(2020);
        car.setProposedModelYearEvidence("Old candidate");
        // when
        var proposal = service.propose(car, new ScrapedCar("car3", "Test", "Test Car '20", ""),
                new YearCandidate(null, "1999 and 2099", "Conflicting records"));
        // then
        assertThat(proposal.proposedModelYear()).isNull();
        assertThat(proposal.proposedModelYearEvidence()).contains("1999 and 2099", "Conflicting");
    }

    @Test
    void givenManualUnknownForReviewedId_whenProposing_thenReviewedCorrectionCannotOverrideDecision() {
        // given
        var car = new Car("Test", "Reviewed Car '25");
        service.applyManual(car, null, null, null, false, false, true);
        // when
        var proposal = service.propose(car, new ScrapedCar("car3599", "Test", "Reviewed Car '25", ""), null);
        // then
        assertThat(proposal.modelYear()).isNull();
        assertThat(proposal.modelYearSource()).isEqualTo("MANUAL");
        assertThat(proposal.modelYearEvidence()).isEqualTo("Explicitly unknown model year");
    }

    @Test
    void givenOnlyTimestampRounding_whenFingerprinting_thenBusinessSnapshotStaysStableAndOverridesStillInvalidateIt() {
        // given
        var car = new Car("Test", "Test Car '20");
        car.setId(java.util.UUID.randomUUID());
        var source = new ScrapedCar("car1", "Test", "Test Car '20", "");
        var proposal = service.propose(car, source, new YearCandidate(2020, "GTPlus candidate 2020", null));
        car.setUpdatedAt(java.time.LocalDateTime.parse("2026-11-01T12:00:00.123456789"));
        var previewHash = service.fingerprint(car, source, proposal);
        // when / then
        car.setUpdatedAt(java.time.LocalDateTime.parse("2026-11-01T12:00:00.123457"));
        assertThat(service.fingerprint(car, source, proposal)).isEqualTo(previewHash);
        car.setModelYearOverride(true);
        assertThat(service.fingerprint(car, source, proposal)).isNotEqualTo(previewHash);
    }

}
