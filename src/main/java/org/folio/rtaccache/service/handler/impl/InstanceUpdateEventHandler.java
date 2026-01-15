package org.folio.rtaccache.service.handler.impl;

import java.sql.SQLException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.rtaccache.domain.dto.Instance;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.handler.InventoryEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Log4j2
public class InstanceUpdateEventHandler implements InventoryEventHandler {

  private final RtacHoldingRepository holdingRepository;
  private final RtacHoldingBulkRepository rtacHoldingBulkRepository;
  private final ResourceEventUtil resourceEventUtil;

  @Override
  @Transactional
  public void handle(InventoryResourceEvent resourceEvent) {
    var instance = resourceEventUtil.getNewFromInventoryEvent(resourceEvent, Instance.class);
    var count = holdingRepository.countByIdInstanceId(UUID.fromString(instance.getId()));
    if (count > 0) {
      try {
        rtacHoldingBulkRepository.bulkUpdateInstanceFormatIds(instance);
      } catch (SQLException e) {
        log.error("Error during updating RTAC holdings with format ids by instanceId: {}", instance.getId(), e);
      }
    }

  }

  @Override
  public InventoryEventType getEventType() {
    return InventoryEventType.UPDATE;
  }


  @Override
  public InventoryEntityType getEntityType() {
    return InventoryEntityType.INSTANCE;
  }

}
