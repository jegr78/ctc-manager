package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceAttachment;

/**
 * Externalised Jackson annotations for {@link RaceAttachment}. Phase 73 EXPORT-04.
 *
 * <p>The {@code type} field is an {@code AttachmentType} enum — serialized as the enum
 * name ({@code "FILE"} / {@code "LINK"}) via the default Jackson enum strategy.
 *
 * <p>Suppresses the {@code isImage()} convenience getter (property {@code image}).
 *
 * <p>The {@code url} field stays — it drives the {@code uploads/} mirror enumeration in
 * Wave 2's {@code BackupArchiveService}.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "image"})
public abstract class RaceAttachmentMixIn {

    @JsonIdentityReference(alwaysAsId = true)
    abstract Race getRace();
}
