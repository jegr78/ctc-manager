package org.ctc.sitegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

class TemplateWriterTest {

	@TempDir
	Path root;

	@Test
	void givenOutputFileOutsideTheSiteRoot_whenWritten_thenRefusedWithoutRendering() {
		// given
		TemplateEngine engine = mock(TemplateEngine.class);
		var writer = new TemplateWriter(engine, new SiteProperties());
		Path outside = root.resolve("season/s/team/../../../../evil.html");

		// when / then
		assertThatThrownBy(() -> writer.write("site/team-profile", new Context(Locale.ENGLISH), outside, root, "", ""))
				.isInstanceOf(IOException.class)
				.hasMessageStartingWith("Refusing to write outside the site output");
		verifyNoInteractions(engine);
		assertThat(root.getParent().resolve("evil.html")).as("file outside the root").doesNotExist();
	}
}
