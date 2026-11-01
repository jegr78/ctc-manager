package org.ctc.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.gt7sync.Gt7ScraperService.ScrapedCar;
import org.ctc.gt7sync.GtPlusCarYearSource.YearCandidate;
import org.springframework.stereotype.Service;

@Service
public class CarMetadataService {
    private static final Pattern SUFFIX = Pattern.compile("['’](\\d{2})$");
    private static final ObjectMapper JSON = new ObjectMapper();

    public Proposal propose(Car current, ScrapedCar official, YearCandidate candidate) {
        Car car = current != null ? current : new Car(official.manufacturer(), official.name());
        var notices = new ArrayList<String>();
        CarCategory category = car.getCategory();
        Drivetrain drivetrain = car.getDrivetrain();
        String categorySource = car.getCategorySource();
        String drivetrainSource = car.getDrivetrainSource();
        if (!car.isCategoryOverride() && official.category() != null) {
            category = official.category();
            categorySource = "GT7:" + official.gt7Id() + ":carClass=" + official.categoryValue();
        } else if (official.category() == null && official.categoryValue() != null) {
            notices.add("Unknown official category: " + official.categoryValue());
        }
        if (!car.isDrivetrainOverride() && official.drivetrain() != null) {
            drivetrain = official.drivetrain();
            drivetrainSource = "GT7:" + official.gt7Id() + ":driveTrain=" + official.drivetrainValue();
        } else if (official.drivetrain() == null && official.drivetrainValue() != null) {
            notices.add("Unknown official drivetrain: " + official.drivetrainValue());
        }
        Integer year = car.getModelYear();
        String yearSource = car.getModelYearSource();
        String yearEvidence = car.getModelYearEvidence();
        Integer proposedYear = car.getProposedModelYear();
        String proposedEvidence = car.getProposedModelYearEvidence();
        if (!car.isModelYearOverride() && year == null
                && ("car3599".equals(official.gt7Id()) || "car3606".equals(official.gt7Id()))) {
            year = 2025;
            yearSource = "REVIEWED_GT7_ID";
            yearEvidence = "Reviewed correction 2026-10-31: " + official.gt7Id() + " model year 2025";
        }
        if (candidate != null) {
            if (candidate.problem() != null) {
                proposedYear = null;
                notices.add(candidate.problem());
                String evidence = candidate.evidence() + "; " + candidate.problem();
                proposedEvidence = evidence.substring(0, Math.min(1000, evidence.length()));
            }
            if (candidate.year() != null && candidate.year() >= 1000 && candidate.year() <= 9999) {
                proposedYear = candidate.year();
                proposedEvidence = candidate.evidence();
                var suffix = SUFFIX.matcher(official.name().trim());
                boolean matches = suffix.find() && Integer.parseInt(suffix.group(1)) == proposedYear % 100;
                if (!car.isModelYearOverride() && year == null && matches && candidate.problem() == null) {
                    year = proposedYear;
                    yearSource = "GTPLUS_SUFFIX";
                    yearEvidence = candidate.evidence() + "; official name=" + official.name();
                } else if (!Objects.equals(year, proposedYear)) {
                    notices.add("Model year candidate requires review");
                }
            }
        }
        return new Proposal(category, drivetrain, year, proposedYear, categorySource, drivetrainSource,
                yearSource, yearEvidence, proposedEvidence, List.copyOf(notices));
    }

    public void applyImported(Car car, Proposal p) {
        car.setCategory(p.category());
        car.setDrivetrain(p.drivetrain());
        car.setModelYear(p.modelYear());
        car.setProposedModelYear(p.proposedModelYear());
        car.setCategorySource(p.categorySource());
        car.setDrivetrainSource(p.drivetrainSource());
        car.setModelYearSource(p.modelYearSource());
        car.setModelYearEvidence(p.modelYearEvidence());
        car.setProposedModelYearEvidence(p.proposedModelYearEvidence());
    }

    public void applyManual(Car car, CarCategory category, Drivetrain drivetrain, Integer year,
                            boolean categoryOverride, boolean drivetrainOverride, boolean yearOverride) {
        validateYear(year);
        if (!Objects.equals(car.getCategory(), category) || categoryOverride != car.isCategoryOverride())
            car.setCategorySource("MANUAL");
        if (!Objects.equals(car.getDrivetrain(), drivetrain) || drivetrainOverride != car.isDrivetrainOverride())
            car.setDrivetrainSource("MANUAL");
        if (!Objects.equals(car.getModelYear(), year) || yearOverride != car.isModelYearOverride()) {
            car.setModelYearSource("MANUAL");
            car.setModelYearEvidence(year == null ? "Explicitly unknown model year" : "Administrative model year " + year);
        }
        categoryOverride |= !Objects.equals(car.getCategory(), category);
        drivetrainOverride |= !Objects.equals(car.getDrivetrain(), drivetrain);
        yearOverride |= !Objects.equals(car.getModelYear(), year);
        car.setCategory(category);
        car.setDrivetrain(drivetrain);
        car.setModelYear(year);
        car.setCategoryOverride(categoryOverride);
        car.setDrivetrainOverride(drivetrainOverride);
        car.setModelYearOverride(yearOverride);
    }

    public static void validateYear(Integer year) {
        if (year != null && (year < 1000 || year > 9999))
            throw new BusinessRuleException("Model year must be a four-digit year or unknown.");
    }

    public boolean changes(Car car, Proposal p) {
        return !metadataValues(car).equals(Arrays.asList(p.category(), p.drivetrain(), p.modelYear(), p.proposedModelYear(),
                p.categorySource(), p.drivetrainSource(), p.modelYearSource(), p.modelYearEvidence(), p.proposedModelYearEvidence()));
    }

    private List<Object> metadataValues(Car car) {
        return Arrays.asList(car.getCategory(), car.getDrivetrain(), car.getModelYear(), car.getProposedModelYear(),
                car.getCategorySource(), car.getDrivetrainSource(), car.getModelYearSource(), car.getModelYearEvidence(), car.getProposedModelYearEvidence());
    }

    public String fingerprint(Car car, ScrapedCar official, Proposal proposal) {
        try {
            var state = car == null ? List.of() : Arrays.asList(car.getId(), car.getGt7Id(), car.getManufacturer(), car.getName(), car.getImageUrl(),
                    metadataValues(car), car.isCategoryOverride(), car.isDrivetrainOverride(), car.isModelYearOverride(), Objects.toString(car.getUpdatedAt(), ""));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JSON.writeValueAsString(Arrays.asList(official, proposal, state)).getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot fingerprint car metadata", e);
        }
    }

    public record ManualMetadata(CarCategory category, Drivetrain drivetrain, Integer modelYear,
                                 boolean categoryOverride, boolean drivetrainOverride, boolean modelYearOverride) {}

    public record Proposal(CarCategory category, Drivetrain drivetrain, Integer modelYear,
                           Integer proposedModelYear, String categorySource, String drivetrainSource,
                           String modelYearSource, String modelYearEvidence, String proposedModelYearEvidence,
                           List<String> notices) {}
}
