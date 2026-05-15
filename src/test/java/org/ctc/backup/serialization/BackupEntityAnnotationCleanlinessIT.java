package org.ctc.backup.serialization;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reflective entity-cleanliness gate: every entity under {@code org.ctc.domain.model} must
 * remain free of Jackson annotations — all serialization concerns live in the MixIn classes
 * under {@code org.ctc.backup.serialization}.
 *
 * <p>Walks every entity reachable from the JPA Metamodel whose class is in
 * {@code org.ctc.domain.model}, then inspects every declared field and method for
 * annotations whose type's package starts with {@code com.fasterxml.jackson}.
 * The collected violation set must be empty.
 *
 * <p>{@code BaseEntity} is a {@code @MappedSuperclass} not reported by
 * {@code Metamodel.getEntities()} — its fields are not walked here.
 * {@code DataImportAudit} lives in {@code org.ctc.backup.audit} and is filtered by
 * the package predicate.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupEntityAnnotationCleanlinessIT {

    @Autowired
    private EntityManagerFactory emf;

    @Test
    void givenAllOperativeEntityClasses_whenInspectAnnotations_thenZeroJacksonAnnotationsPresent() {
        // given — every entity in the JPA metamodel whose Java class is under org.ctc.domain.model
        List<String> violations = new ArrayList<>();

        emf.getMetamodel().getEntities().stream()
                .map(et -> et.getJavaType())
                .filter(c -> c.getPackageName().startsWith("org.ctc.domain.model"))
                .forEach(entityClass -> collectJacksonViolations(entityClass, violations));

        // then
        assertThat(violations)
                .as("Domain entity classes must not carry any Jackson annotations — "
                        + "EXPORT-04 success criterion 3. All Jackson concerns belong on the "
                        + "Phase 73 MixIn classes under org.ctc.backup.serialization.")
                .isEmpty();
    }

    private static void collectJacksonViolations(Class<?> entityClass, List<String> violations) {
        for (Field field : entityClass.getDeclaredFields()) {
            for (Annotation a : field.getDeclaredAnnotations()) {
                if (isJacksonAnnotation(a)) {
                    violations.add(String.format("%s.%s -> @%s",
                            entityClass.getSimpleName(),
                            field.getName(),
                            a.annotationType().getSimpleName()));
                }
            }
        }
        for (Method method : entityClass.getDeclaredMethods()) {
            for (Annotation a : method.getDeclaredAnnotations()) {
                if (isJacksonAnnotation(a)) {
                    violations.add(String.format("%s.%s() -> @%s",
                            entityClass.getSimpleName(),
                            method.getName(),
                            a.annotationType().getSimpleName()));
                }
            }
        }
    }

    private static boolean isJacksonAnnotation(Annotation a) {
        return a.annotationType().getPackageName().startsWith("com.fasterxml.jackson");
    }
}
