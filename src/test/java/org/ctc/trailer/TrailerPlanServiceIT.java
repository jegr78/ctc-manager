package org.ctc.trailer;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import org.ctc.TestHelper;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.CarImage;
import org.ctc.domain.model.CarImageKind;
import org.ctc.domain.model.ImageSource;
import org.ctc.domain.model.MusicTrack;
import org.ctc.domain.model.Track;
import org.ctc.domain.model.TrackImage;
import org.ctc.domain.model.TrackImageKind;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class TrailerPlanServiceIT {
    @Autowired TrailerPlanService plans;
    @Autowired TestHelper helper;
    @Autowired EntityManager entities;

    @Test
    void givenTestSeasonMatchdayWithFinishedPairing_whenPlan_thenNoBlockersAndOneScene() {
        // given
        var fixture = helper.createFullSeasonFixture("Test_Trailer");
        var track = new Track("Test_Trailer Spa");
        track.setDisplayShortName("SPA");
        track.setImageUrl("/uploads/tracks/test/logo.png");
        entities.persist(track);
        image(track, TrackImageKind.BACKGROUND);
        image(track, TrackImageKind.LAYOUT);
        var car = new Car("Test", "Test_Trailer GT-R");
        entities.persist(car);
        var studio = new CarImage();
        studio.setCar(car); studio.setKind(CarImageKind.STUDIO); studio.setSource(ImageSource.MANUAL);
        studio.setImagePath("/uploads/car-images/test/studio.jpg"); studio.setWidth(1920); studio.setHeight(1080);
        entities.persist(studio);
        var action = new CarImage();
        action.setCar(car); action.setKind(CarImageKind.SCENE); action.setSource(ImageSource.MANUAL);
        action.setImagePath("/uploads/car-images/test/scene.jpg"); action.setWidth(1920); action.setHeight(1080);
        entities.persist(action);
        var music = new MusicTrack();
        music.setTitle("Test_Trailer Music"); music.setArtist("Test"); music.setFilePath("/uploads/music/test.mp3"); music.setDurationSeconds(90);
        entities.persist(music);
        var race = fixture.race();
        race.setTrack(track);
        race.setCar(car);
        race.setSettings(helper.completeRaceSettings(race));
        fixture.match().setHomeScore(10);
        fixture.match().setAwayScore(8);
        entities.flush();
        entities.clear();

        // when
        var plan = plans.plan(new TrailerScope.MatchdayScope(fixture.matchday().getId()));

        // then
        assertThat(plan.blockers()).isEmpty();
        assertThat(plan.races()).singleElement().satisfies(scene -> {
            assertThat(scene.trackShortName()).isEqualTo("SPA");
            assertThat(scene.carName()).isEqualTo("Test_Trailer GT-R");
            assertThat(scene.legNumbers()).containsExactly(1);
        });
        assertThat(plan.carsNeedingCutout()).containsExactly(car.getId());
        assertThat(plan.title()).isEqualTo("Test_Trailer MD1");
    }

    private void image(Track track, TrackImageKind kind) {
        var image = new TrackImage();
        image.setTrack(track); image.setKind(kind); image.setSource(ImageSource.MANUAL);
        image.setImagePath("/uploads/track-images/test/" + kind + ".jpg"); image.setWidth(1920); image.setHeight(1080);
        entities.persist(image);
    }
}
