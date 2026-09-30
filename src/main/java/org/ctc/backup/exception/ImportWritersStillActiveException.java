package org.ctc.backup.exception;

/** Writes admitted before the import lock did not finish within the drain timeout. */
public class ImportWritersStillActiveException extends Exception {

	public ImportWritersStillActiveException(int activeWriters) {
		super(activeWriters + " admitted write request(s) still running");
	}
}
