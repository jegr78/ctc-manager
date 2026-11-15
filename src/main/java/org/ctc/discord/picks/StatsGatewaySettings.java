package org.ctc.discord.picks;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Switches the public /stats command on the shared Gateway; picks and incidents keep their own switch. */
@ConfigurationProperties("app.discord.stats")
@Getter
@Setter
public class StatsGatewaySettings {
    private boolean enabled;
}
