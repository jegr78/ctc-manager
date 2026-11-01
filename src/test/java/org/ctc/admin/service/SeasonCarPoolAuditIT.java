package org.ctc.admin.service;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.SeasonCarPoolService;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@CtcDevSpringBootContext
@Transactional
@Tag("integration")
class SeasonCarPoolAuditIT {
    @Autowired DataAuditService audit;
    @Autowired SeasonRepository seasons;
    @Autowired CarRepository cars;
    @Autowired SeasonCarPoolEntryRepository entries;
    @Autowired SeasonCarPoolMemberRepository members;
    @Autowired SeasonCarPoolService pools;
    @Autowired EntityManager em;

    @Test
    void givenOverlappingFrozenSnapshots_whenAudited_thenEvidenceIsReportedWithoutRepair() {
        // given
        var season = new Season("Test Inconsistent Pool " + UUID.randomUUID(), 2030, 1);
        season.setCarPoolMode(CarPoolMode.CATEGORY_POOL);
        seasons.saveAndFlush(season);
        var car = cars.saveAndFlush(new Car("Test Audit", "Shared " + UUID.randomUUID()));
        var firstId = pools.saveEntry(season.getId(), null, "First entry", List.of(new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, null, null)));
        var secondId = pools.saveEntry(season.getId(), null, "Second entry", List.of(new SeasonCarPoolService.ClauseInput(CarCategory.GR_4, null, null)));
        for (var id : List.of(firstId, secondId)) {
            var member = new SeasonCarPoolMember();
            member.setEntry(entries.findById(id).orElseThrow());
            member.setCar(car);
            members.saveAndFlush(member);
        }
        season.setCarPoolState(CarPoolState.FROZEN);
        seasons.saveAndFlush(season);
        em.clear();
        long count = members.count();
        // when
        var findings = audit.audit().sections().stream().filter(section -> section.category().name().equals("CAR_POOL"))
                .flatMap(section -> section.findings().stream()).toList();
        // then
        assertThat(findings).anySatisfy(finding -> {
            assertThat(finding.subject()).contains(season.getId().toString());
            assertThat(finding.evidence()).contains("overlap", car.getId().toString(), "First entry", "Second entry");
        });
        assertThat(members.count()).isEqualTo(count);
        assertThat(seasons.findById(season.getId()).orElseThrow().getCarPoolState()).isEqualTo(CarPoolState.FROZEN);
    }
}
