package org.folio.rtaccache.service;

import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.client.OrdersClient;
import org.folio.rtaccache.domain.dto.PieceCollection;
import org.folio.rtaccache.util.QueryParametersUtil;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrdersService {

  private final OrdersClient ordersClient;
  private final QueryParametersUtil queryParametersUtil;
  private static final Integer MAX_RECORDS = 1000;

  public PieceCollection getPiecesByHoldingId(String holdingId) {
    var cql = String.format("holdingId==(%s) and displayToPublic==true and displayOnHolding==true", holdingId);
    var request = queryParametersUtil.toMap(cql, MAX_RECORDS, 0);
    return ordersClient.getPieces(request);
  }

}
