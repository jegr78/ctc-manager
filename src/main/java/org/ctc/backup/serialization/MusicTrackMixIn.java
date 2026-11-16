package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class MusicTrackMixIn {
}
