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

    public record MergePreview(
            int seasonDriversToReassign, int seasonDriversDuplicate,
            int raceLineupsToReassign, int raceLineupsDuplicate,
            int raceResultsToReassign, int raceResultsDuplicate,
            int psnAliasesToReassign) {

        public int totalToReassign() {
            return seasonDriversToReassign + raceLineupsToReassign + raceResultsToReassign + psnAliasesToReassign;
        }

        public int totalDuplicates() {
            return seasonDriversDuplicate + raceLineupsDuplicate + raceResultsDuplicate;
        }
    }

    @Transactional(readOnly = true)
    public MergePreview previewMerge(UUID sourceId, UUID targetId) {
        // Self-merge prevention (same as merge())
        if (sourceId.equals(targetId)) {
            throw new BusinessRuleException("Cannot merge driver with itself");
        }

        // Entity existence validation (same as merge())
        driverRepository.findById(sourceId)
                .orElseThrow(() -> new EntityNotFoundException("Driver", sourceId));
        driverRepository.findById(targetId)
                .orElseThrow(() -> new EntityNotFoundException("Driver", targetId));

        // Count SeasonDriver reassign vs duplicate (read-only, no save/delete)
        int sdReassign = 0;
        int sdDuplicate = 0;
        for (var sd : seasonDriverRepository.findByDriverId(sourceId)) {
            if (seasonDriverRepository.findBySeasonIdAndDriverId(sd.getSeason().getId(), targetId).isPresent()) {
                sdDuplicate++;
            } else {
                sdReassign++;
            }
        }

        // Count RaceLineup reassign vs duplicate (read-only, no save/delete)
        int rlReassign = 0;
        int rlDuplicate = 0;
        for (var rl : raceLineupRepository.findByDriverId(sourceId)) {
            if (raceLineupRepository.findByRaceIdAndDriverId(rl.getRace().getId(), targetId).isPresent()) {
                rlDuplicate++;
            } else {
                rlReassign++;
            }
        }

        // Count RaceResult reassign vs duplicate (read-only, no save/delete)
        int rrReassign = 0;
        int rrDuplicate = 0;
        for (var rr : raceResultRepository.findByDriverId(sourceId)) {
            if (raceResultRepository.findByRaceIdAndDriverId(rr.getRace().getId(), targetId).isPresent()) {
                rrDuplicate++;
            } else {
                rrReassign++;
            }
        }

        // Count PsnAlias entries to reassign (read-only)
        int aliasCount = psnAliasRepository.findByDriverId(sourceId).size();

        return new MergePreview(sdReassign, sdDuplicate, rlReassign, rlDuplicate, rrReassign, rrDuplicate, aliasCount);
    }

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

        // MERGE-05 + MERGE-11: Reassign SeasonDriver entries, dropping duplicates (per D-01, D-03)
        var seasonDrivers = seasonDriverRepository.findByDriverId(sourceId);
        int seasonDriversReassigned = 0;
        int seasonDriversDropped = 0;
        for (var sd : seasonDrivers) {
            var conflict = seasonDriverRepository.findBySeasonIdAndDriverId(
                    sd.getSeason().getId(), targetId);
            if (conflict.isPresent()) {
                log.info("Dropping duplicate SeasonDriver for season '{}' during merge of driver [{}] into [{}]",
                        sd.getSeason().getName(), sourceId, targetId);
                seasonDriverRepository.delete(sd);
                seasonDriversDropped++;
            } else {
                sd.setDriver(target);
                seasonDriverRepository.save(sd);
                seasonDriversReassigned++;
            }
        }

        // MERGE-06 + MERGE-12: Reassign RaceLineup entries, dropping duplicates (per D-01, D-03, D-07)
        var raceLineups = raceLineupRepository.findByDriverId(sourceId);
        int raceLineupsReassigned = 0;
        int raceLineupsDropped = 0;
        for (var rl : raceLineups) {
            var conflict = raceLineupRepository.findByRaceIdAndDriverId(
                    rl.getRace().getId(), targetId);
            if (conflict.isPresent()) {
                log.info("Dropping duplicate RaceLineup for race [{}] during merge of driver [{}] into [{}]",
                        rl.getRace().getId(), sourceId, targetId);
                raceLineupRepository.delete(rl);
                raceLineupsDropped++;
            } else {
                rl.setDriver(target);
                raceLineupRepository.save(rl);
                raceLineupsReassigned++;
            }
        }

        // MERGE-07 + MERGE-13: Reassign RaceResult entries, dropping duplicates (per D-01, D-02, D-03)
        var raceResults = raceResultRepository.findByDriverId(sourceId);
        int raceResultsReassigned = 0;
        int raceResultsDropped = 0;
        for (var rr : raceResults) {
            var conflict = raceResultRepository.findByRaceIdAndDriverId(
                    rr.getRace().getId(), targetId);
            if (conflict.isPresent()) {
                log.info("Dropping duplicate RaceResult for race [{}] during merge of driver [{}] into [{}]",
                        rr.getRace().getId(), sourceId, targetId);
                raceResultRepository.delete(rr);
                raceResultsDropped++;
            } else {
                rr.setDriver(target);
                raceResultRepository.save(rr);
                raceResultsReassigned++;
            }
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
                seasonDriversReassigned,
                raceLineupsReassigned,
                raceResultsReassigned,
                aliases.size() + psnIdCreated,
                seasonDriversDropped,
                raceLineupsDropped,
                raceResultsDropped
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
