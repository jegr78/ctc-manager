package org.ctc.domain.model;

import java.util.UUID;

/** A row whose backup must carry its stored upload file. */
public interface StoredFileRow {
    UUID getId();

    String storedFilePath();
}
