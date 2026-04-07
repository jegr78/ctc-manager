package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public record MergeResult(int seasonDrivers, int raceLineups, int raceResults, int aliasesReassigned) {}

    @Transactional
    public MergeResult merge(UUID sourceId, UUID targetId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
