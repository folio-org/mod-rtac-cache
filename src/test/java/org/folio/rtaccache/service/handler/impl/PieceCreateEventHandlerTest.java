package org.folio.rtaccache.service.handler.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.Piece;
import org.folio.rtaccache.domain.dto.PieceEventAction;
import org.folio.rtaccache.domain.dto.PieceResourceEvent;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.RtacHoldingMappingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PieceCreateEventHandlerTest {

  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String HOLDINGS_ID = UUID.randomUUID().toString();
  private static final String PIECE_ID = UUID.randomUUID().toString();

  @InjectMocks
  PieceCreateEventHandler handler;

  @Mock
  RtacHoldingRepository holdingRepository;
  @Mock
  RtacHoldingMappingService mappingService;

  @Test
  void pieceCreate_shouldSaveEntity() {
    var holdingsEntity = new RtacHoldingEntity(
      new RtacHoldingId(UUID.fromString(INSTANCE_ID), TypeEnum.HOLDING, UUID.fromString(HOLDINGS_ID)),
      false,
      holdingMapped(TypeEnum.HOLDING, HOLDINGS_ID),
      Instant.now()
    );
    var piece = piece();
    var mappedPieceRtac = holdingMapped(TypeEnum.PIECE, PIECE_ID);
    var event = new PieceResourceEvent()
      .action(PieceEventAction.CREATE)
      .pieceSnapshot(piece);
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(HOLDINGS_ID), TypeEnum.HOLDING))
      .thenReturn(Optional.of(holdingsEntity));
    when(mappingService.mapForPieceTypeFrom(any(RtacHolding.class), any(Piece.class)))
      .thenReturn(mappedPieceRtac);

    handler.handle(event);

    verify(holdingRepository).save(any(RtacHoldingEntity.class));
  }

  @Test
  void pieceCreate_shouldNotSave_whenHoldingsNotFound() {
    var piece = piece();
    var event = new PieceResourceEvent()
      .action(PieceEventAction.CREATE)
      .pieceSnapshot(piece);
    when(holdingRepository.findByIdIdAndIdType(UUID.fromString(HOLDINGS_ID), TypeEnum.HOLDING))
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

  private Piece piece() {
    var p = new Piece();
    p.setId(PIECE_ID);
    p.setHoldingId(HOLDINGS_ID);
    p.setCopyNumber("PCN");
    p.setReceivingStatus(Piece.ReceivingStatusEnum.EXPECTED);
    return p;
  }


}
