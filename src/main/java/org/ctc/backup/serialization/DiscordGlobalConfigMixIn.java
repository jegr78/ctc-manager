package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.ctc.discord.model.DiscordGlobalConfig;

/**
 * Externalised Jackson annotations for {@link DiscordGlobalConfig}.
 *
 * <p>Minimal MixIn: no nested {@code @ManyToOne} associations, no computed convenience
 * getters. All scalar columns (data + audit) serialise as plain camelCase fields via
 * Lombok-generated getters.
 *
 * <p>Stance on {@code id}: preserved verbatim in the JSON and re-applied by
 * {@code DiscordGlobalConfigRestorer} via {@code row.get("id").asLong()}. This keeps
 * referential identity stable across a round-trip; the IDENTITY-sequence bump after a
 * MariaDB restore is the responsibility of the restorer, not the MixIn.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class DiscordGlobalConfigMixIn {
}
