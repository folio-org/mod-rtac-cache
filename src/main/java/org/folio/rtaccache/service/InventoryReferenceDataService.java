package org.folio.rtaccache.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.client.InventoryClient;
import org.folio.rtaccache.domain.dto.FolioCqlRequest;
import org.folio.rtaccache.domain.dto.HoldingsNoteType;
import org.folio.rtaccache.domain.dto.Loantype;
import org.folio.rtaccache.domain.dto.Location;
import org.folio.rtaccache.domain.dto.Loclib;
import org.folio.rtaccache.domain.dto.Materialtype;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryReferenceDataService {

  private final InventoryClient inventoryClient;

  Map<String, Location> getLocationsMap() {
    return inventoryClient.getLocations(new FolioCqlRequest(null, Integer.MAX_VALUE, 0))
      .getLocations()
      .stream()
      .collect(
        java.util.stream.Collectors.toMap(
          Location::getId,
          location -> location
        )
      );
  }

  Map<String, Loclib> getLibraryMap() {
    return inventoryClient.getLibraries(new FolioCqlRequest(null, Integer.MAX_VALUE, 0))
      .getLoclibs()
      .stream()
      .collect(
        java.util.stream.Collectors.toMap(
          Loclib::getId,
          loclib -> loclib
        )
      );
  }

  Map<String, Materialtype> getMaterialTypesMap() {
    return inventoryClient.getMaterialTypes(new FolioCqlRequest(null, Integer.MAX_VALUE, 0))
      .getMtypes()
      .stream()
      .collect(
        java.util.stream.Collectors.toMap(
          Materialtype::getId,
          materialtype -> materialtype
        )
      );
  }

  Map<String, Loantype> getLoanTypesMap() {
    return inventoryClient.getLoanTypes(new FolioCqlRequest(null, Integer.MAX_VALUE, 0))
      .getLoantypes()
      .stream()
      .collect(
        java.util.stream.Collectors.toMap(
          Loantype::getId,
          loantype -> loantype
        )
      );
  }

  Map<String, HoldingsNoteType> getHoldingsNoteTypesMap() {
    return inventoryClient.getHoldingsNoteTypes(new FolioCqlRequest(null, Integer.MAX_VALUE, 0))
      .getHoldingsNoteTypes()
      .stream()
      .collect(
        java.util.stream.Collectors.toMap(
          HoldingsNoteType::getId,
          holdingsNoteType -> holdingsNoteType
        )
      );
  }

}
