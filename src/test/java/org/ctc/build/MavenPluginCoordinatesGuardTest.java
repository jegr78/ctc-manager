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
    private static final Pattern COORDINATE_GOAL = Pattern.compile("[a-z][\\w.-]*:[a-z][\\w.-]*(:[\\w.-]+)?:[a-z][a-zA-Z-]*");

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
                for (String goal : pluginGoals(line.substring(line.indexOf("mvnw")))) {
                    if (!COORDINATE_GOAL.matcher(goal).matches()) violations.add(file + ":" + (index + 1) + " " + goal);
                }
            }
        }
        // then
        assertThat(violations).as("a plugin prefix makes Maven resolve every plugin group's metadata from the network").isEmpty();
    }

    @Test
    void givenPrefixAndCoordinateGoals_whenChecking_thenAcceptOnlyFullCoordinates() {
        // when / then
        assertThat(invalid("mvnw dependency:build-classpath -q")).containsExactly("dependency:build-classpath");
        assertThat(invalid("mvnw -B versions:set -DnewVersion=1")).containsExactly("versions:set");
        assertThat(invalid("mvnw org.apache.maven.plugins:maven-dependency-pluginopy-dependencies -B"))
                .as("a coordinate that lost its goal separator is still a prefix").hasSize(1);
        assertThat(invalid("mvnw clean org.apache.maven.plugins:maven-dependency-plugin:build-classpath -Dmdep.outputFile=target/x.txt")).isEmpty();
        assertThat(invalid("mvnw org.apache.maven.plugins:maven-help-plugin:3.5.2:evaluate -Dx=https://example.com/a:b")).isEmpty();
    }

    private static List<String> invalid(String command) {
        return pluginGoals(command).stream().filter(goal -> !COORDINATE_GOAL.matcher(goal).matches()).toList();
    }

    /** Whitespace-separated arguments with a colon that are not options, expressions or paths. */
    private static List<String> pluginGoals(String command) {
        var goals = new ArrayList<String>();
        for (String token : command.split("[\\s\"'()]+")) {
            if (token.startsWith("-") || token.contains("=") || token.contains("/") || token.contains("$") || !token.contains(":")) continue;
            goals.add(token);
        }
        return goals;
    }
}
