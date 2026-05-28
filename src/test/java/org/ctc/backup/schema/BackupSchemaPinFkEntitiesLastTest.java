package org.ctc.backup.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BackupSchemaPinFkEntitiesLastTest {

	private static final EntityRef A = new EntityRef(Alpha.class, "alpha", "data/alpha.json");
	private static final EntityRef B = new EntityRef(Beta.class, "beta", "data/beta.json");
	private static final EntityRef C = new EntityRef(Gamma.class, "gamma", "data/gamma.json");

	@Test
	void givenMultipleFkTailEntities_whenPin_thenAllMoveToTailPreservingNonTailOrder() {
		List<EntityRef> sorted = List.of(A, B, C);
		List<EntityRef> result = BackupSchema.pinFkEntitiesLast(sorted, Set.of(Alpha.class, Gamma.class));

		assertThat(result).extracting(EntityRef::entityClass)
				.containsExactly(Beta.class, Alpha.class, Gamma.class);
	}

	@Test
	void givenNoFkTailMatch_whenPin_thenOrderUnchanged() {
		List<EntityRef> sorted = List.of(A, B, C);
		List<EntityRef> result = BackupSchema.pinFkEntitiesLast(sorted, Set.of());

		assertThat(result).containsExactly(A, B, C);
	}

	private static final class Alpha {}
	private static final class Beta {}
	private static final class Gamma {}
}
