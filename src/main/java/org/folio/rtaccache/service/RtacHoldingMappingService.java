package org.folio.rtaccache.service;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.folio.rtaccache.domain.dto.HoldingsNote;
import org.folio.rtaccache.domain.dto.HoldingsNoteType;
import org.folio.rtaccache.domain.dto.HoldingsRecord;
import org.folio.rtaccache.domain.dto.HoldingsStatement;
import org.folio.rtaccache.domain.dto.Item;
import org.folio.rtaccache.domain.dto.Piece;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.domain.dto.RtacHoldingLibrary;
import org.folio.rtaccache.domain.dto.RtacHoldingLocation;
import org.folio.rtaccache.domain.dto.RtacHoldingMaterialType;
import org.folio.rtaccache.domain.dto.RtacHoldingNotesInner;
import org.folio.rtaccache.domain.dto.RtacHoldingsStatement;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RtacHoldingMappingService {

  private final InventoryReferenceDataService inventoryReferenceDataService;

  public RtacHolding mapFrom(HoldingsRecord holding, Item item) {
    var rtacHolding = new RtacHolding();
    rtacHolding.setId(item.getId());
    rtacHolding.setType(TypeEnum.ITEM);
    rtacHolding.setInstanceId(holding.getInstanceId());
    rtacHolding.setBarcode(item.getBarcode());
    rtacHolding.setCallNumber(item.getItemLevelCallNumber());
    rtacHolding.setHoldingsCopyNumber(holding.getCopyNumber());
    rtacHolding.setItemCopyNumber(item.getCopyNumber());
    rtacHolding.setVolume(mapVolumeFrom(item));
    rtacHolding.setEffectiveShelvingOrder(item.getEffectiveShelvingOrder());
    rtacHolding.setStatus(item.getStatus().getName().getValue());
    rtacHolding.setSuppressFromDiscovery(item.getDiscoverySuppress());
    rtacHolding.setLocation(mapLocationFrom(item.getEffectiveLocationId()));
    rtacHolding.setLibrary(mapLibraryFrom(item.getEffectiveLocationId()));
    rtacHolding.setMaterialType(mapMaterialTypeFrom(item));
    rtacHolding.setTemporaryLoanType(mapLoanTypeFrom(item.getTemporaryLoanTypeId()));
    rtacHolding.setPermanentLoanType(mapLoanTypeFrom(item.getPermanentLoanTypeId()));
    rtacHolding.setHoldingsStatements(mapArraySafe(holding.getHoldingsStatements(), this::mapHoldingsStatementFrom));
    rtacHolding.setHoldingsStatementsForIndexes(mapArraySafe(holding.getHoldingsStatementsForIndexes(), this::mapHoldingsStatementFrom));
    rtacHolding.setHoldingsStatementsForSupplements(mapArraySafe(holding.getHoldingsStatementsForSupplements(), this::mapHoldingsStatementFrom));
    rtacHolding.setNotes(mapHoldingsNotesFrom(holding));
    return rtacHolding;
  }

  public RtacHolding mapFrom(HoldingsRecord holding) {
    var rtacHolding = new RtacHolding();
    rtacHolding.setId(holding.getId());
    rtacHolding.setType(TypeEnum.HOLDING);
    rtacHolding.setInstanceId(holding.getInstanceId());
    rtacHolding.setCallNumber(holding.getCallNumber());
    rtacHolding.setHoldingsCopyNumber(holding.getCopyNumber());
    rtacHolding.setStatus(mapHoldingsStatusFrom(holding));
    rtacHolding.setSuppressFromDiscovery(holding.getDiscoverySuppress());
    rtacHolding.setLocation(mapLocationFrom(holding.getEffectiveLocationId()));
    rtacHolding.setLibrary(mapLibraryFrom(holding.getEffectiveLocationId()));
    rtacHolding.setHoldingsStatements(mapArraySafe(holding.getHoldingsStatements(), this::mapHoldingsStatementFrom));
    rtacHolding.setHoldingsStatementsForIndexes(mapArraySafe(holding.getHoldingsStatementsForIndexes(), this::mapHoldingsStatementFrom));
    rtacHolding.setHoldingsStatementsForSupplements(mapArraySafe(holding.getHoldingsStatementsForSupplements(), this::mapHoldingsStatementFrom));
    rtacHolding.setNotes(mapHoldingsNotesFrom(holding));
    return rtacHolding;
  }

  public RtacHolding mapFrom(HoldingsRecord holding, Piece piece) {
    var rtacHolding = new RtacHolding();
    rtacHolding.setId(piece.getId());
    rtacHolding.setType(TypeEnum.PIECE);
    rtacHolding.setInstanceId(holding.getInstanceId());
    rtacHolding.setCallNumber(holding.getCallNumber());
    rtacHolding.setHoldingsCopyNumber(piece.getCopyNumber());
    rtacHolding.setStatus(piece.getReceivingStatus().getValue());
    rtacHolding.setVolume(mapVolumeFrom(piece));
    rtacHolding.setSuppressFromDiscovery(holding.getDiscoverySuppress());
    rtacHolding.setLocation(mapLocationFrom(holding.getEffectiveLocationId()));
    rtacHolding.setLibrary(mapLibraryFrom(holding.getEffectiveLocationId()));
    rtacHolding.setHoldingsStatements(mapArraySafe(holding.getHoldingsStatements(), this::mapHoldingsStatementFrom));
    rtacHolding.setHoldingsStatementsForIndexes(mapArraySafe(holding.getHoldingsStatementsForIndexes(), this::mapHoldingsStatementFrom));
    rtacHolding.setHoldingsStatementsForSupplements(mapArraySafe(holding.getHoldingsStatementsForSupplements(), this::mapHoldingsStatementFrom));
    rtacHolding.setNotes(mapHoldingsNotesFrom(holding));
    return rtacHolding;
  }

  private RtacHoldingLocation mapLocationFrom(String locationId) {
    var location = new RtacHoldingLocation();
    if (locationId == null || locationId.isEmpty()) {
      return location;
    }
    var itemLocation = inventoryReferenceDataService.getLocationsMap().get(locationId);
    location.setId(locationId);
    if (itemLocation != null) {
      location.setName(itemLocation.getName());
      location.setCode(itemLocation.getCode());
    }
    return location;
  }

  private RtacHoldingLibrary mapLibraryFrom(String locationId) {
    var location = inventoryReferenceDataService.getLocationsMap().get(locationId);
    var rtacHoldingLibrary = new RtacHoldingLibrary();
    if (location != null) {
      var library = inventoryReferenceDataService.getLibraryMap().get(location.getLibraryId());
      if (library != null) {
        rtacHoldingLibrary.setId(library.getId());
        rtacHoldingLibrary.setCode(library.getCode());
        rtacHoldingLibrary.setName(library.getName());
      }
    }
    return rtacHoldingLibrary;
  }

  private RtacHoldingMaterialType mapMaterialTypeFrom(Item item) {
    if (item.getMaterialTypeId().isEmpty()) {
      return null;
    }
    var materialType = inventoryReferenceDataService.getMaterialTypesMap().get(item.getMaterialTypeId());
    var rtacHoldingMaterialType = new RtacHoldingMaterialType();
    rtacHoldingMaterialType.setId(item.getMaterialTypeId());
    rtacHoldingMaterialType.setName(materialType != null ? materialType.getName() : null);
    return rtacHoldingMaterialType;
  }

  private String mapLoanTypeFrom(String loanTypeId) {
    if (loanTypeId == null || loanTypeId.isEmpty()) {
      return null;
    }
    var loanType = inventoryReferenceDataService.getLoanTypesMap().get(loanTypeId);
    return loanType != null ? loanType.getName() : null;
  }

  private List<RtacHoldingNotesInner> mapHoldingsNotesFrom(HoldingsRecord holding) {
    if (holding.getNotes() == null) {
      return Collections.emptyList();
    }
    var noteTypesMap = inventoryReferenceDataService.getHoldingsNoteTypesMap();
    return holding.getNotes().stream()
      .filter(note -> !note.getStaffOnly())
      .map(note -> mapHoldingsNoteFrom(note, noteTypesMap))
      .toList();
  }

  private RtacHoldingNotesInner mapHoldingsNoteFrom(HoldingsNote note, Map<String, HoldingsNoteType> holdingsNoteTypeMap) {
    var rtacHoldingNote = new RtacHoldingNotesInner();
    var noteType = holdingsNoteTypeMap.get(note.getHoldingsNoteTypeId());
    rtacHoldingNote.setHoldingsNoteTypeName(noteType != null ? noteType.getName() : null);
    rtacHoldingNote.setNote(note.getNote());
    return rtacHoldingNote;
  }

  private RtacHoldingsStatement mapHoldingsStatementFrom(HoldingsStatement statement) {
    var rtacHoldingsStatement = new RtacHoldingsStatement();
    rtacHoldingsStatement.setStatement(statement.getStatement());
    rtacHoldingsStatement.setNote(statement.getNote());
    return rtacHoldingsStatement;
  }

  private String mapHoldingsStatusFrom(HoldingsRecord holding) {
    var status = "Multi";
    if (holding.getHoldingsStatements() != null && !holding.getHoldingsStatements().isEmpty()) {
      status = holding.getHoldingsStatements().get(0).getStatement();
    }
    return status;
  }

  /**
   * Generating rules.
   * <p/>
   * The rules for generating "volume" are as follows:
   * |data set                     |"volume"                    |
   * |-----------------------------|----------------------------|
   * |displaySummary               |(displaySummary)            |
   * |enumeration                  |(enumeration)               |
   * |enumeration chronology       |(enumeration chronology)    |
   * |enumeration chronology volume|(enumeration chronology)    |
   * |volume                       |(volume)                    |
   * |chronology volume            |(volume)                    |
   * |chronology                   |(chronology)                |
   *
   * @param item - folio inventory item
   */
  private String mapVolumeFrom(Item item) {
    final String enumeration = item.getEnumeration();
    final String chronology = item.getChronology();
    final String displaySummary = item.getDisplaySummary();
    final String volume = item.getVolume();

    final StringJoiner sj = new StringJoiner(" ", "(", ")").setEmptyValue("");

    if (isNotBlank(displaySummary)) {
      sj.add(displaySummary);
    } else if (isNotBlank(enumeration)) {
      sj.add(enumeration);
      if (isNotBlank(chronology)) {
        sj.add(chronology);
      }
    } else if (isNotBlank(volume)) {
      sj.add(volume);
    } else if (isNotBlank(chronology)) {
      sj.add(chronology);
    }

    return defaultIfEmpty(sj.toString(), null);
  }

  /**
   * Generating rules.
   * <p/>
   * The rules for generating "volume" are as follows:
   * |data set                     |"volume"                    |
   * |-----------------------------|----------------------------|
   * |displaySummary               |(displaySummary)            |
   * |enumeration                  |(enumeration)               |
   * |enumeration chronology       |(enumeration chronology)    |
   * |chronology                   |(chronology)                |
   *
   * @param piece - folio order piece
   */

  private String mapVolumeFrom(Piece piece) {
    final String enumeration = piece.getEnumeration();
    final String chronology = piece.getChronology();
    final String displaySummary = piece.getDisplaySummary();

    final StringJoiner sj = new StringJoiner(" ", "(", ")").setEmptyValue("");

    if (isNotBlank(displaySummary)) {
      sj.add(displaySummary);
    } else if (isNotBlank(enumeration)) {
      sj.add(enumeration);
      if (isNotBlank(chronology)) {
        sj.add(chronology);
      }
    } else if (isNotBlank(chronology)) {
      sj.add(chronology);
    }

    return defaultIfEmpty(sj.toString(), Strings.EMPTY);
  }

  private <S,T> List<T> mapArraySafe(List<S> sourceList, Function<S, T> mapper) {
    if (sourceList == null) {
      return Collections.emptyList();
    }
    return sourceList.stream()
      .map(mapper)
      .toList();
  }
}
