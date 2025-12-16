package org.folio.rtaccache.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.folio.rtaccache.TestUtil.asString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.domain.dto.Error;
import org.folio.rtaccache.domain.dto.RtacHoldingsBatch;
import org.folio.rtaccache.domain.dto.RtacHoldingsSummary;
import org.folio.rtaccache.domain.dto.RtacHoldingLibrary;
import org.folio.rtaccache.domain.dto.RtacHoldingLocation;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.HttpStatus;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.data.OffsetRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

class RtacHoldingStorageServiceTest extends BaseIntegrationTest {

  @Autowired
  private RtacHoldingStorageService rtacHoldingStorageService;
  @Autowired
  private RtacHoldingRepository rtacHoldingRepository;
  @Autowired
  private FolioModuleMetadata folioModuleMetadata;
  @MockitoSpyBean
  private FolioExecutionContext folioExecutionContext;

  @AfterEach
  void tearDown() {
    rtacHoldingRepository.deleteAll();
  }

  private RtacHoldingEntity createRtacHoldingEntity(UUID instanceId,
                                                    RtacHolding.TypeEnum type,
                                                    String status,
                                                    String libraryId,
                                                    String locationId,
                                                    String volume) {
    RtacHolding rtacHolding = new RtacHolding().status(status);
    if (libraryId != null) {
      rtacHolding.library(new RtacHoldingLibrary().id(libraryId));
    }
    if (locationId != null) {
      rtacHolding.location(new RtacHoldingLocation().id(locationId));
    }
    if (volume != null) {
      rtacHolding.volume(volume);
    }
    return new RtacHoldingEntity(new RtacHoldingId(instanceId, type, UUID.randomUUID()), rtacHolding, Instant.now());
  }

  private RtacHoldingEntity createRtacHoldingEntity(UUID instanceId, RtacHolding.TypeEnum type, String status) {
    return createRtacHoldingEntity(instanceId, type, status, null, null, null);
  }

  private void assertRtacHoldingsSummary(RtacHoldingsSummary summary,
                                         UUID expectedInstanceId,
                                         boolean expectedHasVolumes,
                                         List<ExpectedLocationStatus> expectedLocationStatuses) {
    assertThat(summary.getInstanceId()).isEqualTo(expectedInstanceId.toString());
    assertThat(summary.getHasVolumes()).isEqualTo(expectedHasVolumes);
    assertThat(summary.getLocationStatus()).hasSize(expectedLocationStatuses.size());

    for (ExpectedLocationStatus expected : expectedLocationStatuses) {
      var actual = summary.getLocationStatus().stream()
        .filter(la -> java.util.Objects.equals(expected.libraryId, la.getLibraryId()) &&
                       java.util.Objects.equals(expected.locationId, la.getLocationId()) &&
                       expected.status.equals(la.getStatus()))
        .findFirst().orElseThrow();
      assertThat(actual.getStatusCount()).isEqualTo(expected.statusCount);
    }
  }

  private record ExpectedLocationStatus(String libraryId, String locationId, String status, int statusCount) {}

  @Test
  void testGetRtacHoldingsByInstanceId() {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    var instanceId = UUID.randomUUID();

    // This instance has items, so the Holding record should be filtered out.
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, TypeEnum.ITEM, "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, TypeEnum.PIECE, "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, TypeEnum.HOLDING, "Available"));

    // This data for another instance should not be returned.
    rtacHoldingRepository.save(createRtacHoldingEntity(UUID.randomUUID(), TypeEnum.ITEM, "Available"));

    // Expect only the item and piece records to be returned (total of 2).
    Page<RtacHolding> page = rtacHoldingStorageService
      .getRtacHoldingsByInstanceId(instanceId.toString(), OffsetRequest.of(0, 5));
    assertThat(page.getContent()).hasSize(2);
    assertThat(page.getTotalElements()).isEqualTo(2);
  }

  @Test
  void testGetRtacHoldingsByInstanceIdWithPaging() {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    var instanceId = UUID.randomUUID();

    // Create 7 entities (2 items, 5 pieces) that should be returned, and 1 holding that should be filtered out.
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, TypeEnum.ITEM, "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, TypeEnum.ITEM, "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, TypeEnum.PIECE, "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, TypeEnum.PIECE, "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, TypeEnum.PIECE, "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, TypeEnum.PIECE, "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, TypeEnum.PIECE, "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, TypeEnum.HOLDING, "Available"));

    // Request first page with size 5
    Page<RtacHolding> firstPage = rtacHoldingStorageService
      .getRtacHoldingsByInstanceId(instanceId.toString(), OffsetRequest.of(0, 5));
    assertThat(firstPage.getContent()).hasSize(5);
    assertThat(firstPage.getTotalElements()).isEqualTo(7);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);

    // Request second page with size 5
    Page<RtacHolding> secondPage = rtacHoldingStorageService
      .getRtacHoldingsByInstanceId(instanceId.toString(), OffsetRequest.of(5, 5));
    assertThat(secondPage.getContent()).hasSize(2);
    assertThat(secondPage.getTotalElements()).isEqualTo(7);
    assertThat(secondPage.getTotalPages()).isEqualTo(2);

    // Request a page that is out of bounds
    Page<RtacHolding> emptyPage = rtacHoldingStorageService
      .getRtacHoldingsByInstanceId(instanceId.toString(), OffsetRequest.of(10, 5));
    assertThat(emptyPage.getContent()).isEmpty();
    assertThat(emptyPage.getTotalElements()).isEqualTo(7);
  }

  @Test
  void testCountByIdInstanceId_withCteFiltering() {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    // Scenario 1: Instance has items, so HOLDING records should be excluded from count.
    var instanceWithItems = UUID.randomUUID();
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceWithItems, TypeEnum.ITEM, "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceWithItems, TypeEnum.ITEM, "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceWithItems, TypeEnum.PIECE, "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceWithItems, TypeEnum.HOLDING, "Available")); // Should be filtered out
    // This data for another instance should not be returned
    rtacHoldingRepository.save(createRtacHoldingEntity(UUID.randomUUID(), TypeEnum.ITEM, "Available"));

    final var schema = String.format("%s_mod_rtac_cache", TestConstant.TEST_TENANT.toLowerCase());
    int count1 = rtacHoldingRepository.countByIdInstanceId(schema, instanceWithItems);
    assertThat(count1).isEqualTo(3); // 2 items + 1 piece

    // Scenario 2: Instance has NO items, so HOLDING records should be included in count.
    var instanceWithoutItems = UUID.randomUUID();
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceWithoutItems, TypeEnum.PIECE, "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceWithoutItems, TypeEnum.HOLDING, "Available")); // Should be included

    int count2 = rtacHoldingRepository.countByIdInstanceId(schema, instanceWithoutItems);
    assertThat(count2).isEqualTo(2); // 1 piece + 1 holding
  }

  @Test
  void testGetRtacHoldingsSummaryForInstanceIds() {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    UUID instanceId1 = UUID.randomUUID();
    UUID instanceId2 = UUID.randomUUID();
    UUID instanceId3 = UUID.randomUUID();
    UUID instanceId4 = UUID.randomUUID();
    UUID instanceId5 = UUID.randomUUID();

    // Common location data
    var library1Id = UUID.randomUUID().toString();
    var location1Id = UUID.randomUUID().toString();

    var library2Id = UUID.randomUUID().toString();
    var location2Id = UUID.randomUUID().toString();

    // Instance 1: Multiple statuses in one location, Holding should be excluded because instance has at least one item.
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId1, TypeEnum.HOLDING, "Available", library1Id, location1Id, null));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId1, TypeEnum.ITEM, "Available", library1Id, location1Id, null));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId1, TypeEnum.PIECE, "Unavailable", library1Id, location1Id, null));

    // Instance 2: Multiple locations, different statuses. Holding should be excluded because instance has at least one item.
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId2, TypeEnum.HOLDING, "Available", library1Id, location1Id, null));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId2, TypeEnum.ITEM, "Unavailable", library2Id, location2Id, null));

    // Instance 3: No holdings

    // Instance 4: One holding with volume, no items or pieces.
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId4, TypeEnum.HOLDING, "Available", library1Id, location1Id, "v.1"));

    // Instance 5: Statuses should be summed correctly across multiple holdings, items, pieces and locations. Holding should be excluded because instance has at least one item or piece.
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId5, TypeEnum.ITEM, "Available", library1Id, location1Id, null));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId5, TypeEnum.PIECE, "Available", library1Id, location1Id, null));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId5, TypeEnum.HOLDING, "Available", library1Id, location1Id, null));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId5, TypeEnum.ITEM, "Unavailable", library1Id, location1Id, null));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId5, TypeEnum.PIECE, "Unavailable", library1Id, location1Id, null));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId5, TypeEnum.PIECE, "Available", library1Id, location2Id, null));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId5, TypeEnum.PIECE, "Available", library1Id, location2Id, null));

    List<UUID> targetInstanceIds = List.of(instanceId1, instanceId2, instanceId3, instanceId4, instanceId5);

    RtacHoldingsBatch rtacHoldingsBatch = rtacHoldingStorageService.getRtacHoldingsSummaryForInstanceIds(targetInstanceIds);

    assertThat(rtacHoldingsBatch.getHoldings()).hasSize(4);
    assertThat(rtacHoldingsBatch.getErrors()).hasSize(1);

    // Assertions for Instance 1
    RtacHoldingsSummary summary1 = rtacHoldingsBatch.getHoldings().stream()
      .filter(s -> s.getInstanceId().equals(instanceId1.toString()))
      .findFirst().orElseThrow();
    assertRtacHoldingsSummary(summary1, instanceId1, false, List.of(
      new ExpectedLocationStatus(library1Id, location1Id, "Available", 1),
      new ExpectedLocationStatus(library1Id, location1Id, "Unavailable", 1)
    ));

    // Assertions for Instance 2
    RtacHoldingsSummary summary2 = rtacHoldingsBatch.getHoldings().stream()
      .filter(s -> s.getInstanceId().equals(instanceId2.toString()))
      .findFirst().orElseThrow();
    assertRtacHoldingsSummary(summary2, instanceId2, false, List.of(
      new ExpectedLocationStatus(library2Id, location2Id, "Unavailable", 1)
    ));

    // Assertions for Instance 4
    RtacHoldingsSummary summary4 = rtacHoldingsBatch.getHoldings().stream()
      .filter(s -> s.getInstanceId().equals(instanceId4.toString()))
      .findFirst().orElseThrow();
    assertRtacHoldingsSummary(summary4, instanceId4, true, List.of(
      new ExpectedLocationStatus(library1Id, location1Id, "Available", 1)
    ));

    // Assertions for Instance 4
    RtacHoldingsSummary summary5 = rtacHoldingsBatch.getHoldings().stream()
      .filter(s -> s.getInstanceId().equals(instanceId5.toString()))
      .findFirst().orElseThrow();
    assertRtacHoldingsSummary(summary5, instanceId5, false, List.of(
      new ExpectedLocationStatus(library1Id, location1Id, "Available", 2),
      new ExpectedLocationStatus(library1Id, location1Id, "Unavailable", 2),
      new ExpectedLocationStatus(library1Id, location2Id, "Available", 2)
    ));

    // Assertions for Instance 3 (error)
    Error error3 = rtacHoldingsBatch.getErrors().stream()
      .filter(e -> e.getMessage().contains(instanceId3.toString()))
      .findFirst().orElseThrow();
    assertThat(error3.getCode()).isEqualTo(String.valueOf(HttpStatus.NOT_FOUND.value()));
    assertThat(error3.getMessage()).contains(instanceId3.toString());
  }

  @Test
  void testGetRtacHoldingsSummaryForInstanceIds_noLocationInfo() {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    UUID instanceIdNoLocation = UUID.randomUUID();

    // Create a holding with no location or library information
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceIdNoLocation, TypeEnum.HOLDING, "Available"));

    List<UUID> targetInstanceIds = List.of(instanceIdNoLocation);

    RtacHoldingsBatch rtacHoldingsBatch = rtacHoldingStorageService.getRtacHoldingsSummaryForInstanceIds(targetInstanceIds);

    assertThat(rtacHoldingsBatch.getHoldings()).hasSize(1);
    assertThat(rtacHoldingsBatch.getErrors()).isEmpty();

    RtacHoldingsSummary summaryNoLocation = rtacHoldingsBatch.getHoldings().stream()
      .filter(s -> s.getInstanceId().equals(instanceIdNoLocation.toString()))
      .findFirst().orElseThrow();

    assertRtacHoldingsSummary(summaryNoLocation, instanceIdNoLocation, false, List.of(
      new ExpectedLocationStatus(null, null, "Available", 1)
    ));
  }

  @Test
  void testCountAcrossTenants() throws Exception {
    final var tenant1 = TestConstant.TEST_TENANT;
    final var tenant2 = "tenant2";
    final var instanceId = UUID.randomUUID();

    // Tenant1 is already set up by BaseIntegrationTest. Set up tenant2.
    setUpTenant(tenant2);

    // --- Insert data for tenant 1 ---
    when(folioExecutionContext.getTenantId()).thenReturn(tenant1);
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, TypeEnum.ITEM, "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, TypeEnum.ITEM, "Available"));

    // --- Insert data for tenant 2 ---
    var tenant2Headers = defaultHeaders(tenant2, org.springframework.http.MediaType.APPLICATION_JSON);
    var tenant2Context = new org.folio.spring.DefaultFolioExecutionContext(folioModuleMetadata, (Map) tenant2Headers);
    try (var ignored = new org.folio.spring.scope.FolioExecutionContextSetter(tenant2Context)) {
      rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, TypeEnum.ITEM, "Available"));
      rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, TypeEnum.ITEM, "Available"));
      rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, TypeEnum.ITEM, "Available"));
    }

    // --- Query across both tenants ---
    final var schemas = String.join(",",
      String.format("%s_mod_rtac_cache", tenant1.toLowerCase()),
      String.format("%s_mod_rtac_cache", tenant2.toLowerCase())
    );

    int count = rtacHoldingRepository.countByIdInstanceId(schemas, instanceId);

    assertThat(count).isEqualTo(5);
  }

  private void setUpTenant(String tenantId) throws Exception {
    var tenantAttributes = new org.folio.tenant.domain.dto.TenantAttributes()
      .moduleTo("mod-rtac-cache-1.0.0")
      .parameters(List.of(new org.folio.tenant.domain.dto.Parameter().key("loadReference").value("true")));

    mockMvc.perform(post("/_/tenant")
        .content(asString(tenantAttributes))
        .headers(defaultHeaders(tenantId, org.springframework.http.MediaType.APPLICATION_JSON))
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }
}
