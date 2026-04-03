package org.folio.rtaccache.client;

import java.util.Map;
import org.folio.rtaccache.domain.dto.PieceCollection;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface OrdersClient {

  @GetExchange("orders-storage/pieces")
  PieceCollection getPieces(@RequestParam Map<String, String> queryParametersMap);
}
