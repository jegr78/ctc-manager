package org.ctc.backup.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

/**
 * Isolates the backup ObjectMapper from the admin REST/AJAX serializer.
 *
 * <p>Spring Boot's {@code JacksonAutoConfiguration} uses
 * {@code @ConditionalOnMissingBean(ObjectMapper.class)}, so defining ANY {@code ObjectMapper}
 * bean (regardless of qualifier) silently disables the auto-configured default — the
 * qualified backup mapper would then steal the default-mapper role for admin REST/AJAX
 * paths and break them.
 *
 * <p>To preserve the auto-config default for admin REST/AJAX AND introduce a strict,
 * separately-configured backup mapper, this config declares BOTH beans explicitly:
 * <ol>
 *   <li>{@link #defaultObjectMapper(Jackson2ObjectMapperBuilder)} — {@code @Primary}, built
 *       from the auto-config-supplied {@code Jackson2ObjectMapperBuilder} (preserves
 *       Spring Boot's default behaviour byte-for-byte).</li>
 *   <li>{@link #backupObjectMapper(List)} — {@code @Qualifier("backupObjectMapper")}, strict
 *       ({@code FAIL_ON_UNKNOWN_PROPERTIES=true} / {@code WRITE_DATES_AS_TIMESTAMPS=false} /
 *       {@code JavaTimeModule} registered).</li>
 * </ol>
 *
 * <p>See Phase 72 RESEARCH §Pitfall P-2 for the verified GitHub-issue trail
 * ({@code spring-projects/spring-boot#47379, #22403, #42598}).
 */
@Configuration
public class BackupObjectMapperConfig {

    /**
     * Reconstructs the {@code JacksonAutoConfiguration} default mapper using the same
     * {@code Jackson2ObjectMapperBuilder} defaults Spring Boot applies. Uses the static
     * {@link Jackson2ObjectMapperBuilder#json()} factory rather than autowiring a builder
     * bean — Spring Boot 4 does NOT expose {@code Jackson2ObjectMapperBuilder} as a bean
     * because it has migrated the default REST stack to Jackson 3 ({@code tools.jackson}),
     * keeping Jackson 2 only as a transitive compatibility layer (RESEARCH §A2 risk
     * materialised in Phase 72 / Plan 03 verification). Marked {@code @Primary} so
     * admin REST/AJAX MVC paths still resolve unqualified {@code @Autowired ObjectMapper}
     * to this bean.
     */
    @Bean
    @Primary
    public ObjectMapper defaultObjectMapper() {
        return Jackson2ObjectMapperBuilder.json().build();
    }

    /**
     * Strict backup-only mapper. Per-entity MixIn {@code @Component} beans are collected via
     * {@code backupMixInModules} (Spring DI injects an empty list when no matching beans exist).
     */
    @Bean
    @Qualifier("backupObjectMapper")
    public ObjectMapper backupObjectMapper(List<Module> backupMixInModules) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.registerModule(new JavaTimeModule());
        backupMixInModules.forEach(mapper::registerModule);
        return mapper;
    }
}
