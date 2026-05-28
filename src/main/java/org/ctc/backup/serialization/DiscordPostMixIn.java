package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.ctc.discord.model.DiscordPost;

/**
 * Externalised Jackson annotations for {@link DiscordPost}.
 *
 * <p>Minimal MixIn: the five FK fields ({@code matchId}, {@code matchdayId}, {@code raceId},
 * {@code seasonId}, {@code phaseId}) are {@code @Column UUID} — not {@code @ManyToOne}
 * associations — so Jackson emits them as bare UUID strings via Lombok getters; no
 * {@code @JsonIdentityReference} suppression is needed. The {@code postType} enum
 * serialises as its {@code name()} string ({@code @Enumerated(EnumType.STRING)}).
 *
 * <p>The {@code webhookToken} field is preserved verbatim in the JSON — operator-level
 * filesystem access control on the backup ZIP is the protection model (see
 * {@code docs/operations/discord-integration.md}).
 *
 * <p>Stance on {@code id}: preserved verbatim in the JSON and re-applied by
 * {@code DiscordPostRestorer} via {@code row.get("id").asLong()}. This keeps referential
 * identity stable across a round-trip; the IDENTITY-sequence bump after a MariaDB restore
 * is the responsibility of the restorer, not the MixIn.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class DiscordPostMixIn {
}
