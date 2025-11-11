package org.folio.rtaccache.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
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
    UUID targetInstanceId = UUID.randomUUID();
    rtacHoldingRepository.save(new RtacHoldingEntity(
      new RtacHoldingId(targetInstanceId, TypeEnum.HOLDING, UUID.randomUUID()),
      new RtacHolding(),
      Instant.now()));

    rtacHoldingRepository.save(new RtacHoldingEntity(
      new RtacHoldingId(targetInstanceId, TypeEnum.ITEM, UUID.randomUUID()),
      new RtacHolding(),
      Instant.now()));

    rtacHoldingRepository.save(new RtacHoldingEntity(
      new RtacHoldingId(targetInstanceId, TypeEnum.PIECE, UUID.randomUUID()),
      new RtacHolding(),
      Instant.now()));

    rtacHoldingRepository.save(new RtacHoldingEntity(
      new RtacHoldingId(UUID.randomUUID(), TypeEnum.PIECE, UUID.randomUUID()),
      new RtacHolding(),
      Instant.now()));

    Page<RtacHolding> page0 = rtacHoldingStorageService
      .getRtacHoldingsByInstanceId(targetInstanceId.toString(), OffsetRequest.of(0, 2));

    assertThat(page0.getContent()).hasSize(2);
    assertThat(page0.getTotalElements()).isEqualTo(3);
    assertThat(page0.getNumber()).isZero();
    assertThat(page0.getTotalPages()).isEqualTo(2);

    Page<RtacHolding> page1 = rtacHoldingStorageService
      .getRtacHoldingsByInstanceId(targetInstanceId.toString(), OffsetRequest.of(2, 2));

    assertThat(page1.getContent()).hasSize(1);
    assertThat(page1.getTotalElements()).isEqualTo(3);
    assertThat(page1.getNumber()).isEqualTo(1);
  }

  @Test
  void testGetRtacHoldingsSummaryForInstanceIds() {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    UUID instanceId1 = UUID.randomUUID();
    UUID instanceId2 = UUID.randomUUID();
    UUID instanceId3 = UUID.randomUUID();
    UUID instanceId4 = UUID.randomUUID();

    // Common location data
    var library1Id = UUID.randomUUID().toString();
    var location1Id = UUID.randomUUID().toString();

    var library2Id = UUID.randomUUID().toString();
    var location2Id = UUID.randomUUID().toString();

    // Instance 1: Multiple statuses in one location
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId1, TypeEnum.HOLDING, "Available", library1Id, location1Id, null));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId1, TypeEnum.ITEM, "Available", library1Id, location1Id, null));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId1, TypeEnum.PIECE, "Unavailable", library1Id, location1Id, null));

    // Instance 2: Multiple locations, different statuses
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId2, TypeEnum.HOLDING, "Available", library1Id, location1Id, null));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId2, TypeEnum.ITEM, "Unavailable", library2Id, location2Id, null));

    // Instance 3: No holdings

    // Instance 4: One holding with volume
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId4, TypeEnum.HOLDING, "Available", library1Id, location1Id, "v.1"));

    List<UUID> targetInstanceIds = List.of(instanceId1, instanceId2, instanceId3, instanceId4);

    RtacHoldingsBatch rtacHoldingsBatch = rtacHoldingStorageService.getRtacHoldingsSummaryForInstanceIds(targetInstanceIds);

    assertThat(rtacHoldingsBatch.getHoldings()).hasSize(3);
    assertThat(rtacHoldingsBatch.getErrors()).hasSize(1);

    // Assertions for Instance 1
    RtacHoldingsSummary summary1 = rtacHoldingsBatch.getHoldings().stream()
      .filter(s -> s.getInstanceId().equals(instanceId1.toString()))
      .findFirst().orElseThrow();
    assertRtacHoldingsSummary(summary1, instanceId1, false, List.of(
      new ExpectedLocationStatus(library1Id, location1Id, "Available", 2),
      new ExpectedLocationStatus(library1Id, location1Id, "Unavailable", 1)
    ));

    // Assertions for Instance 2
    RtacHoldingsSummary summary2 = rtacHoldingsBatch.getHoldings().stream()
      .filter(s -> s.getInstanceId().equals(instanceId2.toString()))
      .findFirst().orElseThrow();
    assertRtacHoldingsSummary(summary2, instanceId2, false, List.of(
      new ExpectedLocationStatus(library1Id, location1Id, "Available", 1),
      new ExpectedLocationStatus(library2Id, location2Id, "Unavailable", 1)
    ));

    // Assertions for Instance 4
    RtacHoldingsSummary summary4 = rtacHoldingsBatch.getHoldings().stream()
      .filter(s -> s.getInstanceId().equals(instanceId4.toString()))
      .findFirst().orElseThrow();
    assertRtacHoldingsSummary(summary4, instanceId4, true, List.of(
      new ExpectedLocationStatus(library1Id, location1Id, "Available", 1)
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
}
