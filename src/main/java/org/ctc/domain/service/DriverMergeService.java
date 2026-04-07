package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.PsnAlias;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.PsnAliasRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverMergeService {

    private final DriverRepository driverRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final RaceLineupRepository raceLineupRepository;
    private final RaceResultRepository raceResultRepository;
    private final PsnAliasRepository psnAliasRepository;

    public record MergeResult(int seasonDrivers, int raceLineups, int raceResults, int aliasesReassigned,
                               int seasonDriversDropped, int raceLineupsDropped, int raceResultsDropped) {}

    @Transactional
    public MergeResult merge(UUID sourceId, UUID targetId) {
        // D-03: Self-merge prevention
        if (sourceId.equals(targetId)) {
            throw new BusinessRuleException("Cannot merge driver with itself");
        }

        // D-04: Entity existence validation
        var source = driverRepository.findById(sourceId)
                .orElseThrow(() -> new EntityNotFoundException("Driver", sourceId));
        var target = driverRepository.findById(targetId)
                .orElseThrow(() -> new EntityNotFoundException("Driver", targetId));

        // MERGE-05: Reassign SeasonDriver entries
        var seasonDrivers = seasonDriverRepository.findByDriverId(sourceId);
        for (var sd : seasonDrivers) {
            sd.setDriver(target);
            seasonDriverRepository.save(sd);
        }

        // MERGE-06: Reassign RaceLineup entries
        var raceLineups = raceLineupRepository.findByDriverId(sourceId);
        for (var rl : raceLineups) {
            rl.setDriver(target);
            raceLineupRepository.save(rl);
        }

        // MERGE-07: Reassign RaceResult entries
        var raceResults = raceResultRepository.findByDriverId(sourceId);
        for (var rr : raceResults) {
            rr.setDriver(target);
            raceResultRepository.save(rr);
        }

        // MERGE-08: Reassign PsnAlias entries via repository (D-08: NOT via Driver.aliases collection)
        var aliases = psnAliasRepository.findByDriverId(sourceId);
        for (var alias : aliases) {
            alias.setDriver(target);
            psnAliasRepository.save(alias);
        }

        // MERGE-09 + D-06 + D-07: Transfer source PSN-ID as alias on target (idempotent)
        int psnIdCreated = 0;
        String sourcePsnId = source.getPsnId();
        if (psnAliasRepository.existsByAliasIgnoreCase(sourcePsnId)) {
            log.info("PSN alias '{}' already exists, skipping during merge of driver [{}] into [{}]",
                    sourcePsnId, sourceId, targetId);
        } else {
            psnAliasRepository.save(new PsnAlias(target, sourcePsnId));
            psnIdCreated = 1;
        }

        // MERGE-10: Delete source driver (safe — all FKs reassigned)
        driverRepository.delete(source);

        var result = new MergeResult(
                seasonDrivers.size(),
                raceLineups.size(),
                raceResults.size(),
                aliases.size() + psnIdCreated,
                0,
                0,
                0
        );

        // MERGE-14 + D-10: Audit logging with structured parameters
        log.info("Driver merge complete: source=[{}] '{}', target=[{}] '{}', " +
                        "seasonDrivers={} (dropped={}), raceLineups={} (dropped={}), " +
                        "raceResults={} (dropped={}), aliases={}",
                source.getId(), source.getPsnId(),
                target.getId(), target.getPsnId(),
                result.seasonDrivers(), result.seasonDriversDropped(),
                result.raceLineups(), result.raceLineupsDropped(),
                result.raceResults(), result.raceResultsDropped(),
                result.aliasesReassigned());

        return result;
    }
}
