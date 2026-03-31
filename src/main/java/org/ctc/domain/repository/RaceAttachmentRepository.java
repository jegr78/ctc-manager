package org.ctc.domain.repository;

import org.ctc.domain.model.RaceAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RaceAttachmentRepository extends JpaRepository<RaceAttachment, UUID> {
}
