package org.ctc.domain.model;

import java.util.Arrays;
import java.util.List;

public enum WeatherPreset {
    S01("Dry, Cloudless and Pleasant"),
    S02("Dry and Pleasant with a Few Clouds"),
    S03("Dry, Cloudy and Pleasant"),
    S04("Dry, Cloudy and Sunny"),
    S05("Misty, Cloudless and Sunny"),
    S06("Humid, Cloudless and Pleasant"),
    S07("Humid and Pleasant with a Few Clouds"),
    S08("Humid, Sunny and Cloudy"),
    S09("Humid and Sunny with Lots of Clouds"),
    S10("Misty and Sunny with Lots of Clouds"),
    S11("Sunny with Alpine Mist"),
    S12("Sunny with Lots of Monsoon Clouds"),
    S13("Sunny with a Few Monsoon Clouds"),
    S14("Sunny with Lots of Monsoon Clouds II (More Cloudy)"),
    S15("Hazy, Cloudless and Pleasant"),
    S16("Hazy and Sunny with a Few Clouds"),
    S17("Hazy, Cloudy and Sunny"),
    S18("Hazy, Cloudy and Sunny II (More Cloudy)"),
    C01("Cloudy and Bright"),
    C02("Cloudy and Warm"),
    C03("Cloudy and Dark"),
    C04("Cloudy and Chilly"),
    C05("Cloudy"),
    C06("Thick Clouds"),
    R01("Light Drizzle"),
    R02("Cloudless with Warm Rain"),
    R03("Light Rain"),
    R04("Cloudy with Rain"),
    R05("Cloudy with Rain II (Blanket Cloud, No Individual Clouds)"),
    R06("Thick Cloud with Rain"),
    R07("Cloudy with Torrential Rain"),
    R08("Thick Cloud with Torrential Rain");
    private final String description;
    WeatherPreset(String description) { this.description = description; }
    public String getDescription() { return description; }
    public static List<WeatherPreset> available(TrackRainCapability rain) {
        return Arrays.stream(values()).filter(preset -> rain == TrackRainCapability.RAIN || !preset.name().startsWith("R")).toList();
    }
}
