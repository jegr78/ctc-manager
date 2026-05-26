package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.discord.model.DiscordGlobalConfig;

/**
 * Externalised Jackson annotations for {@link DiscordGlobalConfig}.
 *
 * <p>Minimal MixIn: no nested {@code @ManyToOne} associations, no computed convenience
 * getters. All scalar columns (10 data + 2 audit) serialise as plain camelCase fields
 * via Lombok-generated getters.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class DiscordGlobalConfigMixIn {
}
