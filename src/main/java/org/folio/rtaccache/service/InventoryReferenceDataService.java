package org.folio.rtaccache.service;

import static org.folio.rtaccache.constant.RtacCacheConstant.HOLDINGS_NOTE_CACHE_NAME;
import static org.folio.rtaccache.constant.RtacCacheConstant.LIBRARY_CACHE_NAME;
import static org.folio.rtaccache.constant.RtacCacheConstant.LOAN_TYPES_CACHE_NAME;
import static org.folio.rtaccache.constant.RtacCacheConstant.LOCATIONS_CACHE_NAME;
import static org.folio.rtaccache.constant.RtacCacheConstant.MATERIAL_TYPES_CACHE_NAME;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.client.InventoryClient;
import org.folio.rtaccache.domain.dto.HoldingsNoteType;
import org.folio.rtaccache.domain.dto.LoanType;
import org.folio.rtaccache.domain.dto.Location;
import org.folio.rtaccache.domain.dto.Loclib;
import org.folio.rtaccache.domain.dto.MaterialType;
import org.folio.rtaccache.util.QueryParametersUtil;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryReferenceDataService {

  private final InventoryClient inventoryClient;
  private final QueryParametersUtil queryParametersUtil;

  @Cacheable(value = LOCATIONS_CACHE_NAME,
    key = "'locations'.concat('_').concat(@folioExecutionContext.getTenantId())",
    unless = "#result == null || #result.isEmpty()")
  public Map<String, Location> getLocationsMap() {
    var queryParametersMap = queryParametersUtil.toMap(null, Integer.MAX_VALUE, 0);
    return inventoryClient.getLocations(queryParametersMap)
      .getLocations()
      .stream()
      .collect(
        java.util.stream.Collectors.toMap(
          Location::getId,
          location -> location
        )
      );
  }

  @Cacheable(value = LIBRARY_CACHE_NAME,
    key = "'library'.concat('_').concat(@folioExecutionContext.getTenantId())",
    unless = "#result == null || #result.isEmpty()")
  public Map<String, Loclib> getLibraryMap() {
    var queryParametersMap = queryParametersUtil.toMap(null, Integer.MAX_VALUE, 0);
    return inventoryClient.getLibraries(queryParametersMap)
      .getLoclibs()
      .stream()
      .collect(
        java.util.stream.Collectors.toMap(
          Loclib::getId,
          loclib -> loclib
        )
      );
  }

  @Cacheable(value = MATERIAL_TYPES_CACHE_NAME,
    key = "'materialTypes'.concat('_').concat(@folioExecutionContext.getTenantId())",
    unless = "#result == null || #result.isEmpty()")
  public Map<String, MaterialType> getMaterialTypesMap() {
    var queryParametersMap = queryParametersUtil.toMap(null, Integer.MAX_VALUE, 0);
    return inventoryClient.getMaterialTypes(queryParametersMap)
      .getMtypes()
      .stream()
      .collect(
        java.util.stream.Collectors.toMap(
          MaterialType::getId,
          materialtype -> materialtype
        )
      );
  }

  @Cacheable(value = LOAN_TYPES_CACHE_NAME,
    key = "'loanTypes'.concat('_').concat(@folioExecutionContext.getTenantId())",
    unless = "#result == null || #result.isEmpty()")
  public Map<String, LoanType> getLoanTypesMap() {
    var queryParametersMap = queryParametersUtil.toMap(null, Integer.MAX_VALUE, 0);
    return inventoryClient.getLoanTypes(queryParametersMap)
      .getLoantypes()
      .stream()
      .collect(
        java.util.stream.Collectors.toMap(
          LoanType::getId,
          loanType -> loanType
        )
      );
  }

  @Cacheable(value = HOLDINGS_NOTE_CACHE_NAME,
    key = "'holdingsNotesTypes'.concat('_').concat(@folioExecutionContext.getTenantId())",
    unless = "#result == null || #result.isEmpty()")
  public Map<String, HoldingsNoteType> getHoldingsNoteTypesMap() {
    var queryParametersMap = queryParametersUtil.toMap(null, Integer.MAX_VALUE, 0);
    return inventoryClient.getHoldingsNoteTypes(queryParametersMap)
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
