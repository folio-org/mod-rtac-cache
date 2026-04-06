package org.folio.rtaccache.client;

import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface InventoryClient {

  @GetExchange("instance-storage/instances")
  Instances getInstances(@RequestParam Map<String, String> queryParametersMap);

  @PostExchange("holdings-storage/holdings/retrieve")
  HoldingRecords getHoldings(@RequestBody FolioCqlRequest request);

  @PostExchange("item-storage/items/retrieve")
  Items getItems(@RequestBody FolioCqlRequest request);

  @GetExchange("inventory-storage/bound-with-parts")
  BoundWithParts getBoundWithParts(@RequestParam Map<String, String> queryParametersMap);

  @GetExchange("locations")
  Locations getLocations(@RequestParam Map<String,String> queryParametersMap);

  @GetExchange("location-units/libraries")
  Loclibs getLibraries(@RequestParam Map<String,String> queryParametersMap);

  @GetExchange("holdings-note-types")
  HoldingsNoteTypes getHoldingsNoteTypes(@RequestParam Map<String, String> queryParametersMap);

  @GetExchange("loan-types")
  LoanTypes getLoanTypes(@RequestParam Map<String, String> queryParametersMap);

  @GetExchange("material-types")
  MaterialTypes getMaterialTypes(@RequestParam Map<String, String> queryParametersMap);
}
