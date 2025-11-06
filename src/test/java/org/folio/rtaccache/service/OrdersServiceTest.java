package org.folio.rtaccache.service;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.rtaccache.TestUtil;
import org.folio.rtaccache.client.OrdersClient;
import org.folio.rtaccache.domain.dto.Piece;
import org.folio.rtaccache.domain.dto.PieceCollection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OrdersServiceTest {

  @Mock
  private OrdersClient ordersClient;

  @InjectMocks
  private OrdersService ordersService;

  @Test
  void getPiecesByHoldingId_shouldFetchPieces() {
    var piece = new Piece();
    piece.id("1");
    piece.setHoldingId("hold1");

    when(ordersClient.getPieces(argThat(TestUtil.queryContains("hold1")))).thenReturn(
      new PieceCollection()
        .pieces(List.of(piece))
        .totalRecords(1)
    );

    var pieces = ordersService.getPiecesByHoldingId("hold1");

    verify(ordersClient).getPieces(ArgumentMatchers.any());
    assertEquals(piece, pieces.getPieces().get(0));
  }
}
