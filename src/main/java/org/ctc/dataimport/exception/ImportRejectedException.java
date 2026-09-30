package org.ctc.dataimport.exception;

import java.util.List;

/**
 * A race import found invalid rows or metadata; the whole import is rolled back.
 */
public class ImportRejectedException extends RuntimeException {

	private final List<String> errors;

	public ImportRejectedException(List<String> errors) {
		super("Import rejected: " + String.join("; ", errors));
		this.errors = List.copyOf(errors);
	}

	public List<String> getErrors() {
		return errors;
	}
}
