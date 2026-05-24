package org.ctc.admin.dto;

import org.jspecify.annotations.Nullable;

public record MatchPreviewPreFlightResult(boolean canPost, @Nullable String disabledReason) {
}
