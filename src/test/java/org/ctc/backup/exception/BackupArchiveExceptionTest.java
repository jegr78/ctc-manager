package org.ctc.backup.exception;

import org.ctc.backup.exception.BackupArchiveException.Reason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackupArchiveExceptionTest {

    @Test
    void givenEachReason_whenConstruct_thenReasonAndMessagePropagate() {
        // given — enforces exactly 8 Reason values; any accidental addition or removal fails here
        assertThat(Reason.values()).hasSize(8);

        // when / then — loop over every value; each must round-trip correctly
        for (Reason reason : Reason.values()) {
            String message = "reason=" + reason.name();
            BackupArchiveException ex = new BackupArchiveException(reason, message);

            assertThat(ex.reason())
                    .as("reason should match for %s", reason)
                    .isSameAs(reason);
            assertThat(ex.getMessage())
                    .as("message should match for %s", reason)
                    .isEqualTo(message);
        }
    }

    @Test
    void givenReasonAndMessageAndCause_whenConstruct_thenCausePreserved() {
        // given
        RuntimeException cause = new RuntimeException("root cause");

        // when — two-arg constructor (no cause)
        BackupArchiveException twoArg = new BackupArchiveException(Reason.MANIFEST_MISSING, "no manifest");

        // then
        assertThat(twoArg.getCause()).isNull();
        assertThat(twoArg.reason()).isSameAs(Reason.MANIFEST_MISSING);

        // when — three-arg constructor (with cause)
        BackupArchiveException threeArg = new BackupArchiveException(Reason.MANIFEST_INVALID, "parse failed", cause);

        // then
        assertThat(threeArg.getCause()).isSameAs(cause);
        assertThat(threeArg.reason()).isSameAs(Reason.MANIFEST_INVALID);
        assertThat(threeArg.getMessage()).isEqualTo("parse failed");
    }
}
