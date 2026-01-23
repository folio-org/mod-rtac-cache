package org.folio.rtaccache.service.handler.impl;

import static org.folio.rtaccache.service.RtacCacheGenerationService.CONSORTIUM_SOURCE;

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
    var oldInstance = resourceEventUtil.getOldFromInventoryEvent(resourceEvent, Instance.class);
    var newInstance = resourceEventUtil.getNewFromInventoryEvent(resourceEvent, Instance.class);
    var count = holdingRepository.countByIdInstanceId(UUID.fromString(newInstance.getId()));
    if (count > 0) {
      try {
        rtacHoldingBulkRepository.bulkUpdateInstanceFormatIds(newInstance);
        if (isInstanceBecomeShared(oldInstance, newInstance)) {
          rtacHoldingBulkRepository.bulkMarkHoldingsAsSharedByInstanceId(
            UUID.fromString(newInstance.getId()));
        }
      } catch (SQLException e) {
        log.error("Error during updating RTAC holdings with format ids by instanceId: {}", newInstance.getId(), e);
      }
    }
  }

  private boolean isInstanceBecomeShared(Instance oldInstance, Instance newInstance) {
    if (newInstance.getSource() == null) {
      return false;
    }
    return (oldInstance.getSource() == null || !oldInstance.getSource().contains(CONSORTIUM_SOURCE)) &&
      newInstance.getSource().contains(CONSORTIUM_SOURCE);
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
