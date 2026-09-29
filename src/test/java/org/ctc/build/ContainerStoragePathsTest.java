package org.ctc.build;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerStoragePathsTest {

	private static final List<String> PERSISTENT_PATH_PROPERTIES = List.of(
			"app.upload-dir",
			"ctc.site.output-dir",
			"app.backup.staging-dir",
			"app.backup.import-backups-dir");

	@ParameterizedTest(name = "{0} with {1}")
	@CsvSource({
			"docker-compose.prod.yml, prod",
			"docker-compose.yml, docker"})
	void givenComposeFile_whenProfilePathsResolved_thenEveryPathLiesInsideAMountedVolume(
			String composeFile, String profile) throws IOException {
		// given
		Map<String, Object> app = appService(Path.of(composeFile));
		List<String> mountTargets = mountTargets(app);
		Map<String, Object> profileConfig = load(Path.of("src/main/resources/application-" + profile + ".yml"));

		// when / then
		assertThat(environment(app)).containsEntry("SPRING_PROFILES_ACTIVE", profile);
		for (String property : PERSISTENT_PATH_PROPERTIES) {
			Object value = lookup(profileConfig, property);
			assertThat(value)
					.as("%s must set %s explicitly, the base default is relative to the container layer", profile, property)
					.isInstanceOf(String.class);
			Path path = Path.of((String) value);
			assertThat(path.isAbsolute()).as("%s=%s must be absolute", property, value).isTrue();
			assertThat(mountTargets)
					.as("%s=%s must lie inside a volume mounted by %s", property, value, composeFile)
					.anyMatch(target -> path.startsWith(Path.of(target)));
		}
	}

	@ParameterizedTest(name = "{0} with {1}")
	@CsvSource({
			"docker-compose.prod.yml, prod",
			"docker-compose.yml, docker"})
	void givenComposeFile_whenUploadsAreSwappedOnRestore_thenUploadsSitBelowTheVolumeRootThatHoldsTheRecoveryArchives(
			String composeFile, String profile) throws IOException {
		// given
		List<String> mountTargets = mountTargets(appService(Path.of(composeFile)));
		Map<String, Object> config = load(Path.of("src/main/resources/application-" + profile + ".yml"));
		Path uploads = Path.of((String) lookup(config, "app.upload-dir"));
		Path importBackups = Path.of((String) lookup(config, "app.backup.import-backups-dir"));

		// when
		Path uploadsVolume = mountTargets.stream()
				.map(Path::of)
				.filter(uploads::startsWith)
				.findFirst()
				.orElseThrow();

		// then
		assertThat(uploads)
				.as("the restore renames %s, so it must not be the mount root itself", uploads)
				.isNotEqualTo(uploadsVolume);
		assertThat(importBackups.startsWith(uploadsVolume))
				.as("%s must lie in the uploads volume %s so the swap stays a same-filesystem rename",
						importBackups, uploadsVolume)
				.isTrue();
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource({"prod", "docker"})
	void givenContainerProfile_whenUploadsAreServedOverHttp_thenNoBackupDirectoryLiesInsideThem(String profile)
			throws IOException {
		// given
		Map<String, Object> config = load(Path.of("src/main/resources/application-" + profile + ".yml"));
		Path uploads = Path.of((String) lookup(config, "app.upload-dir"));

		// when / then
		for (String property : List.of("app.backup.staging-dir", "app.backup.import-backups-dir", "ctc.site.output-dir")) {
			Path path = Path.of((String) lookup(config, property));
			assertThat(path.startsWith(uploads))
					.as("%s=%s would be downloadable through /uploads/**", property, path)
					.isFalse();
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> appService(Path composeFile) throws IOException {
		Map<String, Object> services = (Map<String, Object>) load(composeFile).get("services");
		return (Map<String, Object>) services.get("app");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> environment(Map<String, Object> app) {
		return (Map<String, Object>) app.get("environment");
	}

	@SuppressWarnings("unchecked")
	private static List<String> mountTargets(Map<String, Object> app) {
		return ((List<String>) app.get("volumes")).stream()
				.map(volume -> volume.split(":")[1])
				.toList();
	}

	@SuppressWarnings("unchecked")
	private static Object lookup(Map<String, Object> config, String dottedKey) {
		Object node = config;
		for (String key : dottedKey.split("\\.")) {
			if (!(node instanceof Map)) {
				return null;
			}
			node = ((Map<String, Object>) node).get(key);
		}
		return node;
	}

	private static Map<String, Object> load(Path file) throws IOException {
		try (Reader reader = Files.newBufferedReader(file)) {
			return new Yaml().load(reader);
		}
	}
}
