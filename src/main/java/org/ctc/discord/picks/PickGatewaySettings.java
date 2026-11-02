package org.ctc.discord.picks;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.discord.picks")
@Getter
@Setter
public class PickGatewaySettings {
    private boolean enabled;
    private int workers = 2;
    private int queueCapacity = 32;
}
