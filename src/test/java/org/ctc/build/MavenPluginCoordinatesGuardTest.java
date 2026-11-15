package org.ctc.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MavenPluginCoordinatesGuardTest {
    private static final Pattern PREFIX_GOAL = Pattern.compile("(?<![\\w.:/=-])[a-z][a-z0-9-]*:[a-z][a-zA-Z-]*(?![\\w:/-])");

    @Test
    void givenCiDockerAndReleaseCommands_whenCallingAPluginGoal_thenUseFullCoordinatesInsteadOfAPrefix() throws IOException {
        // given
        var files = new ArrayList<Path>(List.of(Path.of("Dockerfile")));
        try (Stream<Path> paths = Stream.concat(Stream.concat(Files.walk(Path.of(".github")), Files.walk(Path.of("scripts/ci"))), Files.walk(Path.of("deploy")))) {
            paths.filter(Files::isRegularFile).filter(path -> path.toString().matches(".*\\.(ya?ml|sh|py)$")).forEach(files::add);
        }
        // when
        var violations = new ArrayList<String>();
        for (var file : files) {
            var lines = Files.readAllLines(file);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (!line.contains("mvnw")) continue;
                var match = PREFIX_GOAL.matcher(line.substring(line.indexOf("mvnw")));
                while (match.find()) violations.add(file + ":" + (index + 1) + " " + match.group());
            }
        }
        // then
        assertThat(violations).as("a plugin prefix makes Maven resolve every plugin group's metadata from the network").isEmpty();
    }

    @Test
    void givenPrefixAndCoordinateGoals_whenMatching_thenFlagOnlyThePrefix() {
        // when / then
        assertThat(PREFIX_GOAL.matcher("mvnw dependency:build-classpath -q").find()).isTrue();
        assertThat(PREFIX_GOAL.matcher("mvnw -B versions:set -DnewVersion=1").find()).isTrue();
        assertThat(PREFIX_GOAL.matcher("mvnw org.apache.maven.plugins:maven-dependency-plugin:build-classpath -Dmdep.outputFile=target/x.txt").find()).isFalse();
        assertThat(PREFIX_GOAL.matcher("mvnw clean verify -Dsome=https://example.com/a:b").find()).isFalse();
    }
}
