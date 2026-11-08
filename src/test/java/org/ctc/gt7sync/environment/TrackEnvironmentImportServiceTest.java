package org.ctc.gt7sync.environment;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.service.PickSessionEpoch;
import org.ctc.domain.service.PickWriteExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.Environment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TrackEnvironmentImportServiceTest {
    final TrackRepository tracks=mock(TrackRepository.class);
    final EntityManager entities=mock(EntityManager.class);
    final GtPlanetEnvironmentSource source=mock(GtPlanetEnvironmentSource.class);
    final PickSessionEpoch epoch=mock(PickSessionEpoch.class);
    final PickWriteExecutor writes=mock(PickWriteExecutor.class);
    final Environment env=mock(Environment.class);
    final Principal manager=()->"test-manager";
    final TrackEnvironmentImportStore store=new TrackEnvironmentImportStore(Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"),ZoneOffset.UTC));
    TrackEnvironmentImportService service;
    Track track;
    @BeforeEach void setUp()throws Exception {
        track=new Track("24 Heures du Mans Racing Circuit","France");track.setId(UUID.randomUUID());track.setImageUrl("/uploads/test-owner.png");
        when(epoch.managementGeneration()).thenReturn("process:0");
        when(tracks.findAllByOrderByNameAsc()).thenReturn(List.of(track));
        when(tracks.findByIdForEnvironmentUpdate(track.getId())).thenReturn(Optional.of(track));
        when(writes.execute(any())).thenAnswer(call -> ((Supplier<?>)call.getArgument(0)).get());
        service=new TrackEnvironmentImportService(tracks,entities,new TrackEnvironmentCatalog(),source,store,epoch,writes,env);
    }
    @Test void givenPristineLeMans_whenApplyingReviewedOfflinePreview_thenFillTimesRainAndProvenanceWithoutChangingIdentity()throws Exception {
        // given
        var preview=service.bundled(manager);assertThat(preview.readyCount()).isEqualTo(1);
        assertThat(track.getRainCapability()).isEqualTo("UNKNOWN");
        // when
        assertThat(service.apply(preview.id(),List.of(track.getId()),manager)).isEqualTo(1);
        // then
        assertThat(track.getTimeOfDayOptions()).hasSize(11);assertThat(track.getRainCapability()).isEqualTo("RAIN");assertThat(track.getEnvironmentVersion()).isEqualTo(1);
        assertThat(track.getImageUrl()).isEqualTo("/uploads/test-owner.png");assertThat(track.getCountry()).isEqualTo("France");assertThat(track.isEnvironmentOverride()).isFalse();
        var provenance=TrackEnvironmentProvenance.decode(track.getEnvironmentSource());assertThat(provenance.profile()).contains("24 Heures");
        assertThat(track.getEnvironmentImportHash()).isEqualTo(TrackEnvironmentProvenance.valuesHash(TrackEnvironmentValues.from(track)));
        verify(entities).refresh(track,LockModeType.PESSIMISTIC_WRITE);verifyNoInteractions(source);
        assertThatThrownBy(() -> service.apply(preview.id(),List.of(track.getId()),manager)).isInstanceOf(BusinessRuleException.class);
        assertThat(service.bundled(manager).readyCount()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"manual-unknown","legacy-known","manual-imported","tampered-import","unmapped","negative-version","version-limit"})
    void givenProtectedOrUnqualifiedLayout_whenPreviewing_thenNeverOfferAnOverwrite(String kind)throws Exception {
        // given
        switch(kind) {
            case "manual-unknown" -> track.setEnvironmentOverride(true);
            case "legacy-known" -> {track.setRainCapability("RAIN");track.setEnvironmentVersion(1);}
            case "manual-imported","tampered-import" -> {
                var first=service.bundled(manager);service.apply(first.id(),List.of(track.getId()),manager);
                if(kind.equals("manual-imported"))track.setEnvironmentOverride(true);else track.setTimeOfDayOptions(List.of("Night"));
            }
            case "negative-version" -> track.setEnvironmentVersion(-1);
            case "unmapped" -> track.setName("Nürburgring Endurance II");
            default -> track.setEnvironmentVersion(Long.MAX_VALUE);
        }
        // when
        var preview=service.bundled(manager);
        // then
        assertThat(preview.readyCount()).isZero();
        assertThatThrownBy(() -> service.apply(preview.id(),List.of(track.getId()),manager)).isInstanceOf(BusinessRuleException.class);
    }
    @ParameterizedTest @ValueSource(strings={"rename","manual","raw-value","delete","restore","other-owner","unoffered","duplicates","empty"})
    void givenPreviewChangedOrForgedBeforeConfirmation_whenApplying_thenRejectBeforeAnyImport(String kind)throws Exception {
        // given
        var preview=service.bundled(manager);List<UUID> selected=List.of(track.getId());Principal actor=manager;
        switch(kind) {
            case "rename" -> doAnswer(call -> {track.setName("24 Heures du Mans Racing Circuit No Chicane");return null;}).when(entities).refresh(eq(track),any(LockModeType.class));
            case "manual" -> track.setEnvironmentOverride(true);
            case "raw-value" -> track.setTimeOfDayOptions(List.of("Night"));
            case "delete" -> when(tracks.findByIdForEnvironmentUpdate(track.getId())).thenReturn(Optional.empty());
            case "restore" -> when(epoch.managementGeneration()).thenReturn("process:1");
            case "other-owner" -> actor=()->"other-manager";
            case "unoffered" -> selected=List.of(UUID.randomUUID());
            case "duplicates" -> selected=List.of(track.getId(),track.getId());
            default -> selected=List.of();
        }
        var ids=selected;var owner=actor;
        // when / then
        assertThatThrownBy(() -> service.apply(preview.id(),ids,owner)).isInstanceOf(BusinessRuleException.class);
        assertThat(track.getEnvironmentVersion()).isZero();assertThat(track.getEnvironmentSource()).isNull();
    }
    @Test void givenTwoSelectedLayoutsAndOneStale_whenApplying_thenValidateAllBeforeChangingEither()throws Exception {
        // given
        var other=new Track("24 Heures du Mans Racing Circuit No Chicane");other.setId(UUID.randomUUID());
        when(tracks.findAllByOrderByNameAsc()).thenReturn(List.of(track,other));when(tracks.findByIdForEnvironmentUpdate(other.getId())).thenReturn(Optional.of(other));
        var preview=service.bundled(manager);other.setEnvironmentVersion(1);
        // when / then
        assertThatThrownBy(() -> service.apply(preview.id(),List.of(track.getId(),other.getId()),manager)).isInstanceOf(BusinessRuleException.class);
        assertThat(track.getRainCapability()).isEqualTo("UNKNOWN");assertThat(track.getEnvironmentVersion()).isZero();assertThat(other.getEnvironmentSource()).isNull();
    }
    @Test void givenUntouchedImportedValuesAndNewSourceRevision_whenRefreshing_thenOfferAndApplyTheReviewedUpdate()throws Exception {
        // given
        var first=service.bundled(manager);service.apply(first.id(),List.of(track.getId()),manager);
        var old=first.source();String edited="2026-08-01T00:00:00Z";
        var changed=new GtPlanetEnvironmentSource.Snapshot(edited,old.retrievedAt(),GtPlanetEnvironmentSource.hash(edited,old.profiles()),old.profiles());when(source.fetch()).thenReturn(changed);
        // when
        var preview=service.refresh(manager);assertThat(preview.entries().getFirst().status()).isEqualTo(TrackEnvironmentImportService.Status.UPDATE);
        service.apply(preview.id(),List.of(track.getId()),manager);
        // then
        assertThat(track.getEnvironmentVersion()).isEqualTo(2);assertThat(TrackEnvironmentProvenance.decode(track.getEnvironmentSource()).sourceEditedAt()).isEqualTo(edited);
    }
    @Test void givenDuplicateTrimmedNamesOrMissingActor_whenPreviewing_thenBlockAmbiguityAndRequireManagement()throws Exception {
        // given
        var duplicate=new Track(track.getName()+" ");duplicate.setId(UUID.randomUUID());when(tracks.findAllByOrderByNameAsc()).thenReturn(List.of(track,duplicate));
        // when / then
        assertThat(service.bundled(manager).entries()).allMatch(e -> e.status()==TrackEnvironmentImportService.Status.AMBIGUOUS);
        assertThatThrownBy(() -> service.bundled(null)).isInstanceOf(BusinessRuleException.class);
    }
    @Test void givenRestoreGenerationChangesDuringWriterAdmission_whenApplying_thenRejectBeforeRowMutation()throws Exception {
        var preview=service.bundled(manager);
        doAnswer(call->{when(epoch.managementGeneration()).thenReturn("process:changed");return ((Supplier<?>)call.getArgument(0)).get();}).when(writes).execute(any());
        assertThatThrownBy(()->service.apply(preview.id(),List.of(track.getId()),manager)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("restart or restore");
        assertThat(track.getEnvironmentVersion()).isZero();verify(tracks,never()).findByIdForEnvironmentUpdate(any());
    }
}
