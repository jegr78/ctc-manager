package org.ctc.domain.service;

import java.util.*;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.IncidentDeliveryRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

abstract class IncidentDeliveryDispatchFixture extends IncidentIntegrationFixture {
    @Autowired IncidentDeliveryService delivery;
    @Autowired IncidentDeliveryRepository deliveryRows;
    @Autowired org.ctc.domain.repository.IncidentRulingPublicationRepository rulingPublications;
    @Autowired IncidentDisclosureService disclosure;
    @Autowired PickSessionEpoch epoch;
    @Autowired ImportLockService imports;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean IncidentReceiptSender sender;
    @BeforeEach void openWindow() { windows.open(contextId,NOW.plusSeconds(3600),0,manager); when(sender.send(any())).thenReturn(new IncidentSendResult(IncidentSendResult.Disposition.SENT,"500",null)); }
    UUID receipt() { var saved=reports.submit(windowId,fixture.race().getId(),1,UUID.randomUUID(),actor,payload); return tx.execute(status -> deliveryRows.findByOperationKey(saved.reportId()+":RECEIPT").orElseThrow().getId()); }
    IncidentDeliveryState state(UUID id) { return tx.execute(status -> deliveryRows.findById(id).orElseThrow().getState()); }

}
