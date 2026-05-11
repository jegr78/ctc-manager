package org.ctc.backup.schema;

import jakarta.persistence.metamodel.Attribute.PersistentAttributeType;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.stereotype.Component;

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
                // SingularAttribute covers ManyToOne (always owning) + OneToOne owning side.
                // mappedBy lives on the INVERSE side; by restricting to singular attributes we
                // cover the owning-side without an explicit mappedBy() introspection call.
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
}
