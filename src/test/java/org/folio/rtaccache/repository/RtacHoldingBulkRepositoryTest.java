package org.folio.rtaccache.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.LoanType;
import org.folio.rtaccache.domain.dto.Location;
import org.folio.rtaccache.domain.dto.Loclib;
import org.folio.rtaccache.domain.dto.MaterialType;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.domain.dto.RtacHoldingLibrary;
import org.folio.rtaccache.domain.dto.RtacHoldingLocation;
import org.folio.rtaccache.domain.dto.RtacHoldingMaterialType;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RtacHoldingBulkRepositoryTest extends BaseIntegrationTest {

  @Autowired
  private RtacHoldingBulkRepository rtacHoldingBulkRepository;
  @Autowired
  private RtacHoldingRepository rtacHoldingRepository;

  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String ITEM_ID_1 = UUID.randomUUID().toString();
  private static final String ITEM_ID_2 = UUID.randomUUID().toString();
  private static final String LOCATION_ID_1 = UUID.randomUUID().toString();
  private static final String LOCATION_ID_2 = UUID.randomUUID().toString();
  private static final String LIBRARY_ID_1 = UUID.randomUUID().toString();
  private static final String LIBRARY_ID_2 = UUID.randomUUID().toString();
  private static final String MATERIAL_TYPE_ID_1 = UUID.randomUUID().toString();
  private static final String MATERIAL_TYPE_ID_2 = UUID.randomUUID().toString();

  @AfterEach
  void tearDown() {
    withinTenant(TestConstant.TEST_TENANT, rtacHoldingRepository::deleteAll);
  }

  @Test
  void bulkUpsert_insertsRecords() throws SQLException, JsonProcessingException {
    withinTenant(TestConstant.TEST_TENANT, () -> {
      var rtacHolding1 = getRtacHolding(ITEM_ID_1, INSTANCE_ID);
      var rtacHolding2 = getRtacHolding(ITEM_ID_2, INSTANCE_ID);
      var rtacHoldingEntity1 = new RtacHoldingEntity(RtacHoldingId.from(rtacHolding1), false, rtacHolding1, Instant.now());
      var rtacHoldingEntity2 = new RtacHoldingEntity(RtacHoldingId.from(rtacHolding2), false, rtacHolding2, Instant.now());
      var holdings = List.of(rtacHoldingEntity1, rtacHoldingEntity2);

      rtacHoldingBulkRepository.bulkUpsert(holdings);

      var retrieved = rtacHoldingRepository.findAll();
      assertEquals(2, retrieved.size());
      assertIterableEquals(holdings.stream().map(RtacHoldingEntity::getId).toList(),
        retrieved.stream().map(RtacHoldingEntity::getId).toList());
    });
  }

  @Test
  void bulkUpsert_updatesRecords() throws SQLException, JsonProcessingException {
    withinTenant(TestConstant.TEST_TENANT, () -> {
      var rtacHolding1 = getRtacHolding(ITEM_ID_1, INSTANCE_ID);
      var rtacHolding2 = getRtacHolding(ITEM_ID_2, INSTANCE_ID);
      rtacHolding1.setBarcode("test");
      rtacHolding2.setBarcode("test");
      var rtacHoldingEntity1 = new RtacHoldingEntity(RtacHoldingId.from(rtacHolding1), false, rtacHolding1, Instant.now());
      var rtacHoldingEntity2 = new RtacHoldingEntity(RtacHoldingId.from(rtacHolding2), false, rtacHolding2, Instant.now());
      var holdings = List.of(rtacHoldingEntity1, rtacHoldingEntity2);

      rtacHoldingBulkRepository.bulkUpsert(holdings);

      var retrieved = rtacHoldingRepository.findAll();
      assertEquals(2, retrieved.size());
      assertIterableEquals(holdings.stream().map(RtacHoldingEntity::getId).toList(),
        retrieved.stream().map(RtacHoldingEntity::getId).toList());
      assertEquals("test", retrieved.get(0).getRtacHolding().getBarcode());
      assertEquals("test", retrieved.get(1).getRtacHolding().getBarcode());
    });
  }

  @Test
  void bulkUpdateLocationData_updatesEmbeddedLocation() throws SQLException, JsonProcessingException {
    withinTenant(TestConstant.TEST_TENANT, () -> {
      var loc1 = getRtacHoldingLocation(LOCATION_ID_1, "OLD_CODE", "Old Name");
      var loc2 = getRtacHoldingLocation(LOCATION_ID_2, "OTHER_CODE", "Other Name");
      var rtacHolding1 = getRtacHolding(ITEM_ID_1, INSTANCE_ID);
      var rtacHolding2 = getRtacHolding(ITEM_ID_2, INSTANCE_ID);
      rtacHolding1.setLocation(loc1);
      rtacHolding2.setLocation(loc2);
      var holdings = List.of(
        new RtacHoldingEntity(RtacHoldingId.from(rtacHolding1), false, rtacHolding1, Instant.now()),
        new RtacHoldingEntity(RtacHoldingId.from(rtacHolding2), false, rtacHolding2, Instant.now())
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
    });
  }

  @Test
  void bulkUpdateLibraryData_updatesEmbeddedLibrary() throws SQLException, JsonProcessingException {
    withinTenant(TestConstant.TEST_TENANT, () -> {
      var lib1 = getRtacHoldingLibrary(LIBRARY_ID_1, "OLD_CODE", "Old Name");
      var lib2 = getRtacHoldingLibrary(LIBRARY_ID_2, "OTHER_CODE", "Other Name");
      var rtacHolding1 = getRtacHolding(ITEM_ID_1, INSTANCE_ID);
      var rtacHolding2 = getRtacHolding(ITEM_ID_2, INSTANCE_ID);
      rtacHolding1.setLibrary(lib1);
      rtacHolding2.setLibrary(lib2);
      var holdings = List.of(
        new RtacHoldingEntity(RtacHoldingId.from(rtacHolding1), false, rtacHolding1, Instant.now()),
        new RtacHoldingEntity(RtacHoldingId.from(rtacHolding2), false, rtacHolding2, Instant.now())
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
    });
  }

  @Test
  void bulkMarkHoldingsAsSharedByInstanceId_marksMatchingInstanceShared() throws SQLException {
    withinTenant(TestConstant.TEST_TENANT, () -> {
      var instanceIdToMark = UUID.randomUUID();

      var holding1 = getRtacHolding(ITEM_ID_1, instanceIdToMark.toString());
      var holding2 = getRtacHolding(ITEM_ID_2, instanceIdToMark.toString());

      var e1 = new RtacHoldingEntity(RtacHoldingId.from(holding1), false, holding1, Instant.now());
      var e2 = new RtacHoldingEntity(RtacHoldingId.from(holding2), false, holding2, Instant.now());

      rtacHoldingRepository.saveAll(List.of(e1, e2));

      rtacHoldingBulkRepository.bulkMarkHoldingsAsSharedByInstanceId(instanceIdToMark);

      var retrieved = rtacHoldingRepository.findAll();

      var updatedForInstance = retrieved.stream()
        .filter(h -> instanceIdToMark.equals(h.getId().getInstanceId()))
        .toList();
      assertEquals(2, updatedForInstance.size());
      assertTrue(updatedForInstance.stream().allMatch(RtacHoldingEntity::isShared));
    });
  }

  @Test
  void bulkUpdateMaterialTypeData_updatesEmbeddedMaterialType() throws SQLException {
    withinTenant(TestConstant.TEST_TENANT, () -> {
      var rtacHolding1 = getRtacHolding(ITEM_ID_1, INSTANCE_ID);
      var rtacHolding2 = getRtacHolding(ITEM_ID_2, INSTANCE_ID);
      rtacHolding1.setMaterialType(getRtacHoldingMaterialType(MATERIAL_TYPE_ID_1, "Old Name"));
      rtacHolding2.setMaterialType(getRtacHoldingMaterialType(MATERIAL_TYPE_ID_2, "Other Name"));
      var holdings = List.of(
        new RtacHoldingEntity(RtacHoldingId.from(rtacHolding1), false, rtacHolding1, Instant.now()),
        new RtacHoldingEntity(RtacHoldingId.from(rtacHolding2), false, rtacHolding2, Instant.now())
      );
      rtacHoldingRepository.saveAll(holdings);

      rtacHoldingBulkRepository.bulkUpdateMaterialTypeData(new MaterialType().id(MATERIAL_TYPE_ID_1).name("Updated Name"));

      var retrieved = rtacHoldingRepository.findAll();
      assertEquals(2, retrieved.size());
      var updated = findByMaterialTypeId(retrieved, MATERIAL_TYPE_ID_1);
      assertEquals("Updated Name", updated.getRtacHolding().getMaterialType().getName());
      var unchanged = findByMaterialTypeId(retrieved, MATERIAL_TYPE_ID_2);
      assertEquals("Other Name", unchanged.getRtacHolding().getMaterialType().getName());
    });
  }

  @Test
  void bulkUpdateLoanTypeData_updatesTemporaryAndPermanentLoanTypeNames() throws SQLException {
    withinTenant(TestConstant.TEST_TENANT, () -> {
      var rtacHolding1 = getRtacHolding(ITEM_ID_1, INSTANCE_ID);
      var rtacHolding2 = getRtacHolding(ITEM_ID_2, INSTANCE_ID);
      rtacHolding1.setTemporaryLoanType("Old Loan Type");
      rtacHolding1.setPermanentLoanType("Old Loan Type");
      rtacHolding2.setTemporaryLoanType("Other Loan Type");
      rtacHolding2.setPermanentLoanType("Other Loan Type");
      var holdings = List.of(
        new RtacHoldingEntity(RtacHoldingId.from(rtacHolding1), false, rtacHolding1, Instant.now()),
        new RtacHoldingEntity(RtacHoldingId.from(rtacHolding2), false, rtacHolding2, Instant.now())
      );
      rtacHoldingRepository.saveAll(holdings);

      var oldLoanType = new LoanType().name("Old Loan Type");
      var newLoanType = new LoanType().name("New Loan Type");
      rtacHoldingBulkRepository.bulkUpdateLoanTypeData(oldLoanType, newLoanType);

      var retrieved = rtacHoldingRepository.findAll();
      assertEquals(2, retrieved.size());
      var updated = findByRtacHoldingId(retrieved, ITEM_ID_1);
      assertEquals("New Loan Type", updated.getRtacHolding().getTemporaryLoanType());
      assertEquals("New Loan Type", updated.getRtacHolding().getPermanentLoanType());
      var unchanged = findByRtacHoldingId(retrieved, ITEM_ID_2);
      assertEquals("Other Loan Type", unchanged.getRtacHolding().getTemporaryLoanType());
      assertEquals("Other Loan Type", unchanged.getRtacHolding().getPermanentLoanType());
    });
  }

  private void withinTenant(String tenant, ThrowingRunnable action) {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext(tenant))) {
      action.run();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {

    void run() throws Exception;
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

  private RtacHoldingEntity findByMaterialTypeId(List<RtacHoldingEntity> retrieved, String materialTypeId) {
    return retrieved.stream()
      .filter(h -> h.getRtacHolding().getMaterialType().getId().equals(materialTypeId))
      .findFirst()
      .orElseThrow();
  }

  private RtacHoldingEntity findByRtacHoldingId(List<RtacHoldingEntity> retrieved, String rtacHoldingId) {
    return retrieved.stream()
      .filter(h -> h.getRtacHolding().getId().equals(rtacHoldingId))
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

  private RtacHoldingMaterialType getRtacHoldingMaterialType(String id, String name) {
    return new RtacHoldingMaterialType()
      .id(id)
      .name(name);
  }

//  @Test
//  void testBulkUpdateLocationData_performance() throws SQLException, JsonProcessingException {
//    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
//
//    // Prepare test data
//    String locationId = UUID.randomUUID().toString();
//    String locationCode = "TEST_CODE";
//    String locationName = "Test Location";
//
//    var location = new RtacHoldingLocation();
//    location.setId(locationId);
//    location.setCode(locationCode);
//    location.setName(locationName);
//
//    List<RtacHoldingEntity> holdings = IntStream.range(0, 10000000)
//      .parallel()
//      .mapToObj(i -> {
//        var rtacHolding = new RtacHolding();
//        rtacHolding.setId(UUID.randomUUID().toString());
//        rtacHolding.setType(TypeEnum.ITEM);
//        rtacHolding.setInstanceId(UUID.randomUUID().toString());
//        rtacHolding.setStatus("Available");
//        rtacHolding.setLocation(location);
//
//        return new RtacHoldingEntity(
//          RtacHoldingId.from(rtacHolding),
//          rtacHolding,
//          Instant.now()
//        );
//      })
//      .collect(Collectors.toList());
//
//    System.out.println("Holdings list prepared.");
//
//    // Insert test data
//    rtacHoldingBulkRepository.bulkUpsert(holdings);
//
//    System.out.println("Records created");
//
////    // Verify data was inserted
////    assertEquals(1, rtacHoldingRepository.count());
//
//    // Create updated location
//    var updatedLocation = new Location();
//    updatedLocation.setId(locationId);
//    updatedLocation.setCode("UPDATED_CODE");
//    updatedLocation.setName("Updated Location Name");
//
//    // Measure performance
//    long startTime = System.currentTimeMillis();
//    rtacHoldingBulkRepository.bulkUpdateLocationData(updatedLocation);
//    long endTime = System.currentTimeMillis();
//    long duration = endTime - startTime;
//
//    // Log performance metrics
//    System.out.println("Bulk update of records took: " + duration + " ms");
//  }
}
