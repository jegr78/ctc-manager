package org.ctc.discord.incidents;

import java.time.Clock;
import org.ctc.domain.service.PickSessionEpoch;
import org.ctc.domain.incidents.IncidentReportAccess;
import org.ctc.domain.service.IncidentAccessService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IncidentGatewayConfiguration {
    @Bean
    public IncidentReportAccess incidentReportAccess(IncidentAccessService access) { return access::requireCurrent; }

    @Bean
    public IncidentDraftSessionStore incidentDraftSessionStore(Clock clock, PickSessionEpoch epoch) { return new IncidentDraftSessionStore(clock, epoch, 512); }
}
