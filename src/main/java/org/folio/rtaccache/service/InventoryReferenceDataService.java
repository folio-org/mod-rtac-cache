package org.folio.rtaccache.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.client.InventoryClient;
import org.folio.rtaccache.domain.dto.FolioCqlRequest;
import org.folio.rtaccache.domain.dto.HoldingsNoteType;
import org.folio.rtaccache.domain.dto.LoanType;
import org.folio.rtaccache.domain.dto.Location;
import org.folio.rtaccache.domain.dto.Loclib;
import org.folio.rtaccache.domain.dto.MaterialType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryReferenceDataService {

  private final InventoryClient inventoryClient;

  @Cacheable(value = "locationsMap", unless = "#result == null || #result.isEmpty()")
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

  @Cacheable(value = "libraryMap", unless = "#result == null || #result.isEmpty()")
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

  @Cacheable(value = "materialTypesMap", unless = "#result == null || #result.isEmpty()")
  Map<String, MaterialType> getMaterialTypesMap() {
    return inventoryClient.getMaterialTypes(new FolioCqlRequest(null, Integer.MAX_VALUE, 0))
      .getMtypes()
      .stream()
      .collect(
        java.util.stream.Collectors.toMap(
          MaterialType::getId,
          materialtype -> materialtype
        )
      );
  }

  @Cacheable(value = "loanTypesMap", unless = "#result == null || #result.isEmpty()")
  Map<String, LoanType> getLoanTypesMap() {
    return inventoryClient.getLoanTypes(new FolioCqlRequest(null, Integer.MAX_VALUE, 0))
      .getLoantypes()
      .stream()
      .collect(
        java.util.stream.Collectors.toMap(
          LoanType::getId,
          loanType -> loanType
        )
      );
  }

  @Cacheable(value = "holdingsNoteTypesMap", unless = "#result == null || #result.isEmpty()")
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
