package org.folio.rtaccache.service.handler.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.folio.rtaccache.domain.dto.Instance;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstanceUpdateEventHandlerTest {

  @InjectMocks
  InstanceUpdateEventHandler handler;

  @Mock
  RtacHoldingRepository holdingRepository;
  @Mock
  RtacHoldingBulkRepository rtacHoldingBulkRepository;
  @Mock
  ResourceEventUtil resourceEventUtil;
  @Mock
  InventoryResourceEvent resourceEvent;

  @Test
  void instanceUpdate_noBulkUpdate_whenNoHoldingsFound() throws Exception {
    var instanceId = UUID.randomUUID();
    var oldInstance = new Instance().id(instanceId.toString()).source("CONSORTIUM-FOLIO");
    var newInstance = new Instance().id(instanceId.toString())
      .source("CONSORTIUM-FOLIO")
      .instanceFormatIds(List.of("fmt-1"));

    when(resourceEventUtil.getOldFromInventoryEvent(resourceEvent, Instance.class)).thenReturn(oldInstance);
    when(resourceEventUtil.getNewFromInventoryEvent(resourceEvent, Instance.class)).thenReturn(newInstance);
    when(holdingRepository.countByIdInstanceId(instanceId)).thenReturn(0);

    handler.handle(resourceEvent);

    verify(holdingRepository).countByIdInstanceId(instanceId);
    verify(rtacHoldingBulkRepository, never()).bulkUpdateInstanceFormatIds(any(Instance.class));
  }

  @Test
  void instanceUpdate_bulkUpdate_whenHoldingsExistAndFormatIdsPresent() throws Exception {
    var instanceId = UUID.randomUUID();
    var oldInstance = new Instance().id(instanceId.toString()).source("CONSORTIUM-FOLIO");
    var newInstance = new Instance().id(instanceId.toString())
      .source("CONSORTIUM-FOLIO")
      .instanceFormatIds(List.of("fmt-1"));

    when(resourceEventUtil.getOldFromInventoryEvent(resourceEvent, Instance.class)).thenReturn(oldInstance);
    when(resourceEventUtil.getNewFromInventoryEvent(resourceEvent, Instance.class)).thenReturn(newInstance);
    when(holdingRepository.countByIdInstanceId(instanceId)).thenReturn(2);

    handler.handle(resourceEvent);

    verify(holdingRepository).countByIdInstanceId(instanceId);
    verify(rtacHoldingBulkRepository).bulkUpdateInstanceFormatIds(newInstance);
  }

  @Test
  void instanceUpdate_marksHoldingsShared_whenInstanceBecomesShared() throws Exception {
    var instanceId = UUID.randomUUID();
    var oldInstance = new Instance().id(instanceId.toString()).source("local");
    var newInstance = new Instance().id(instanceId.toString())
      .source("CONSORTIUM-FOLIO")
      .instanceFormatIds(List.of("fmt-1"));

    when(resourceEventUtil.getOldFromInventoryEvent(resourceEvent, Instance.class)).thenReturn(oldInstance);
    when(resourceEventUtil.getNewFromInventoryEvent(resourceEvent, Instance.class)).thenReturn(newInstance);
    when(holdingRepository.countByIdInstanceId(instanceId)).thenReturn(1);

    handler.handle(resourceEvent);

    verify(rtacHoldingBulkRepository).bulkUpdateInstanceFormatIds(newInstance);
    verify(rtacHoldingBulkRepository).bulkMarkHoldingsAsSharedByInstanceId(instanceId);
  }

  @Test
  void instanceUpdate_doesNotMarkHoldingsShared_whenInstanceAlreadyShared() throws Exception {
    var instanceId = UUID.randomUUID();
    var oldInstance = new Instance().id(instanceId.toString()).source("CONSORTIUM-FOLIO");
    var newInstance = new Instance().id(instanceId.toString())
      .source("CONSORTIUM-FOLIO")
      .instanceFormatIds(List.of("fmt-1"));

    when(resourceEventUtil.getOldFromInventoryEvent(resourceEvent, Instance.class)).thenReturn(oldInstance);
    when(resourceEventUtil.getNewFromInventoryEvent(resourceEvent, Instance.class)).thenReturn(newInstance);
    when(holdingRepository.countByIdInstanceId(instanceId)).thenReturn(1);

    handler.handle(resourceEvent);

    verify(rtacHoldingBulkRepository).bulkUpdateInstanceFormatIds(newInstance);
    verify(rtacHoldingBulkRepository, never()).bulkMarkHoldingsAsSharedByInstanceId(any(UUID.class));
  }

  @Test
  void instanceUpdate_doesNotMarkHoldingsShared_whenNewInstanceSourceIsNull() throws Exception {
    var instanceId = UUID.randomUUID();
    var oldInstance = new Instance().id(instanceId.toString()).source("CONSORTIUM-FOLIO");
    var newInstance = new Instance().id(instanceId.toString())
      .instanceFormatIds(List.of("fmt-1"));

    when(resourceEventUtil.getOldFromInventoryEvent(resourceEvent, Instance.class)).thenReturn(oldInstance);
    when(resourceEventUtil.getNewFromInventoryEvent(resourceEvent, Instance.class)).thenReturn(newInstance);
    when(holdingRepository.countByIdInstanceId(instanceId)).thenReturn(1);

    handler.handle(resourceEvent);

    verify(rtacHoldingBulkRepository).bulkUpdateInstanceFormatIds(newInstance);
    verify(rtacHoldingBulkRepository, never()).bulkMarkHoldingsAsSharedByInstanceId(any(UUID.class));
  }

  @Test
  void instanceUpdate_marksHoldingsShared_whenOldInstanceSourceIsNull() throws Exception {
    var instanceId = UUID.randomUUID();
    var oldInstance = new Instance().id(instanceId.toString());
    var newInstance = new Instance().id(instanceId.toString()).source("CONSORTIUM-FOLIO")
      .instanceFormatIds(List.of("fmt-1"));

    when(resourceEventUtil.getOldFromInventoryEvent(resourceEvent, Instance.class)).thenReturn(oldInstance);
    when(resourceEventUtil.getNewFromInventoryEvent(resourceEvent, Instance.class)).thenReturn(newInstance);
    when(holdingRepository.countByIdInstanceId(instanceId)).thenReturn(1);

    handler.handle(resourceEvent);

    verify(rtacHoldingBulkRepository).bulkUpdateInstanceFormatIds(newInstance);
    verify(rtacHoldingBulkRepository).bulkMarkHoldingsAsSharedByInstanceId(instanceId);
  }
}
