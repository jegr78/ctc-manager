package org.ctc.domain.service;

import java.util.List;
import org.ctc.domain.model.*;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class IncidentThreadMappingTest {
    IncidentDelivery closedDelivery() {
        var fixture = new IncidentUnitFixture();fixture.openWindow();
        ReflectionTestUtils.setField(fixture.window,"state",IncidentWindowState.CLOSED);
        var report = IncidentUnitFixture.id(new IncidentReport(fixture.window,fixture.race,"900",fixture.party()));
        ReflectionTestUtils.setField(report,"frozenRevisionNumber",1);
        var revision = IncidentUnitFixture.id(new IncidentReportRevision(report,1,1,new IncidentPayload("Test drivers","1:20","Test facts",""),"900",IncidentUnitFixture.NOW,java.util.UUID.randomUUID()));
        return IncidentUnitFixture.id(IncidentDelivery.capture(report,revision,IncidentDeliveryOperation.DISCLOSURE,IncidentUnitFixture.NOW));
    }
    IncidentThreadPublication mapping(int chunks) {
        return assertDoesNotThrow(() -> IncidentThreadPublication.capture(closedDelivery(),"600","500","",chunks));
    }
    @Test void givenATextPairWithSeveralChunks_whenAcknowledgingEachStep_thenRetainEverySuccessfulIdentityInOrder() {
        var row=mapping(3);assertThat(row.stage()).isEqualTo(IncidentThreadStage.PRIVATE_STARTER);
        row.accept(row.stage(),0,"701",null);assertThat(row.getPrivateStarterId()).isEqualTo("701");assertThat(row.stage()).isEqualTo(IncidentThreadStage.PRIVATE_THREAD);
        row.accept(row.stage(),0,"701","701");assertThat(row.getPrivateThreadId()).isEqualTo("701");
        assertThat(row.stage()).isEqualTo(IncidentThreadStage.PRIVATE_BODY);assertThat(row.chunkIndex()).isEqualTo(1);
        row.accept(row.stage(),1,"702",null);row.accept(row.stage(),2,"703",null);assertThat(row.privateMessages()).containsExactly("702","703");
        assertThat(row.stage()).isEqualTo(IncidentThreadStage.PUBLIC_STARTER);assertThat(row.publicComplete()).isFalse();
        row.accept(row.stage(),0,"801",null);row.accept(row.stage(),0,"801","801");assertThat(row.getPublicStarterId()).isEqualTo("801");assertThat(row.getPublicThreadId()).isEqualTo("801");
        row.accept(row.stage(),1,"802",null);assertThat(row.publicComplete()).isFalse();row.accept(row.stage(),2,"803",null);
        assertThat(row.publicMessages()).containsExactly("802","803");assertThat(row.publicComplete()).isTrue();assertThat(row.stage()).isEqualTo(IncidentThreadStage.PRIVATE_REFERENCE);
        row.accept(row.stage(),0,"704",null);assertThat(row.stage()).isEqualTo(IncidentThreadStage.COMPLETE);assertThat(row.getReferenceMessageId()).isEqualTo("704");
        assertThatThrownBy(() -> row.accept(IncidentThreadStage.COMPLETE,0,"705",null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> row.privateMessages().add("799")).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void givenAForumCreation_whenItsStarterAndThreadArriveTogether_thenPersistBothAndContinueWithoutCreatingAnotherThread() {
        var row=mapping(1);row.accept(row.stage(),0,"701","700");assertThat(row.getPrivateStarterId()).isEqualTo("701");assertThat(row.getPrivateThreadId()).isEqualTo("700");assertThat(row.stage()).isEqualTo(IncidentThreadStage.PUBLIC_STARTER);
        row.accept(row.stage(),0,"801",null);row.accept(row.stage(),0,"801","801");assertThat(row.stage()).isEqualTo(IncidentThreadStage.PRIVATE_REFERENCE);
    }
    @ParameterizedTest @ValueSource(strings={"stage","index","message","thread","same-parent","staff","tags","chunks","receipt"})
    void givenContradictoryCaptureOrAcknowledgement_whenUpdatingTheMapping_thenRejectWithoutAdvancing(String cause) {
        var delivery=closedDelivery();
        if(cause.equals("receipt"))ReflectionTestUtils.setField(delivery,"operation",IncidentDeliveryOperation.RECEIPT);
        if(List.of("same-parent","staff","tags","chunks","receipt").contains(cause)) {
            assertThatThrownBy(() -> IncidentThreadPublication.capture(delivery,cause.equals("same-parent")?delivery.getChannelId():"600",cause.equals("staff")?"":"500",cause.equals("tags")?"900,900":"",cause.equals("chunks")?0:1)).isInstanceOf(BusinessRuleException.class);return;
        }
        var row=mapping(1);
        assertThatThrownBy(() -> row.accept(cause.equals("stage")?IncidentThreadStage.PUBLIC_STARTER:row.stage(),cause.equals("index")?1:0,cause.equals("message")?"not-id":"701",cause.equals("thread")?"not-id":null)).isInstanceOf(BusinessRuleException.class);
        assertThat(row.stage()).isEqualTo(IncidentThreadStage.PRIVATE_STARTER);assertThat(row.getPrivateStarterId()).isNull();
    }
}
