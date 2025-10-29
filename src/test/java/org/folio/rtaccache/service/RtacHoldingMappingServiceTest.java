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
import org.folio.rtaccache.domain.dto.ItemStatus;
import org.folio.rtaccache.domain.dto.ItemStatus.NameEnum;
import org.folio.rtaccache.domain.dto.Loantype;
import org.folio.rtaccache.domain.dto.Location;
import org.folio.rtaccache.domain.dto.Loclib;
import org.folio.rtaccache.domain.dto.Materialtype;
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
  private Map<String, Materialtype> materialTypesMap;
  private Map<String, Loantype> loanTypesMap;
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
    String instanceId = UUID.randomUUID().toString();
    Item item = new Item();
    item.setId(UUID.randomUUID().toString());
    item.setBarcode("123456");
    item.setItemLevelCallNumber("PS3545.H16");

    String holdingId = UUID.randomUUID().toString();

    HoldingsRecord holding = new HoldingsRecord();
    holding.setInstanceId(instanceId);
    holding.setId(holdingId);
    holding.setCallNumber("PS3545.H16");

    ItemStatus status = new ItemStatus();
    status.setName(NameEnum.AVAILABLE);
    item.setStatus(status);

    RtacHolding result = rtacHoldingMappingService.mapFrom(holding, item);

    assertNotNull(result);
    assertEquals(instanceId, result.getInstanceId());
    assertEquals(item.getId(), result.getId());
    assertEquals(RtacHolding.TypeEnum.ITEM, result.getType());
    assertEquals("123456", result.getBarcode());
    assertEquals("PS3545.H16", result.getCallNumber());
    assertEquals(status.getName().getValue(), result.getStatus());
    verify(inventoryReferenceDataService, times(1)).getLocationsMap();
  }

  @Test
  void testMapItemToRtacHolding_WithLocation() {
    String locationId = UUID.randomUUID().toString();
    String libraryId = UUID.randomUUID().toString();
    String instanceId = UUID.randomUUID().toString();
    String holdingId = UUID.randomUUID().toString();

    HoldingsRecord holding = new HoldingsRecord();
    holding.setInstanceId(instanceId);
    holding.setId(holdingId);
    holding.setCallNumber("PS3545.H16");

    Location location = new Location();
    location.setId(locationId);
    location.setName("Main Library");
    location.setLibraryId(libraryId);
    locationsMap.put(locationId, location);

    Loclib library = new Loclib();
    library.setId(libraryId);
    library.setName("Central Library");
    libraryMap.put(libraryId, library);

    ItemStatus status = new ItemStatus();
    status.setName(NameEnum.AVAILABLE);

    Item item = new Item();
    item.setId(UUID.randomUUID().toString());
    item.setEffectiveLocationId(locationId);
    item.setStatus(status);

    RtacHolding result = rtacHoldingMappingService.mapFrom(holding, item);

    assertNotNull(result.getLocation());
    assertEquals("Main Library", result.getLocation().getName());
    assertNotNull(result.getLibrary());
    assertEquals("Central Library", result.getLibrary().getName());
  }

  @Test
  void testMapPieceToRtacHolding_BasicMapping() {
    String instanceId = UUID.randomUUID().toString();
    String holdingId = UUID.randomUUID().toString();

    HoldingsRecord holding = new HoldingsRecord();
    holding.setInstanceId(instanceId);
    holding.setId(holdingId);
    holding.setCallNumber("PS3545.H16");
    Piece piece = new Piece();
    piece.setId(UUID.randomUUID().toString());
    piece.setReceivingStatus(Piece.ReceivingStatusEnum.RECEIVED);

    RtacHolding result = rtacHoldingMappingService.mapFrom(holding, piece);

    assertNotNull(result);
    assertEquals(instanceId, result.getInstanceId());
    assertEquals(piece.getId(), result.getId());
    assertEquals(RtacHolding.TypeEnum.PIECE, result.getType());
    assertEquals("PS3545.H16", result.getCallNumber());
    assertEquals(Piece.ReceivingStatusEnum.RECEIVED.toString(), result.getStatus());
  }

  @Test
  void testMapHoldingToRtacHolding_WithHoldingsStatements() {
    String instanceId = UUID.randomUUID().toString();
    String holdingId = UUID.randomUUID().toString();

    HoldingsRecord holding = new HoldingsRecord();
    holding.setInstanceId(instanceId);
    holding.setId(holdingId);
    holding.setCallNumber("PS3545.H16");

    HoldingsStatement statement = new HoldingsStatement();
    statement.setStatement("v.1-10");
    statement.setNote("Complete set");
    holding.setHoldingsStatements(List.of(statement));

    RtacHolding result = rtacHoldingMappingService.mapFrom(holding);

    assertNotNull(result);
    assertEquals(RtacHolding.TypeEnum.HOLDING, result.getType());
    assertEquals(1, result.getHoldingsStatements().size());
    assertEquals("v.1-10", result.getHoldingsStatements().get(0).getStatement());
    assertEquals("Complete set", result.getHoldingsStatements().get(0).getNote());
  }

}
