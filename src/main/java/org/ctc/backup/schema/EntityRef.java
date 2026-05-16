package org.ctc.backup.schema;

import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;

/**
 * Per-entity reference: maps a JPA entity class to its wire-format file name.
 *
 * <p>fileName derivation: {@code season_phases} table -&gt; {@code data/season-phases.json}
 * (snake_case -&gt; kebab-case, prefix {@code data/}, suffix {@code .json}).
 */
public record EntityRef(
        Class<?> entityClass,
        String tableName,
        String fileName
) {
    /**
     * Builds an {@link EntityRef} from a JPA {@link EntityType}.
     *
     * <p>The {@code tableName} is read from the {@link Table @Table(name=...)} annotation if
     * present, otherwise falls back to {@code et.getName().toLowerCase()}. The {@code fileName}
     * is always derived from {@code tableName} via snake_case to kebab-case conversion.
     *
     * @param et the JPA entity type to describe
     * @return a fully populated {@code EntityRef}
     */
    public static EntityRef fromEntityType(EntityType<?> et) {
        Table tableAnno = et.getJavaType().getAnnotation(Table.class);
        String table = (tableAnno != null && !tableAnno.name().isBlank())
                ? tableAnno.name()
                : et.getName().toLowerCase();
        String file = "data/" + table.replace('_', '-') + ".json";
        return new EntityRef(et.getJavaType(), table, file);
    }
}
