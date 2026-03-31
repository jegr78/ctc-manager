package org.ctc.admin.dto;

import java.util.UUID;

public record MatchdayDto(UUID id, String label, int sortIndex) {
}
