package org.folio.rtaccache.service.handler.impl;

import static java.util.Optional.ofNullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.dto.PieceEventAction;
import org.folio.rtaccache.domain.dto.PieceResourceEvent;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PieceDeleteEventHandlerTest {

  private static final String PIECE_ID = UUID.randomUUID().toString();

  @InjectMocks
  PieceDeleteEventHandler handler;

  @Mock
  RtacHoldingRepository holdingRepository;
  @Mock
  RtacHoldingEntity rtacHoldingEntity;

  @Test
  void pieceDelete_shouldInvokeRepositoryDelete() {
    var event = new PieceResourceEvent()
      .action(PieceEventAction.DELETE)
      .pieceId(PIECE_ID);
    when(holdingRepository.findByIdId(UUID.fromString(PIECE_ID)))
      .thenReturn(ofNullable(rtacHoldingEntity));

    handler.handle(event);

    verify(holdingRepository).deleteByIdId(UUID.fromString(PIECE_ID));
  }

}
