package org.ctc.backup.schema;

import jakarta.persistence.OneToOne;
import jakarta.persistence.metamodel.Attribute.PersistentAttributeType;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kahn's-algorithm topo-sort over JPA {@link EntityType}. Dependency edge:
 * for every owning-side {@code @ManyToOne}/{@code @OneToOne} (mappedBy null), record
 * {@code dependency -> owner}. Self-FK edges (e.g. {@code Team.parentTeam}) are
 * detected and excluded so Kahn's queue does not deadlock at depth 0.
 *
 * <p>Extracted from {@link BackupSchema} (D-05 / Claude's discretion) to enable pure
 * unit testing without a Spring context. Package-private — only {@code BackupSchema}
 * (same package) may inject it.
 */
@Component
class EntityTopoSorter {

    List<EntityRef> sort(List<EntityType<?>> entityTypes) {
        Map<Class<?>, Set<Class<?>>> outgoing = new HashMap<>();   // dependency -> {owners}
        Map<Class<?>, Integer> inDegree = new HashMap<>();
        Map<Class<?>, EntityType<?>> byClass = entityTypes.stream()
                .collect(Collectors.toMap(EntityType::getJavaType, et -> et));

        for (EntityType<?> et : entityTypes) {
            outgoing.putIfAbsent(et.getJavaType(), new HashSet<>());
            inDegree.putIfAbsent(et.getJavaType(), 0);
        }
        for (EntityType<?> owner : entityTypes) {
            for (SingularAttribute<?, ?> attr : owner.getSingularAttributes()) {
                var type = attr.getPersistentAttributeType();
                if (type != PersistentAttributeType.MANY_TO_ONE
                        && type != PersistentAttributeType.ONE_TO_ONE) {
                    continue;
                }
                // @ManyToOne is always owning. @OneToOne can be either side: the inverse side
                // also surfaces as a SingularAttribute. We must skip the inverse side, or both
                // ends record an edge and Kahn deadlocks (e.g. Race <-> RaceSettings).
                if (type == PersistentAttributeType.ONE_TO_ONE && isInverseOneToOne(attr)) {
                    continue;
                }
                Class<?> depClass = attr.getJavaType();
                if (!byClass.containsKey(depClass)) {
                    continue;                                       // FK to non-domain entity — skip
                }
                Class<?> ownerClass = owner.getJavaType();
                if (depClass.equals(ownerClass)) continue;        // self-FK (Team.parentTeam) — skip
                if (outgoing.get(depClass).add(ownerClass)) {       // dedupe duplicate edges
                    inDegree.merge(ownerClass, 1, Integer::sum);
                }
            }
        }

        Deque<Class<?>> queue = inDegree.entrySet().stream()
                .filter(e -> e.getValue() == 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayDeque::new));
        List<EntityRef> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            Class<?> cls = queue.poll();
            result.add(EntityRef.fromEntityType(byClass.get(cls)));
            for (Class<?> owner : outgoing.get(cls)) {
                if (inDegree.merge(owner, -1, Integer::sum) == 0) {
                    queue.offer(owner);
                }
            }
        }
        if (result.size() != entityTypes.size()) {
            throw new IllegalStateException(
                    "Topo-sort produced " + result.size() + " entries, expected " + entityTypes.size()
                            + " — likely an unexpected cycle outside the known Team.parentTeam self-FK");
        }
        return result;
    }

    /**
     * Returns {@code true} if the given {@link SingularAttribute} backs a
     * {@code @OneToOne(mappedBy=...)} field (the inverse side of a one-to-one
     * association). The JPA Metamodel does not expose {@code mappedBy} directly on
     * {@code SingularAttribute}, so we look it up via reflection on the underlying
     * {@link Field} (or accessor {@link Member}).
     *
     * <p>If the attribute does not back a reflectable {@code @OneToOne} annotation, we
     * treat it as the owning side ({@code false}) — safe default, matches the
     * legacy {@code @ManyToOne} behaviour where every attribute is the owning side.
     */
    private static boolean isInverseOneToOne(SingularAttribute<?, ?> attr) {
        Member member = attr.getJavaMember();
        if (!(member instanceof Field field)) {
            return false;
        }
        OneToOne anno = field.getAnnotation(OneToOne.class);
        return anno != null && !anno.mappedBy().isEmpty();
    }
}
