package org.ctc.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MariaDbDriverSecurityTest {

    @Test
    void whenResolvingRuntimeDriver_thenKnownVulnerableConnectorVersionsAreExcluded() throws Exception {
        Properties metadata = new Properties();
        try (var input = Class.forName("org.mariadb.jdbc.Driver").getResourceAsStream(
                "/META-INF/maven/org.mariadb.jdbc/mariadb-java-client/pom.properties")) {
            assertThat(input).as("the resolved connector supplies Maven artifact metadata").isNotNull();
            metadata.load(input);
        }
        String version = metadata.getProperty("version");
        assertThat(version).as("the runtime connector declares its actual version").isNotBlank();
        var parsed = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+).*$").matcher(version);
        assertThat(parsed.matches()).as("runtime connector version %s is comparable", version).isTrue();
        int major = Integer.parseInt(parsed.group(1));
        int minor = Integer.parseInt(parsed.group(2));
        int patch = Integer.parseInt(parsed.group(3));
        assertThat(major > 3 || major == 3 && (minor > 5 || minor == 5 && patch >= 11))
                .as("runtime connector %s excludes the four advisories fixed in 3.5.11", version).isTrue();
    }
}
