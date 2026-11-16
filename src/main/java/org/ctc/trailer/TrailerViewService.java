package org.ctc.trailer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.Playoff;
import org.ctc.domain.service.MusicLibraryService;
import org.ctc.domain.service.PlayoffService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Prepares the trailer section for matchday and playoff pages. */
@Service
@RequiredArgsConstructor
public class TrailerViewService {

    private final TrailerPlanService plans;
    private final TrailerService trailers;
    private final MusicLibraryService music;
    private final PlayoffService playoffs;

    public TrailerView view(TrailerScope scope) {
        var plan = plans.plan(scope);
        var status = trailers.status(scope);
        boolean running = status.map(s -> s.running()).orElse(false);
        return new TrailerView(basePath(scope), !plan.races().isEmpty(), plan.blockers(), music.list(), status, trailers.latest(scope),
                plans.estimatedLength(plan), plan.ready() && !running);
    }

    /** Trailer sections keyed by round ID, in round order. */
    public Map<UUID, TrailerView> forPlayoff(Playoff playoff) {
        var views = new LinkedHashMap<UUID, TrailerView>();
        for (var round : playoff.getRounds()) {
            views.put(round.getId(), view(new TrailerScope.PlayoffRoundScope(round.getId())));
        }
        return views;
    }

    @Transactional(readOnly = true)
    public UUID playoffIdForRound(UUID roundId) {
        return playoffs.findRoundById(roundId).getPlayoff().getId();
    }

    public static String basePath(TrailerScope scope) {
        return switch (scope) {
            case TrailerScope.MatchdayScope matchday -> "/admin/matchdays/" + matchday.matchdayId() + "/trailer";
            case TrailerScope.PlayoffRoundScope round -> "/admin/playoffs/round/" + round.roundId() + "/trailer";
        };
    }
}
