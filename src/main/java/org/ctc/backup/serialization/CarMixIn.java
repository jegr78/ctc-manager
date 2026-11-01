package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonPropertyOrder({"id", "manufacturer", "name", "gt7Id", "imageUrl", "category", "drivetrain", "modelYear",
        "proposedModelYear", "categorySource", "drivetrainSource", "modelYearSource", "modelYearEvidence",
        "proposedModelYearEvidence", "categoryOverride", "drivetrainOverride", "modelYearOverride", "createdAt", "updatedAt"})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "displayName"})
public abstract class CarMixIn {
}
