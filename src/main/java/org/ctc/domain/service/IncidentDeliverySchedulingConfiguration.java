package org.ctc.domain.service;

import org.ctc.backup.lock.ImportLockService;
import org.ctc.integration.OutboundPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods=false)
@EnableScheduling
@ConditionalOnProperty(name="app.discord.incidents.delivery-enabled",havingValue="true")
public class IncidentDeliverySchedulingConfiguration {
    @Bean
    public IncidentDueWorkWorker incidentDueWorkWorker(IncidentDeliveryService delivery,IncidentDisclosureService disclosure,ImportLockService imports,OutboundPolicy outbound) {
        return new IncidentDueWorkWorker(delivery,disclosure,imports,outbound);
    }
}
