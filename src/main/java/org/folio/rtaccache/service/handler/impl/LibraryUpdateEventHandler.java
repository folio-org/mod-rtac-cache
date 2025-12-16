package org.folio.rtaccache.service.handler.impl;

import static org.folio.rtaccache.constant.RtacCacheConstant.LIBRARY_CACHE_NAME;

import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.Loclib;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.service.handler.InventoryEventHandler;
import org.folio.rtaccache.util.CacheUtil;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Log4j2
public class LibraryUpdateEventHandler implements InventoryEventHandler {

  private final RtacHoldingBulkRepository rtacHoldingBulkRepository;
  private final ResourceEventUtil resourceEventUtil;
  private final CacheUtil cacheUtil;

  @Override
  @Transactional
  public void handle(InventoryResourceEvent resourceEvent) {
    cacheUtil.clearCache(LIBRARY_CACHE_NAME);
    var library = resourceEventUtil.getNewFromInventoryEvent(resourceEvent, Loclib.class);
    try {
      rtacHoldingBulkRepository.bulkUpdateLibraryData(library);
    } catch (SQLException e) {
      log.error("Error during updating RTAC holdings with library data by library id: {}", library.getId(), e);
    }
  }

  @Override
  public InventoryEventType getEventType() {
    return InventoryEventType.UPDATE;
  }

  @Override
  public InventoryEntityType getEntityType() {
    return InventoryEntityType.LIBRARY;
  }
}
