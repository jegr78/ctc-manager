package org.ctc.trailer;

/** One open precondition; {@code fixUrl} leads to the place that resolves it, or is null. */
public record TrailerChecklistItem(String message, String fixUrl) {
}
