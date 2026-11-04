package org.ctc.domain.service;

import java.util.UUID;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.IncidentThreadPublicationRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

abstract class IncidentThreadDispatchFixture extends IncidentDeliveryDispatchFixture {
    @Autowired IncidentThreadPublicationService publication;
    @Autowired IncidentThreadPublicationRepository threadRows;
    @MockitoBean IncidentThreadSender threadSender;
    String oldParent,oldStaff,oldTags;
    @BeforeEach void destination() {
        tx.executeWithoutResult(status -> {var config=configs.findById(configId).orElseThrow();oldParent=config.getStewardsChannelId();oldStaff=config.getIncidentStaffRoleIds();oldTags=config.getStewardsForumTagIds();config.setStewardsChannelId("600000000000000006");config.setIncidentStaffRoleIds("500000000000000005");config.setStewardsForumTagIds("");});
        when(threadSender.send(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var claim=call.getArgument(0,IncidentThreadClaim.class);assertThat(state(claim.id())).isEqualTo(IncidentDeliveryState.CLAIMED);
            return switch(claim.work().stage()) {
                case PRIVATE_STARTER -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT,"701",null,null);
                case PRIVATE_THREAD -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT,"701","701",null);
                case PUBLIC_STARTER -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT,"801",null,null);
                case PUBLIC_THREAD -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT,"801","801",null);
                case PRIVATE_REFERENCE -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT,"702",null,null);
                default -> throw new AssertionError(claim.work().stage());
            };
        });
    }
    @AfterEach void destinationCleanup() {tx.executeWithoutResult(status -> configs.findById(configId).ifPresent(config -> {config.setStewardsChannelId(oldParent);config.setIncidentStaffRoleIds(oldStaff);config.setStewardsForumTagIds(oldTags);}));}
    UUID disclosure() {
        receipt();clock.set(NOW.plusSeconds(3600));disclosure.freezeDueWindow(windowId);
        return tx.execute(status -> deliveryRows.findByReportWindowId(windowId).stream().filter(row -> row.getOperation()==IncidentDeliveryOperation.DISCLOSURE).findFirst().orElseThrow().getId());
    }
    IncidentThreadStage stage(UUID id) {return tx.execute(status -> threadRows.findByDeliveryId(id).orElseThrow().stage());}
}
