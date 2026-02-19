package org.folio.rtaccache.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.rtaccache.domain.dto.HoldingsNoteType;
import org.folio.rtaccache.domain.dto.HoldingsRecord;
import org.folio.rtaccache.domain.dto.HoldingsStatement;
import org.folio.rtaccache.domain.dto.Item;
import org.folio.rtaccache.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.rtaccache.domain.dto.ItemStatus;
import org.folio.rtaccache.domain.dto.ItemStatus.NameEnum;
import org.folio.rtaccache.domain.dto.LoanType;
import org.folio.rtaccache.domain.dto.Location;
import org.folio.rtaccache.domain.dto.Loclib;
import org.folio.rtaccache.domain.dto.MaterialType;
import org.folio.rtaccache.domain.dto.Piece;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RtacHoldingMappingServiceTest {

  @Mock
  private InventoryReferenceDataService inventoryReferenceDataService;

  @InjectMocks
  private RtacHoldingMappingService rtacHoldingMappingService;

  private Map<String, Location> locationsMap;
  private Map<String, Loclib> libraryMap;
  private Map<String, MaterialType> materialTypesMap;
  private Map<String, LoanType> loanTypesMap;
  private Map<String, HoldingsNoteType> holdingsNoteTypesMap;

  @BeforeEach
  void setUp() {
    locationsMap = new HashMap<>();
    libraryMap = new HashMap<>();
    materialTypesMap = new HashMap<>();
    loanTypesMap = new HashMap<>();
    holdingsNoteTypesMap = new HashMap<>();

    lenient().when(inventoryReferenceDataService.getLocationsMap()).thenReturn(locationsMap);
    lenient().when(inventoryReferenceDataService.getLibraryMap()).thenReturn(libraryMap);
    lenient().when(inventoryReferenceDataService.getMaterialTypesMap()).thenReturn(materialTypesMap);
    lenient().when(inventoryReferenceDataService.getLoanTypesMap()).thenReturn(loanTypesMap);
    lenient().when(inventoryReferenceDataService.getHoldingsNoteTypesMap()).thenReturn(holdingsNoteTypesMap);
  }

  @Test
  void testMapItemToRtacHolding_BasicMapping() {
    var instanceId = UUID.randomUUID().toString();
    var item = new Item();
    item.setId(UUID.randomUUID().toString());
    item.setBarcode("123456");
    item.setMaterialTypeId(UUID.randomUUID().toString());

    var holdingId = UUID.randomUUID().toString();

    var holding = new HoldingsRecord();
    holding.setInstanceId(instanceId);
    holding.setId(holdingId);

    var status = new ItemStatus();
    status.setName(NameEnum.AVAILABLE);
    item.setStatus(status);

    var result = rtacHoldingMappingService.mapFrom(holding, item);

    assertNotNull(result);
    assertEquals(instanceId, result.getInstanceId());
    assertEquals(item.getId(), result.getId());
    assertEquals(RtacHolding.TypeEnum.ITEM, result.getType());
    assertEquals("123456", result.getBarcode());
    assertEquals(status.getName().getValue(), result.getStatus());
    verify(inventoryReferenceDataService, times(1)).getLocationsMap();
  }

  @Test
  void testMapItemToRtacHolding_WithLocation() {
    var locationId = UUID.randomUUID().toString();
    var libraryId = UUID.randomUUID().toString();
    var instanceId = UUID.randomUUID().toString();
    var holdingId = UUID.randomUUID().toString();

    var holding = new HoldingsRecord();
    holding.setInstanceId(instanceId);
    holding.setId(holdingId);
    holding.setCallNumber("PS3545.H16");

    var location = new Location();
    location.setId(locationId);
    location.setName("Main Library");
    location.setLibraryId(libraryId);
    locationsMap.put(locationId, location);

    var library = new Loclib();
    library.setId(libraryId);
    library.setName("Central Library");
    libraryMap.put(libraryId, library);

    var status = new ItemStatus();
    status.setName(NameEnum.AVAILABLE);

    var item = new Item();
    item.setId(UUID.randomUUID().toString());
    item.setEffectiveLocationId(locationId);
    item.setStatus(status);
    item.setMaterialTypeId(UUID.randomUUID().toString());

    var result = rtacHoldingMappingService.mapFrom(holding, item);

    assertNotNull(result.getLocation());
    assertEquals("Main Library", result.getLocation().getName());
    assertNotNull(result.getLibrary());
    assertEquals("Central Library", result.getLibrary().getName());
  }

  @Test
  void testMapItemToRtacHolding_withItemCallNumber() {
    var instanceId = UUID.randomUUID().toString();
    var holdingId = UUID.randomUUID().toString();

    var holding = new HoldingsRecord();
    holding.setInstanceId(instanceId);
    holding.setId(holdingId);
    holding.setCallNumber("PS3545.H16");

    var status = new ItemStatus();
    status.setName(NameEnum.AVAILABLE);

    var item = new Item();
    item.setId(UUID.randomUUID().toString());
    var callNumberComponents = new ItemEffectiveCallNumberComponents();
    callNumberComponents.setCallNumber("new-call-number");
    item.setEffectiveCallNumberComponents(callNumberComponents);
    item.setStatus(status);

    var result = rtacHoldingMappingService.mapFrom(holding, item);

    assertEquals("new-call-number", result.getCallNumber());
  }

  @Test
  void testMapItemToRtacHolding_withHoldingsCallNumber() {
    var instanceId = UUID.randomUUID().toString();
    var holdingId = UUID.randomUUID().toString();

    var holding = new HoldingsRecord();
    holding.setInstanceId(instanceId);
    holding.setId(holdingId);
    holding.setCallNumber("PS3545.H16");

    var status = new ItemStatus();
    status.setName(NameEnum.AVAILABLE);

    var item = new Item();
    item.setId(UUID.randomUUID().toString());
    item.setStatus(status);

    var result = rtacHoldingMappingService.mapFrom(holding, item);

    assertEquals("PS3545.H16", result.getCallNumber());
  }

  @Test
  void testMapPieceToRtacHolding_BasicMapping() {
    var instanceId = UUID.randomUUID().toString();
    var holdingId = UUID.randomUUID().toString();

    var holding = new HoldingsRecord();
    holding.setInstanceId(instanceId);
    holding.setId(holdingId);
    holding.setCallNumber("PS3545.H16");
    var piece = new Piece();
    piece.setId(UUID.randomUUID().toString());
    piece.setReceivingStatus(Piece.ReceivingStatusEnum.RECEIVED);

    var result = rtacHoldingMappingService.mapFrom(holding, piece);

    assertNotNull(result);
    assertEquals(instanceId, result.getInstanceId());
    assertEquals(piece.getId(), result.getId());
    assertEquals(RtacHolding.TypeEnum.PIECE, result.getType());
    assertEquals("PS3545.H16", result.getCallNumber());
    assertEquals(Piece.ReceivingStatusEnum.RECEIVED.toString(), result.getStatus());
  }

  @Test
  void testMapHoldingToRtacHolding_WithHoldingsStatements() {
    var instanceId = UUID.randomUUID().toString();
    var holdingId = UUID.randomUUID().toString();

    var holding = new HoldingsRecord();
    holding.setInstanceId(instanceId);
    holding.setId(holdingId);
    holding.setCallNumber("PS3545.H16");

    var statement = new HoldingsStatement();
    statement.setStatement("v.1-10");
    statement.setNote("Complete set");
    holding.setHoldingsStatements(List.of(statement));

    var result = rtacHoldingMappingService.mapFrom(holding);

    assertNotNull(result);
    assertEquals(RtacHolding.TypeEnum.HOLDING, result.getType());
    assertEquals(1, result.getHoldingsStatements().size());
    assertEquals("v.1-10", result.getHoldingsStatements().get(0).getStatement());
    assertEquals("Complete set", result.getHoldingsStatements().get(0).getNote());
  }

  @Test
  void testMapForItemTypeFromHolding() {
    var instanceId = UUID.randomUUID().toString();
    var holdingId = UUID.randomUUID().toString();

    var existingRtacHolding = new RtacHolding();
    existingRtacHolding.setId(UUID.randomUUID().toString());
    existingRtacHolding.setBarcode("barcode-123");
    existingRtacHolding.setCallNumber("existing-call-number");

    var holding = new HoldingsRecord();
    holding.setInstanceId(instanceId);
    holding.setId(holdingId);
    holding.setCopyNumber("copy-1");

    var result = rtacHoldingMappingService.mapForItemTypeFrom(existingRtacHolding, holding);

    assertNotNull(result);
    assertEquals(existingRtacHolding.getId(), result.getId());
    assertEquals(RtacHolding.TypeEnum.ITEM, result.getType());
    assertEquals(instanceId, result.getInstanceId());
    assertEquals(holdingId, result.getHoldingsId());
    assertEquals("barcode-123", result.getBarcode());
    assertEquals("existing-call-number", result.getCallNumber());
    assertEquals("copy-1", result.getHoldingsCopyNumber());
  }

  @Test
  void testMapForItemTypeFromItem() {
    var instanceId = UUID.randomUUID().toString();
    var holdingId = UUID.randomUUID().toString();
    var itemId = UUID.randomUUID().toString();

    var existingRtacHolding = new RtacHolding();
    existingRtacHolding.setInstanceId(instanceId);
    existingRtacHolding.setHoldingsId(holdingId);
    existingRtacHolding.setInstanceFormatIds(List.of("fmt-1", "fmt-2")); // cover copy

    var item = new Item();
    item.setId(itemId);
    item.setBarcode("new-barcode");
    var callNumberComponents = new ItemEffectiveCallNumberComponents();
    callNumberComponents.setCallNumber("new-call-number");
    item.setEffectiveCallNumberComponents(callNumberComponents);
    item.setMaterialTypeId(UUID.randomUUID().toString());
    var status = new ItemStatus();
    status.setName(NameEnum.AVAILABLE);
    item.setStatus(status);

    var result = rtacHoldingMappingService.mapForItemTypeFrom(existingRtacHolding, item);

    assertNotNull(result);
    assertEquals(itemId, result.getId());
    assertEquals(RtacHolding.TypeEnum.ITEM, result.getType());
    assertEquals(instanceId, result.getInstanceId());
    assertEquals(holdingId, result.getHoldingsId());
    assertEquals("new-barcode", result.getBarcode());
    assertEquals("new-call-number", result.getCallNumber());
    assertEquals(NameEnum.AVAILABLE.getValue(), result.getStatus());
    assertEquals(List.of("fmt-1", "fmt-2"), result.getInstanceFormatIds()); // asserts line 136
  }

  @Test
  void testMapForItemTypeFromItem_shouldCopyCallNumberFromHolding() {
    var instanceId = UUID.randomUUID().toString();
    var holdingId = UUID.randomUUID().toString();
    var itemId = UUID.randomUUID().toString();

    var existingRtacHolding = new RtacHolding();
    existingRtacHolding.setInstanceId(instanceId);
    existingRtacHolding.setHoldingsId(holdingId);
    existingRtacHolding.setCallNumber("holding-call-number");

    var item = new Item();
    item.setId(itemId);
    var status = new ItemStatus();
    status.setName(NameEnum.AVAILABLE);
    item.setStatus(status);

    var result = rtacHoldingMappingService.mapForItemTypeFrom(existingRtacHolding, item);

    assertNotNull(result);
    assertEquals(itemId, result.getId());
    assertEquals(RtacHolding.TypeEnum.ITEM, result.getType());
    assertEquals(instanceId, result.getInstanceId());
    assertEquals(holdingId, result.getHoldingsId());
    assertEquals("holding-call-number", result.getCallNumber());
    assertEquals(NameEnum.AVAILABLE.getValue(), result.getStatus());
  }

  @Test
  void testMapForPieceTypeFromHolding() {
    var instanceId = UUID.randomUUID().toString();
    var holdingId = UUID.randomUUID().toString();

    var existingRtacHolding = new RtacHolding();
    existingRtacHolding.setId(UUID.randomUUID().toString());
    existingRtacHolding.setStatus(Piece.ReceivingStatusEnum.EXPECTED.getValue());

    var holding = new HoldingsRecord();
    holding.setInstanceId(instanceId);
    holding.setId(holdingId);
    holding.setCallNumber("holding-call-number");

    var result = rtacHoldingMappingService.mapForPieceTypeFrom(existingRtacHolding, holding);

    assertNotNull(result);
    assertEquals(existingRtacHolding.getId(), result.getId());
    assertEquals(RtacHolding.TypeEnum.PIECE, result.getType());
    assertEquals(instanceId, result.getInstanceId());
    assertEquals(holdingId, result.getHoldingsId());
    assertEquals("holding-call-number", result.getCallNumber());
    assertEquals(Piece.ReceivingStatusEnum.EXPECTED.getValue(), result.getStatus());
  }

  @Test
  void testMapForBoundWithItemTypeFrom_CopiesInstanceFormatIdsFromHoldingsHolding() {
    var holdingsRtacHolding = new RtacHolding();
    holdingsRtacHolding.setInstanceId(UUID.randomUUID().toString());
    holdingsRtacHolding.setHoldingsId(UUID.randomUUID().toString());
    holdingsRtacHolding.setHoldingsCopyNumber("h-copy-1");
    holdingsRtacHolding.setInstanceFormatIds(List.of("bfmt-1")); // cover copy

    var itemRtacHolding = new RtacHolding();
    itemRtacHolding.setId(UUID.randomUUID().toString());
    itemRtacHolding.setBarcode("i-barcode");
    itemRtacHolding.setCallNumber("i-call");
    itemRtacHolding.setItemCopyNumber("i-copy-1");
    itemRtacHolding.setStatus("Available");
    itemRtacHolding.setSuppressFromDiscovery(false);

    var result = rtacHoldingMappingService.mapForBoundWithItemTypeFrom(holdingsRtacHolding, itemRtacHolding);

    assertNotNull(result);
    assertEquals(RtacHolding.TypeEnum.ITEM, result.getType());
    assertEquals(itemRtacHolding.getId(), result.getId());
    assertEquals(holdingsRtacHolding.getInstanceId(), result.getInstanceId());
    assertEquals(holdingsRtacHolding.getHoldingsId(), result.getHoldingsId());
    assertEquals(List.of("bfmt-1"), result.getInstanceFormatIds()); // asserts line 162
    assertEquals(true, result.getIsBoundWith());
  }

}
