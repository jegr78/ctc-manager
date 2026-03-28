package de.ctc.domain.repository;

import de.ctc.domain.model.RaceAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RaceAttachmentRepository extends JpaRepository<RaceAttachment, UUID> {
}
