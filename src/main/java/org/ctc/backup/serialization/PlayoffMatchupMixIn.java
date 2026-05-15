package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.PlayoffRound;
import org.ctc.domain.model.Team;

/**
 * Externalised Jackson annotations for {@link PlayoffMatchup}.
 *
 * <p>The self-typed {@code nextMatchup} foreign key is rendered as a UUID reference. Intra-file
 * forward reference resolved by Jackson via the same {@code @JsonIdentityInfo} declaration.
 *
 * <p>Suppresses the {@code races} back-reference collection and the two computed booleans
 * ({@code isComplete()} → property {@code complete}, {@code isReady()} → property
 * {@code ready}).
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler",
        "races", "complete", "ready"})
public abstract class PlayoffMatchupMixIn {

    @JsonIdentityReference(alwaysAsId = true)
    abstract PlayoffRound getRound();

    @JsonIdentityReference(alwaysAsId = true)
    abstract Team getTeam1();

    @JsonIdentityReference(alwaysAsId = true)
    abstract Team getTeam2();

    @JsonIdentityReference(alwaysAsId = true)
    abstract Team getWinner();

    @JsonIdentityReference(alwaysAsId = true)
    abstract PlayoffMatchup getNextMatchup();
}
