package org.ctc.admin.service;

import java.io.IOException;

/** Contract for graphic services that support custom HTML templates. */
public interface TemplateManageable {
	String loadTemplate() throws IOException;

	void saveTemplate(String content) throws IOException;

	void resetTemplate() throws IOException;

	boolean hasCustomTemplate();
}
