package org.ctc.admin.service;

import org.ctc.domain.model.*;
import org.ctc.domain.service.PhaseTestFixtures;
import java.util.UUID;
import org.ctc.domain.model.TimeProgressionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LobbySettingsGraphicServiceTest {
    @TempDir Path directory;

    @Test
    void givenEqualAndZero_whenRendering_thenTemplateReceivesDistinctValues() throws Exception {
        // given
        var engine = mock(TemplateEngine.class);
        var service = spy(new LobbySettingsGraphicService(engine, directory.toString()));
        doNothing().when(service).renderScreenshot(anyString(), any(Path.class));
        when(engine.process(anyString(), any(Context.class))).thenReturn("<html></html>");
        var race = readyRace();
        // when
        race.getSettings().setTimeProgressionMode(TimeProgressionMode.EQUAL_CONDITIONS);
        race.getSettings().setTimeProgressionMultiplier(null);
        service.generateLobbySettings(race);
        race.getSettings().setTimeProgressionMode(TimeProgressionMode.MULTIPLIER);
        race.getSettings().setTimeProgressionMultiplier(0);
        service.generateLobbySettings(race);
        // then
        var contexts = ArgumentCaptor.forClass(Context.class);
        verify(engine, times(2)).process(anyString(), contexts.capture());
        assertThat(((java.util.Map<?, ?>) contexts.getAllValues().get(0).getVariable("v")).get("equalConditionsMode")).isEqualTo("On");
        assertThat(((java.util.Map<?, ?>) contexts.getAllValues().get(1).getVariable("v")).get("equalConditionsMode")).isEqualTo("Off");
        assertThat(((java.util.Map<?, ?>) contexts.getAllValues().get(0).getVariable("v")).get("timeSpeed")).isEqualTo("Equal Conditions");
        assertThat(((java.util.Map<?, ?>) contexts.getAllValues().get(1).getVariable("v")).get("timeSpeed")).isEqualTo("0x");
    }
    private org.ctc.domain.model.Race readyRace() {
        var season = new Season("Test", 2026, 1);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(PhaseTestFixtures.matchdayInRegularPhase(season, "Test-MD1", 1));
        race.setHomeTeamOverride(new Team("Test Home", "TH"));
        race.setAwayTeamOverride(new Team("Test Away", "TA"));
        race.setTrack(new Track("Test Track"));
        var settings = new RaceSettings(race);
        settings.setNumberOfLaps(5);
        settings.setTyreWearMultiplier(2);
        settings.setFuelConsumptionMultiplier(2);
        settings.setRefuelingSpeed(1);
        settings.setInitialFuel("Full");
        settings.setNumberOfRequiredPitStops(0);
        settings.setWeather("Clear");
        settings.setTimeOfDay("Day");
        settings.setAvailableTyres("RH");
        settings.setMandatoryTyres("RH");
        race.setSettings(settings);
        return race;
    }

}
