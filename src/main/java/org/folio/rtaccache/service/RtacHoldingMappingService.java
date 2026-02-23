package org.folio.rtaccache.service;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
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
@Log4j2
public class RtacHoldingMappingService {

  private final InventoryReferenceDataService inventoryReferenceDataService;

  public RtacHolding mapFrom(HoldingsRecord holding, Item item) {
    var rtacHolding = new RtacHolding();
    rtacHolding.setId(item.getId());
    rtacHolding.setType(TypeEnum.ITEM);
    rtacHolding.setInstanceId(holding.getInstanceId());
    rtacHolding.setHoldingsId(holding.getId());
    rtacHolding.setBarcode(item.getBarcode());
    rtacHolding.setCallNumber(mapCallNumber(holding, item));
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
    rtacHolding.setCreatedAt(Date.from(Instant.now()));
    return rtacHolding;
  }

  public RtacHolding mapFrom(HoldingsRecord holding) {
    var rtacHolding = new RtacHolding();
    rtacHolding.setId(holding.getId());
    rtacHolding.setType(TypeEnum.HOLDING);
    rtacHolding.setInstanceId(holding.getInstanceId());
    rtacHolding.setHoldingsId(holding.getId());
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
    rtacHolding.setCreatedAt(Date.from(Instant.now()));
    return rtacHolding;
  }

  public RtacHolding mapFrom(HoldingsRecord holding, Piece piece) {
    var rtacHolding = new RtacHolding();
    rtacHolding.setId(piece.getId());
    rtacHolding.setType(TypeEnum.PIECE);
    rtacHolding.setInstanceId(holding.getInstanceId());
    rtacHolding.setHoldingsId(holding.getId());
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
    rtacHolding.setCreatedAt(Date.from(Instant.now()));
    return rtacHolding;
  }

  public RtacHolding mapForItemTypeFrom(RtacHolding existingRtacHolding, HoldingsRecord holding) {
    var newRtacHolding = new RtacHolding();
    newRtacHolding.setId(existingRtacHolding.getId());
    newRtacHolding.setType(TypeEnum.ITEM);
    newRtacHolding.setInstanceId(holding.getInstanceId());
    newRtacHolding.setHoldingsId(holding.getId());
    newRtacHolding.setBarcode(existingRtacHolding.getBarcode());
    newRtacHolding.setCallNumber(existingRtacHolding.getCallNumber());
    newRtacHolding.setHoldingsCopyNumber(holding.getCopyNumber());
    newRtacHolding.setItemCopyNumber(existingRtacHolding.getItemCopyNumber());
    newRtacHolding.setVolume(existingRtacHolding.getVolume());
    newRtacHolding.setEffectiveShelvingOrder(existingRtacHolding.getEffectiveShelvingOrder());
    newRtacHolding.setStatus(existingRtacHolding.getStatus());
    newRtacHolding.setSuppressFromDiscovery(existingRtacHolding.getSuppressFromDiscovery());
    newRtacHolding.setLocation(existingRtacHolding.getLocation());
    newRtacHolding.setLibrary(existingRtacHolding.getLibrary());
    newRtacHolding.setMaterialType(existingRtacHolding.getMaterialType());
    newRtacHolding.setTemporaryLoanType(existingRtacHolding.getTemporaryLoanType());
    newRtacHolding.setPermanentLoanType(existingRtacHolding.getPermanentLoanType());
    newRtacHolding.setHoldingsStatements(mapArraySafe(holding.getHoldingsStatements(), this::mapHoldingsStatementFrom));
    newRtacHolding.setHoldingsStatementsForIndexes(mapArraySafe(holding.getHoldingsStatementsForIndexes(), this::mapHoldingsStatementFrom));
    newRtacHolding.setHoldingsStatementsForSupplements(mapArraySafe(holding.getHoldingsStatementsForSupplements(), this::mapHoldingsStatementFrom));
    newRtacHolding.setNotes(mapHoldingsNotesFrom(holding));
    return newRtacHolding;
  }

  public RtacHolding mapForItemTypeFrom(RtacHolding existingRtacHolding, Item item) {
    var newRtacHolding = new RtacHolding();
    newRtacHolding.setId(item.getId());
    newRtacHolding.setType(TypeEnum.ITEM);
    newRtacHolding.setInstanceId(existingRtacHolding.getInstanceId());
    newRtacHolding.setHoldingsId(existingRtacHolding.getHoldingsId());
    newRtacHolding.setInstanceFormatIds(existingRtacHolding.getInstanceFormatIds());
    newRtacHolding.setBarcode(item.getBarcode());
    newRtacHolding.setCallNumber(mapCallNumber(existingRtacHolding, item));
    newRtacHolding.setHoldingsCopyNumber(existingRtacHolding.getHoldingsCopyNumber());
    newRtacHolding.setItemCopyNumber(item.getCopyNumber());
    newRtacHolding.setVolume(mapVolumeFrom(item));
    newRtacHolding.setEffectiveShelvingOrder(item.getEffectiveShelvingOrder());
    newRtacHolding.setStatus(item.getStatus().getName().getValue());
    newRtacHolding.setSuppressFromDiscovery(item.getDiscoverySuppress());
    newRtacHolding.setLocation(mapLocationFrom(item.getEffectiveLocationId()));
    newRtacHolding.setLibrary(mapLibraryFrom(item.getEffectiveLocationId()));
    newRtacHolding.setMaterialType(mapMaterialTypeFrom(item));
    newRtacHolding.setTemporaryLoanType(mapLoanTypeFrom(item.getTemporaryLoanTypeId()));
    newRtacHolding.setPermanentLoanType(mapLoanTypeFrom(item.getPermanentLoanTypeId()));
    newRtacHolding.setHoldingsStatements(existingRtacHolding.getHoldingsStatements());
    newRtacHolding.setHoldingsStatementsForIndexes(existingRtacHolding.getHoldingsStatementsForIndexes());
    newRtacHolding.setHoldingsStatementsForSupplements(existingRtacHolding.getHoldingsStatementsForSupplements());
    newRtacHolding.setNotes(existingRtacHolding.getNotes());
    return newRtacHolding;
  }

  public RtacHolding mapForBoundWithItemTypeFrom(RtacHolding holdingsRtacHolding, RtacHolding itemRtacHolding) {
    var newRtacHolding = new RtacHolding();
    newRtacHolding.setId(itemRtacHolding.getId());
    newRtacHolding.setType(TypeEnum.ITEM);
    newRtacHolding.setInstanceId(holdingsRtacHolding.getInstanceId());
    newRtacHolding.setInstanceFormatIds(holdingsRtacHolding.getInstanceFormatIds());
    newRtacHolding.setHoldingsId(holdingsRtacHolding.getHoldingsId());
    newRtacHolding.setBarcode(itemRtacHolding.getBarcode());
    newRtacHolding.setCallNumber(itemRtacHolding.getCallNumber());
    newRtacHolding.setHoldingsCopyNumber(holdingsRtacHolding.getHoldingsCopyNumber());
    newRtacHolding.setItemCopyNumber(itemRtacHolding.getItemCopyNumber());
    newRtacHolding.setVolume(itemRtacHolding.getVolume());
    newRtacHolding.setEffectiveShelvingOrder(itemRtacHolding.getEffectiveShelvingOrder());
    newRtacHolding.setStatus(itemRtacHolding.getStatus());
    newRtacHolding.setSuppressFromDiscovery(itemRtacHolding.getSuppressFromDiscovery());
    newRtacHolding.setLocation(itemRtacHolding.getLocation());
    newRtacHolding.setLibrary(itemRtacHolding.getLibrary());
    newRtacHolding.setMaterialType(itemRtacHolding.getMaterialType());
    newRtacHolding.setTemporaryLoanType(itemRtacHolding.getTemporaryLoanType());
    newRtacHolding.setPermanentLoanType(itemRtacHolding.getPermanentLoanType());
    newRtacHolding.setHoldingsStatements(holdingsRtacHolding.getHoldingsStatements());
    newRtacHolding.setHoldingsStatementsForIndexes(holdingsRtacHolding.getHoldingsStatementsForIndexes());
    newRtacHolding.setHoldingsStatementsForSupplements(holdingsRtacHolding.getHoldingsStatementsForSupplements());
    newRtacHolding.setNotes(holdingsRtacHolding.getNotes());
    newRtacHolding.setIsBoundWith(true);
    return newRtacHolding;
  }

  public RtacHolding mapForPieceTypeFrom(RtacHolding existingRtacHolding, HoldingsRecord holding) {
    var newRtacHolding = new RtacHolding();
    newRtacHolding.setId(existingRtacHolding.getId());
    newRtacHolding.setType(TypeEnum.PIECE);
    newRtacHolding.setInstanceId(holding.getInstanceId());
    newRtacHolding.setHoldingsId(holding.getId());
    newRtacHolding.setCallNumber(holding.getCallNumber());
    newRtacHolding.setHoldingsCopyNumber(existingRtacHolding.getHoldingsCopyNumber());
    newRtacHolding.setStatus(existingRtacHolding.getStatus());
    newRtacHolding.setVolume(existingRtacHolding.getVolume());
    newRtacHolding.setSuppressFromDiscovery(holding.getDiscoverySuppress());
    newRtacHolding.setLocation(mapLocationFrom(holding.getEffectiveLocationId()));
    newRtacHolding.setLibrary(mapLibraryFrom(holding.getEffectiveLocationId()));
    newRtacHolding.setHoldingsStatements(mapArraySafe(holding.getHoldingsStatements(), this::mapHoldingsStatementFrom));
    newRtacHolding.setHoldingsStatementsForIndexes(mapArraySafe(holding.getHoldingsStatementsForIndexes(), this::mapHoldingsStatementFrom));
    newRtacHolding.setHoldingsStatementsForSupplements(mapArraySafe(holding.getHoldingsStatementsForSupplements(), this::mapHoldingsStatementFrom));
    newRtacHolding.setNotes(mapHoldingsNotesFrom(holding));
    return newRtacHolding;
  }

  public RtacHolding mapForPieceTypeFrom(RtacHolding existingRtacHolding, Piece piece) {
    var newRtacHolding = new RtacHolding();
    newRtacHolding.setId(piece.getId());
    newRtacHolding.setType(TypeEnum.PIECE);
    newRtacHolding.setInstanceId(existingRtacHolding.getInstanceId());
    newRtacHolding.setInstanceFormatIds(existingRtacHolding.getInstanceFormatIds());
    newRtacHolding.setHoldingsId(existingRtacHolding.getId());
    newRtacHolding.setCallNumber(existingRtacHolding.getCallNumber());
    newRtacHolding.setHoldingsCopyNumber(piece.getCopyNumber());
    newRtacHolding.setStatus(piece.getReceivingStatus().getValue());
    newRtacHolding.setVolume(mapVolumeFrom(piece));
    newRtacHolding.setSuppressFromDiscovery(existingRtacHolding.getSuppressFromDiscovery());
    newRtacHolding.setLocation(existingRtacHolding.getLocation());
    newRtacHolding.setLibrary(existingRtacHolding.getLibrary());
    newRtacHolding.setHoldingsStatements(existingRtacHolding.getHoldingsStatements());
    newRtacHolding.setHoldingsStatementsForIndexes(existingRtacHolding.getHoldingsStatementsForIndexes());
    newRtacHolding.setHoldingsStatementsForSupplements(existingRtacHolding.getHoldingsStatementsForSupplements());
    newRtacHolding.setNotes(existingRtacHolding.getNotes());
    newRtacHolding.setCreatedAt(Date.from(Instant.now()));
    return newRtacHolding;
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
    if (StringUtils.isBlank(item.getMaterialTypeId())) {
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
      status = holding.getHoldingsStatements().getFirst().getStatement();
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

  private String mapCallNumber(RtacHolding existingRtacHolding, Item item) {
    if (nonNull(item.getEffectiveCallNumberComponents())) {
      return item.getEffectiveCallNumberComponents().getCallNumber();
    }
    if (StringUtils.isNotBlank(item.getItemLevelCallNumber())) {
      return item.getItemLevelCallNumber();
    }
    return existingRtacHolding.getCallNumber();
  }

  private String mapCallNumber(HoldingsRecord holding, Item item) {
    if (nonNull(item.getEffectiveCallNumberComponents())) {
      return item.getEffectiveCallNumberComponents().getCallNumber();
    }
    if (StringUtils.isNotBlank(item.getItemLevelCallNumber())) {
      return item.getItemLevelCallNumber();
    }
    return holding.getCallNumber();
  }

}
