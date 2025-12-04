package org.folio.rtaccache.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.Location;
import org.folio.rtaccache.domain.dto.Loclib;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.domain.dto.RtacHoldingLibrary;
import org.folio.rtaccache.domain.dto.RtacHoldingLocation;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class RtacHoldingBulkRepositoryTest extends BaseIntegrationTest {

  @Autowired
  private RtacHoldingBulkRepository rtacHoldingBulkRepository;
  @Autowired
  private RtacHoldingRepository rtacHoldingRepository;
  @MockitoSpyBean
  private FolioExecutionContext folioExecutionContext;
  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String ITEM_ID_1 = UUID.randomUUID().toString();
  private static final String ITEM_ID_2 = UUID.randomUUID().toString();
  private static final String LOCATION_ID_1 = UUID.randomUUID().toString();
  private static final String LOCATION_ID_2 = UUID.randomUUID().toString();
  private static final String LIBRARY_ID_1 = UUID.randomUUID().toString();
  private static final String LIBRARY_ID_2 = UUID.randomUUID().toString();

  @AfterEach
  void tearDown() {
    rtacHoldingRepository.deleteAll();
  }

  @Test
  void bulkUpsert_insertsRecords() throws SQLException, JsonProcessingException {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    var rtacHolding1 = getRtacHolding(ITEM_ID_1, INSTANCE_ID);
    var rtacHolding2 = getRtacHolding(ITEM_ID_2, INSTANCE_ID);
    var rtacHoldingEntity1 = new RtacHoldingEntity(RtacHoldingId.from(rtacHolding1), rtacHolding1, Instant.now());
    var rtacHoldingEntity2 = new RtacHoldingEntity(RtacHoldingId.from(rtacHolding2), rtacHolding2, Instant.now());
    var holdings = List.of(rtacHoldingEntity1, rtacHoldingEntity2);

    rtacHoldingBulkRepository.bulkUpsert(holdings);

    var retrieved = rtacHoldingRepository.findAll();
    assertEquals(2, retrieved.size());
    assertIterableEquals(holdings.stream().map(RtacHoldingEntity::getId).toList(),
      retrieved.stream().map(RtacHoldingEntity::getId).toList());
  }

  @Test
  void bulkUpsert_updatesRecords() throws SQLException, JsonProcessingException {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    var rtacHolding1 = getRtacHolding(ITEM_ID_1, INSTANCE_ID);
    var rtacHolding2 = getRtacHolding(ITEM_ID_2, INSTANCE_ID);
    rtacHolding1.setBarcode("test");
    rtacHolding2.setBarcode("test");
    var rtacHoldingEntity1 = new RtacHoldingEntity(RtacHoldingId.from(rtacHolding1), rtacHolding1, Instant.now());
    var rtacHoldingEntity2 = new RtacHoldingEntity(RtacHoldingId.from(rtacHolding2), rtacHolding2, Instant.now());
    var holdings = List.of(rtacHoldingEntity1, rtacHoldingEntity2);

    rtacHoldingBulkRepository.bulkUpsert(holdings);

    var retrieved = rtacHoldingRepository.findAll();
    assertEquals(2, retrieved.size());
    assertIterableEquals(holdings.stream().map(RtacHoldingEntity::getId).toList(),
      retrieved.stream().map(RtacHoldingEntity::getId).toList());
    assertEquals("test", retrieved.get(0).getRtacHolding().getBarcode());
    assertEquals("test", retrieved.get(1).getRtacHolding().getBarcode());
  }

  @Test
  void bulkUpdateLocationData_updatesEmbeddedLocation() throws SQLException, JsonProcessingException {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    var loc1 = getRtacHoldingLocation(LOCATION_ID_1, "OLD_CODE", "Old Name");
    var loc2 = getRtacHoldingLocation(LOCATION_ID_2, "OTHER_CODE", "Other Name");
    var rtacHolding1 = getRtacHolding(ITEM_ID_1, INSTANCE_ID);
    var rtacHolding2 = getRtacHolding(ITEM_ID_2, INSTANCE_ID);
    rtacHolding1.setLocation(loc1);
    rtacHolding2.setLocation(loc2);
    var holdings = List.of(
      new RtacHoldingEntity(RtacHoldingId.from(rtacHolding1), rtacHolding1, Instant.now()),
      new RtacHoldingEntity(RtacHoldingId.from(rtacHolding2), rtacHolding2, Instant.now())
    );
    rtacHoldingRepository.saveAll(holdings);
    var updatedLocation = getLocation();

    rtacHoldingBulkRepository.bulkUpdateLocationData(updatedLocation);

    var retrieved = rtacHoldingRepository.findAll();
    assertEquals(2, retrieved.size());
    var updated = findByLocationId(retrieved, LOCATION_ID_1);
    assertEquals("UPDATED_CODE", updated.getRtacHolding().getLocation().getCode());
    assertEquals("Updated Name", updated.getRtacHolding().getLocation().getName());
    var unchanged = findByLocationId(retrieved, LOCATION_ID_2);
    assertEquals("OTHER_CODE", unchanged.getRtacHolding().getLocation().getCode());
    assertEquals("Other Name", unchanged.getRtacHolding().getLocation().getName());
  }

  @Test
  void bulkUpdateLibraryData_updatesEmbeddedLibrary() throws SQLException, JsonProcessingException {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    var lib1 = getRtacHoldingLibrary(LIBRARY_ID_1, "OLD_CODE", "Old Name");
    var lib2 = getRtacHoldingLibrary(LIBRARY_ID_2, "OTHER_CODE", "Other Name");
    var rtacHolding1 = getRtacHolding(ITEM_ID_1, INSTANCE_ID);
    var rtacHolding2 = getRtacHolding(ITEM_ID_2, INSTANCE_ID);
    rtacHolding1.setLibrary(lib1);
    rtacHolding2.setLibrary(lib2);
    var holdings = List.of(
      new RtacHoldingEntity(RtacHoldingId.from(rtacHolding1), rtacHolding1, Instant.now()),
      new RtacHoldingEntity(RtacHoldingId.from(rtacHolding2), rtacHolding2, Instant.now())
    );
    rtacHoldingRepository.saveAll(holdings);
    var updatedLib = getLibrary();

    rtacHoldingBulkRepository.bulkUpdateLibraryData(updatedLib);

    var retrieved = rtacHoldingRepository.findAll();
    assertEquals(2, retrieved.size());
    // matching id updated
    var updated = findByLibraryId(retrieved, LIBRARY_ID_1);
    assertEquals("UPDATED_CODE", updated.getRtacHolding().getLibrary().getCode());
    assertEquals("Updated Name", updated.getRtacHolding().getLibrary().getName());
    // non-matching id unchanged
    var unchanged = findByLibraryId(retrieved, LIBRARY_ID_2);
    assertEquals("OTHER_CODE", unchanged.getRtacHolding().getLibrary().getCode());
    assertEquals("Other Name", unchanged.getRtacHolding().getLibrary().getName());
  }

  private RtacHolding getRtacHolding(String itemId1, String instanceId) {
    var rtacHolding1 = new RtacHolding();
    rtacHolding1.setId(itemId1);
    rtacHolding1.setType(TypeEnum.ITEM);
    rtacHolding1.setInstanceId(instanceId);
    return rtacHolding1;
  }

  private RtacHoldingEntity findByLocationId(List<RtacHoldingEntity> retrieved, String locationId) {
    return retrieved.stream()
      .filter(h -> h.getRtacHolding()
        .getLocation()
        .getId().equals(locationId))
      .findFirst()
      .orElseThrow();
  }

  private RtacHoldingEntity findByLibraryId(List<RtacHoldingEntity> retrieved, String libraryId) {
    return retrieved.stream()
      .filter(h -> h.getRtacHolding()
        .getLibrary()
        .getId().equals(libraryId))
      .findFirst()
      .orElseThrow();
  }

  private Location getLocation() {
    return new Location()
      .id(LOCATION_ID_1)
      .code("UPDATED_CODE")
      .name("Updated Name");
  }

  private Loclib getLibrary() {
    return new Loclib()
      .id(LIBRARY_ID_1)
      .code("UPDATED_CODE")
      .name("Updated Name");
  }

  private RtacHoldingLocation getRtacHoldingLocation(String id, String code, String name) {
    return new RtacHoldingLocation()
      .id(id)
      .code(code)
      .name(name);
  }

  private RtacHoldingLibrary getRtacHoldingLibrary(String id, String code, String name) {
    return new RtacHoldingLibrary()
      .id(id)
      .code(code)
      .name(name);
  }


}
