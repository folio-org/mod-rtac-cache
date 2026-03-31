package org.folio.rtaccache.service.handler.impl;

import static org.folio.rtaccache.constant.RtacCacheConstant.LOAN_TYPES_CACHE_NAME;

import java.sql.SQLException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.LoanType;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.service.handler.InventoryEventHandler;
import org.folio.rtaccache.util.CacheUtil;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Log4j2
public class LoanTypeUpdateEventHandler implements InventoryEventHandler {

  private final RtacHoldingBulkRepository rtacHoldingBulkRepository;
  private final ResourceEventUtil resourceEventUtil;
  private final CacheUtil cacheUtil;

  @Override
  @Transactional
  public void handle(InventoryResourceEvent resourceEvent) {
    cacheUtil.clearCache(LOAN_TYPES_CACHE_NAME);

    var oldLoanType = resourceEventUtil.getOldFromInventoryEvent(resourceEvent, LoanType.class);
    var newLoanType = resourceEventUtil.getNewFromInventoryEvent(resourceEvent, LoanType.class);
    if (oldLoanType == null || newLoanType == null
      || oldLoanType.getName() == null
      || Objects.equals(oldLoanType.getName(), newLoanType.getName())) {
      return;
    }

    try {
      rtacHoldingBulkRepository.bulkUpdateLoanTypeData(oldLoanType, newLoanType);
    } catch (SQLException e) {
      log.error("Error during updating RTAC holdings with loan type data by loan type id: {}", newLoanType.getId(), e);
    }
  }

  @Override
  public InventoryEventType getEventType() {
    return InventoryEventType.UPDATE;
  }

  @Override
  public InventoryEntityType getEntityType() {
    return InventoryEntityType.LOAN_TYPE;
  }
}

