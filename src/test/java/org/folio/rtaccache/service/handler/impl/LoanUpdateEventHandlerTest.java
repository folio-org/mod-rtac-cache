package org.folio.rtaccache.service.handler.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.CirculationEventType;
import org.folio.rtaccache.domain.dto.CirculationResourceEvent;
import org.folio.rtaccache.domain.dto.Loan;
import org.folio.rtaccache.domain.dto.LoanStatus;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanUpdateEventHandlerTest {

  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String HOLDINGS_ID = UUID.randomUUID().toString();
  private static final String ITEM_ID = UUID.randomUUID().toString();

  @InjectMocks
  LoanUpdateEventHandler handler;

  @Mock
  RtacHoldingRepository holdingRepository;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void loanUpdate_open_shouldSetDueDateAndSave() {
    var existingItemEntity = new RtacHoldingEntity(
      new RtacHoldingId(UUID.fromString(INSTANCE_ID), TypeEnum.ITEM, UUID.fromString(ITEM_ID)),
      holdingMapped(TypeEnum.ITEM, ITEM_ID),
      Instant.now()
    );
    var dueDate = new Date();
    var loan = loan(dueDate, "Open");
    var event = new CirculationResourceEvent()
      .type(CirculationEventType.UPDATED)
      .data(new org.folio.rtaccache.domain.dto.CirculationResourceEventData()._new(loan));
    when(resourceEventUtil.getNewFromCirculationEvent(event, Loan.class)).thenReturn(loan);
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(ITEM_ID), TypeEnum.ITEM))
      .thenReturn(Optional.of(existingItemEntity));

    handler.handle(event);

    verify(holdingRepository).save(existingItemEntity);
    assert existingItemEntity.getRtacHolding().getDueDate() == dueDate;
  }

  @Test
  void loanUpdate_closed_shouldClearDueDateAndSave() {
    var rh = holdingMapped(TypeEnum.ITEM, ITEM_ID);
    rh.setDueDate(new Date());
    var existingItemEntity = new RtacHoldingEntity(
      new RtacHoldingId(UUID.fromString(INSTANCE_ID), TypeEnum.ITEM, UUID.fromString(ITEM_ID)),
      rh,
      Instant.now()
    );
    var loan = loan(new Date(), "Closed"); // new due date ignored, will be cleared
    var event = new CirculationResourceEvent()
      .type(CirculationEventType.UPDATED)
      .data(new org.folio.rtaccache.domain.dto.CirculationResourceEventData()._new(loan));
    when(resourceEventUtil.getNewFromCirculationEvent(event, Loan.class)).thenReturn(loan);
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(ITEM_ID), TypeEnum.ITEM))
      .thenReturn(Optional.of(existingItemEntity));

    handler.handle(event);

    verify(holdingRepository).save(existingItemEntity);
    assertThat(existingItemEntity.getRtacHolding().getDueDate()).isNull();
  }

  @Test
  void loanUpdate_shouldNotSave_whenStatusNull() {
    var loan = loan(new Date(), null);
    var event = new CirculationResourceEvent()
      .type(CirculationEventType.UPDATED)
      .data(new org.folio.rtaccache.domain.dto.CirculationResourceEventData()._new(loan));
    when(resourceEventUtil.getNewFromCirculationEvent(event, Loan.class)).thenReturn(loan);

    handler.handle(event);

    verify(holdingRepository, never()).save(any(RtacHoldingEntity.class));
  }

  @Test
  void loanUpdate_shouldNotSave_whenItemNotFound() {
    var loan = loan(new Date(), "Open");
    var event = new CirculationResourceEvent()
      .type(CirculationEventType.UPDATED)
      .data(new org.folio.rtaccache.domain.dto.CirculationResourceEventData()._new(loan));
    when(resourceEventUtil.getNewFromCirculationEvent(event, Loan.class)).thenReturn(loan);
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(ITEM_ID), TypeEnum.ITEM))
      .thenReturn(Optional.empty());

    handler.handle(event);

    verify(holdingRepository, never()).save(any(RtacHoldingEntity.class));
  }

  private RtacHolding holdingMapped(TypeEnum type, String id) {
    var rh = new RtacHolding();
    rh.setType(type);
    rh.setId(id);
    rh.setInstanceId(INSTANCE_ID);
    rh.setHoldingsId(HOLDINGS_ID);
    rh.setStatus("Available");
    return rh;
  }

  private Loan loan(Date dueDate, String statusName) {
    var loan = new Loan();
    loan.setItemId(ITEM_ID);
    loan.setDueDate(dueDate);
    if (statusName != null) {
      var ls = new LoanStatus();
      ls.setName(statusName);
      loan.setStatus(ls);
    }
    return loan;
  }

}
