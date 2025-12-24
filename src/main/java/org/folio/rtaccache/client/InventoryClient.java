package org.folio.rtaccache.client;

import org.folio.rtaccache.domain.dto.BoundWithParts;
import org.folio.rtaccache.domain.dto.FolioCqlRequest;
import org.folio.rtaccache.domain.dto.HoldingRecords;
import org.folio.rtaccache.domain.dto.HoldingsNoteTypes;
import org.folio.rtaccache.domain.dto.Instances;
import org.folio.rtaccache.domain.dto.Items;
import org.folio.rtaccache.domain.dto.LoanTypes;
import org.folio.rtaccache.domain.dto.Locations;
import org.folio.rtaccache.domain.dto.Loclibs;
import org.folio.rtaccache.domain.dto.MaterialTypes;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "inventory")
public interface InventoryClient {

  @GetMapping("/instance-storage/instances")
  Instances getInstances(@SpringQueryMap FolioCqlRequest request);

  @PostMapping("/holdings-storage/holdings/retrieve")
  HoldingRecords getHoldings(@RequestBody FolioCqlRequest request);

  @PostMapping("/item-storage/items/retrieve")
  Items getItems(@RequestBody FolioCqlRequest request);

  @GetMapping("/inventory-storage/bound-with-parts")
  BoundWithParts getBoundWithParts(@SpringQueryMap FolioCqlRequest request);

  @GetMapping("/locations")
  Locations getLocations(@SpringQueryMap FolioCqlRequest request);

  @GetMapping("/location-units/libraries")
  Loclibs getLibraries(@SpringQueryMap FolioCqlRequest request);

  @GetMapping("/holdings-note-types")
  HoldingsNoteTypes getHoldingsNoteTypes(@SpringQueryMap FolioCqlRequest request);

  @GetMapping("/loan-types")
  LoanTypes getLoanTypes(@SpringQueryMap FolioCqlRequest request);

  @GetMapping("/material-types")
  MaterialTypes getMaterialTypes(@SpringQueryMap FolioCqlRequest request);
}
