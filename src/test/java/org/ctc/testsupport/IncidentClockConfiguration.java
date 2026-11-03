package org.ctc.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class IncidentClockConfiguration {
    @Bean @Primary public IncidentTestClock incidentTestClock() { return new IncidentTestClock(); }
}
