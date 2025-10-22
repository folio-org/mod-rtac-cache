package org.folio.rtaccache.client;

import org.folio.rtaccache.domain.dto.FolioCqlRequest;
import org.folio.rtaccache.domain.dto.PieceCollection;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient("orders")
public interface OrdersClient {

  @GetMapping("/orders/pieces")
  PieceCollection getPieces(@SpringQueryMap FolioCqlRequest request);
}
