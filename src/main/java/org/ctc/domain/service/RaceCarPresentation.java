package org.ctc.domain.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.Race;

/** Saved one-make vehicle and optional frozen entry context, without eligibility calculations. */
public record RaceCarPresentation(String carName, String poolEntryLabel, String lobbyCategory) {
    public static RaceCarPresentation from(Race race) {
        return new RaceCarPresentation(race.getCar() == null ? null : race.getCar().getDisplayName(),
                race.getCarPoolEntry() == null ? null : race.getCarPoolEntry().getLabel(), lobbyCategory(race.getCar()));
    }

    public static String lobbyCategory(Car car) {
        if (car == null || car.getCategory() == null) return "—";
        return switch (car.getCategory()) {
            case GR_1, GR_2, GR_3, GR_4, GR_B -> car.getCategory().getLabel();
            case GR_N, GR_X -> "—";
        };
    }

    public static Map<UUID, RaceCarPresentation> forRaces(Collection<Race> races) {
        return races.stream().collect(Collectors.toMap(Race::getId, RaceCarPresentation::from));
    }
}
