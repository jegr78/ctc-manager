package org.ctc.backup.repository;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.support.Repositories;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Phase 73-02 — proves that every entity in {@link BackupSchema#getExportOrder()} has
 * a corresponding {@code JpaRepository} bean whose {@code findAllForBackup()} method
 * (a) executes against the dev-data-seeded fixture without throwing
 * {@code LazyInitializationException} when each declared {@code @EntityGraph}
 * attribute is dereferenced AFTER the repository-managed transaction has closed, and
 * (b) carries an {@code @EntityGraph} annotation whose {@code attributePaths} list is
 * non-empty for every non-leaf entity.
 *
 * <p>This is the runtime contract for EXPORT-05 — the repository half of
 * {@code BackupExportService}'s {@code @Transactional(readOnly=true) + @EntityGraph}
 * eager-fetch promise.
 *
 * <p>The test class deliberately is NOT {@code @Transactional}: a class-level
 * {@code @Transactional} would extend the Hibernate session across the entire test
 * method, masking lazy-init bugs (the {@code @EntityGraph} could be removed and the
 * test would still pass). Without it, each {@code findAllForBackup()} runs in its own
 * Spring-Data-generated transaction scope which closes when the method returns;
 * subsequent getter calls on lazy associations would fire
 * {@code LazyInitializationException} unless the {@code @EntityGraph} eager-fetched
 * them.
 *
 * <p>Gated to the {@code dev} profile so the H2 fixture seeded by
 * {@code DevDataSeeder} provides at least one row per entity for the lazy-init probes.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupRepositoryEntityGraphIT {

	/**
	 * Entities WITHOUT any {@code @ManyToOne} (or owning {@code @OneToOne}) association
	 * the export aggregate touches. Their {@code findAllForBackup()} legally carries an
	 * empty {@code @EntityGraph}; the size-check in method 2 skips them, and the
	 * lazy-init probe in method 1 has no attributes to dereference.
	 */
	private static final Set<String> ZERO_ASSOCIATION_ENTITIES = Set.of(
			"Car", "Track", "RaceScoring", "MatchScoring", "Driver",
			"DiscordGlobalConfig", "DiscordPost"
	);

	@Autowired
	private BackupSchema backupSchema;

	@Autowired
	private ListableBeanFactory beanFactory;

	private Repositories repositories;

	@BeforeEach
	void setUp() {
		this.repositories = new Repositories(beanFactory);
	}

	@Test
	void givenAllExportOrderEntities_whenInvokeFindAllForBackup_thenNoLazyInitExceptionOnDeclaredAttributePaths() {
		// given
		List<EntityRef> order = backupSchema.getExportOrder();
		assertThat(order).as("export order must contain 26 entities").hasSize(26);

		Set<String> missingRepositories = new HashSet<>();
		Set<String> missingFinder = new HashSet<>();

		// when / then — for every entity, the repository bean must expose a working
		// findAllForBackup() and the declared attributePaths must be eager-fetched
		for (EntityRef ref : order) {
			Object repository = repositories.getRepositoryFor(ref.entityClass()).orElse(null);
			if (repository == null) {
				missingRepositories.add(ref.entityClass().getSimpleName());
				continue;
			}

			Method finderOnProxy = findFinderMethod(repository.getClass());
			if (finderOnProxy == null) {
				missingFinder.add(ref.entityClass().getSimpleName());
				continue;
			}

			List<?> rows;
			try {
				rows = (List<?>) finderOnProxy.invoke(repository);
			} catch (ReflectiveOperationException ex) {
				Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
				fail("findAllForBackup() on %s threw %s: %s",
						ref.entityClass().getSimpleName(),
						cause.getClass().getName(),
						cause.getMessage());
				continue;
			}
			assertThat(rows)
					.as("findAllForBackup() on %s must return a non-null List",
							ref.entityClass().getSimpleName())
					.isNotNull();

			// Probe each row's eager-fetched associations — accesses run OUTSIDE the
			// repository's transaction scope, so a missing @EntityGraph attributePath
			// would surface as LazyInitializationException here.
			Method finderOnInterface = findFinderMethodOnInterface(ref.entityClass());
			assertThat(finderOnInterface)
					.as("Repository interface for %s must declare findAllForBackup()",
							ref.entityClass().getSimpleName())
					.isNotNull();
			EntityGraph entityGraph = finderOnInterface.getAnnotation(EntityGraph.class);
			assertThat(entityGraph)
					.as("findAllForBackup() on %s repository must carry @EntityGraph",
							ref.entityClass().getSimpleName())
					.isNotNull();

			for (String attributePath : entityGraph.attributePaths()) {
				for (Object row : rows) {
					try {
						Object value = readField(row, attributePath);
						// Force materialisation: calling toString() on a Hibernate proxy
						// triggers the underlying load if the proxy is not yet initialised.
						if (value != null) {
							value.toString();
						}
					} catch (RuntimeException ex) {
						// org.hibernate.LazyInitializationException or any other init failure
						fail("Access to '%s' on %s row triggered %s: %s",
								attributePath,
								ref.entityClass().getSimpleName(),
								ex.getClass().getName(),
								ex.getMessage());
					}
				}
			}
		}

		assertThat(missingRepositories)
				.as("every export-order entity must have a Spring Data repository bean")
				.isEmpty();
		assertThat(missingFinder)
				.as("every repository bean must expose findAllForBackup()")
				.isEmpty();
	}

	@Test
	void givenAllExportOrderEntitiesWithFks_whenInspectFindAllForBackupAnnotation_thenEntityGraphHasNonEmptyAttributePaths() {
		// given
		List<EntityRef> order = backupSchema.getExportOrder();

		// when / then — every non-leaf entity must carry non-empty @EntityGraph.attributePaths
		for (EntityRef ref : order) {
			String entityName = ref.entityClass().getSimpleName();
			if (ZERO_ASSOCIATION_ENTITIES.contains(entityName)) {
				continue;
			}

			Method finder = findFinderMethodOnInterface(ref.entityClass());
			assertThat(finder)
					.as("Repository for %s must declare findAllForBackup()", entityName)
					.isNotNull();

			EntityGraph entityGraph = finder.getAnnotation(EntityGraph.class);
			assertThat(entityGraph)
					.as("findAllForBackup() on %s repository must carry @EntityGraph", entityName)
					.isNotNull();
			assertThat(entityGraph.attributePaths())
					.as("findAllForBackup() on %s repository must declare at least one attributePath "
							+ "(Phase 73-01 MixIn renders the @ManyToOne associations as ID references)",
							entityName)
					.isNotEmpty();
		}
	}

	/**
	 * Locates {@code findAllForBackup()} on the repository proxy class. Spring Data JPA
	 * proxies expose the interface method via {@code getMethod(...)} without parameters.
	 *
	 * @return the {@link Method}, or {@code null} if the repository does not declare it
	 */
	private Method findFinderMethod(Class<?> repositoryProxyClass) {
		try {
			return repositoryProxyClass.getMethod("findAllForBackup");
		} catch (NoSuchMethodException ex) {
			return null;
		}
	}

	/**
	 * Locates {@code findAllForBackup()} on the repository INTERFACE for the given entity.
	 * The interface lookup is needed to read the {@code @EntityGraph} annotation, which
	 * Spring Data does not propagate to the proxy class.
	 *
	 * @return the {@link Method}, or {@code null} if no repository interface is found
	 */
	private Method findFinderMethodOnInterface(Class<?> entityClass) {
		Class<?> repositoryInterface = repositories.getRepositoryInformationFor(entityClass)
				.map(info -> info.getRepositoryInterface())
				.orElse(null);
		if (repositoryInterface == null) {
			return null;
		}
		try {
			return repositoryInterface.getMethod("findAllForBackup");
		} catch (NoSuchMethodException ex) {
			return null;
		}
	}

	/**
	 * Reflectively reads {@code fieldName} on {@code instance}. Tries the conventional
	 * Lombok-generated getter first ({@code get<Field>}); if no public getter exists
	 * (e.g., entities that use {@code @Getter(AccessLevel.NONE)} on internal raw FK
	 * fields), falls back to direct field access via {@link java.lang.reflect.Field}.
	 *
	 * <p>The fallback is essential for {@code Race.homeTeamOverride} /
	 * {@code Race.awayTeamOverride} — those fields are exposed only through the
	 * convenience methods {@code getHomeTeam()} / {@code getAwayTeam()}, not via a
	 * direct getter.
	 */
	private Object readField(Object instance, String fieldName) {
		String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
		// 1) Try the conventional getter.
		try {
			Method getter = instance.getClass().getMethod(getterName);
			return getter.invoke(instance);
		} catch (NoSuchMethodException ignored) {
			// Fall through to reflective field access.
		} catch (ReflectiveOperationException ex) {
			Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
			throw cause instanceof RuntimeException re ? re : new RuntimeException(cause);
		}
		// 2) Fallback — locate the declared field on the entity class (or its superclasses).
		Class<?> clazz = instance.getClass();
		while (clazz != null && clazz != Object.class) {
			try {
				java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
				field.setAccessible(true);
				return field.get(instance);
			} catch (NoSuchFieldException ignored) {
				clazz = clazz.getSuperclass();
			} catch (ReflectiveOperationException ex) {
				Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
				throw cause instanceof RuntimeException re ? re : new RuntimeException(cause);
			}
		}
		fail("Entity %s exposes neither getter %s() nor field %s for attributePath '%s'",
				instance.getClass().getSimpleName(), getterName, fieldName, fieldName);
		return null;
	}
}
