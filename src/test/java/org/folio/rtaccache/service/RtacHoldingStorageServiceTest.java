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
    rtacHoldingRepository.save(new RtacHoldingEntity(
      new RtacHoldingId(instanceId1, TypeEnum.HOLDING, UUID.randomUUID()),
      new RtacHolding()
        .status("Available")
        .location(new RtacHoldingLocation().id(location1Id))
        .library(new RtacHoldingLibrary().id(library1Id)),
      Instant.now()));
    rtacHoldingRepository.save(new RtacHoldingEntity(
      new RtacHoldingId(instanceId1, TypeEnum.ITEM, UUID.randomUUID()),
      new RtacHolding()
        .status("Available")
        .location(new RtacHoldingLocation().id(location1Id))
        .library(new RtacHoldingLibrary().id(library1Id)),
      Instant.now()));
    rtacHoldingRepository.save(new RtacHoldingEntity(
      new RtacHoldingId(instanceId1, TypeEnum.PIECE, UUID.randomUUID()),
      new RtacHolding()
        .status("Unavailable")
        .location(new RtacHoldingLocation().id(location1Id))
        .library(new RtacHoldingLibrary().id(library1Id)),
      Instant.now()));

    // Instance 2: Multiple locations, different statuses
    rtacHoldingRepository.save(new RtacHoldingEntity(
      new RtacHoldingId(instanceId2, TypeEnum.HOLDING, UUID.randomUUID()),
      new RtacHolding()
        .status("Available")
        .location(new RtacHoldingLocation().id(location1Id))
        .library(new RtacHoldingLibrary().id(library1Id)),
      Instant.now()));
    rtacHoldingRepository.save(new RtacHoldingEntity(
      new RtacHoldingId(instanceId2, TypeEnum.ITEM, UUID.randomUUID()),
      new RtacHolding()
        .status("Unavailable")
        .location(new RtacHoldingLocation().id(location2Id))
        .library(new RtacHoldingLibrary().id(library2Id)),
      Instant.now()));

    // Instance 3: No holdings

    // Instance 4: One holding with volume
    rtacHoldingRepository.save(new RtacHoldingEntity(
      new RtacHoldingId(instanceId4, TypeEnum.HOLDING, UUID.randomUUID()),
      new RtacHolding()
        .status("Available")
        .volume("v.1")
        .location(new RtacHoldingLocation().id(location1Id))
        .library(new RtacHoldingLibrary().id(library1Id)),
      Instant.now()));

    List<UUID> targetInstanceIds = List.of(instanceId1, instanceId2, instanceId3, instanceId4);

    RtacHoldingsBatch rtacHoldingsBatch = rtacHoldingStorageService.getRtacHoldingsSummaryForInstanceIds(targetInstanceIds);

    assertThat(rtacHoldingsBatch.getHoldings()).hasSize(3);
    assertThat(rtacHoldingsBatch.getErrors()).hasSize(1);

    // Assertions for Instance 1
    RtacHoldingsSummary summary1 = rtacHoldingsBatch.getHoldings().stream()
      .filter(s -> s.getInstanceId().equals(instanceId1.toString()))
      .findFirst().orElseThrow();
    assertThat(summary1.getHasVolumes()).isFalse();
    assertThat(summary1.getLocationStatus()).hasSize(2); // Two distinct status/location combinations

    var inst1Loc1Avail = summary1.getLocationStatus().stream()
      .filter(la -> la.getLibraryId().equals(library1Id) && la.getLocationId().equals(location1Id) && la.getStatus().equals("Available"))
      .findFirst().orElseThrow();
    assertThat(inst1Loc1Avail.getStatusCount()).isEqualTo(2);

    var inst1Loc1Unavail = summary1.getLocationStatus().stream()
      .filter(la -> la.getLibraryId().equals(library1Id) && la.getLocationId().equals(location1Id) && la.getStatus().equals("Unavailable"))
      .findFirst().orElseThrow();
    assertThat(inst1Loc1Unavail.getStatusCount()).isEqualTo(1);

    // Assertions for Instance 2
    RtacHoldingsSummary summary2 = rtacHoldingsBatch.getHoldings().stream()
      .filter(s -> s.getInstanceId().equals(instanceId2.toString()))
      .findFirst().orElseThrow();
    assertThat(summary2.getHasVolumes()).isFalse();
    assertThat(summary2.getLocationStatus()).hasSize(2); // Two distinct status/location combinations

    var inst2Loc1Avail = summary2.getLocationStatus().stream()
      .filter(la -> la.getLibraryId().equals(library1Id) && la.getLocationId().equals(location1Id) && la.getStatus().equals("Available"))
      .findFirst().orElseThrow();
    assertThat(inst2Loc1Avail.getStatusCount()).isEqualTo(1);

    var inst2Loc2Unavail = summary2.getLocationStatus().stream()
      .filter(la -> la.getLibraryId().equals(library2Id) && la.getLocationId().equals(location2Id) && la.getStatus().equals("Unavailable"))
      .findFirst().orElseThrow();
    assertThat(inst2Loc2Unavail.getStatusCount()).isEqualTo(1);

    // Assertions for Instance 4
    RtacHoldingsSummary summary4 = rtacHoldingsBatch.getHoldings().stream()
      .filter(s -> s.getInstanceId().equals(instanceId4.toString()))
      .findFirst().orElseThrow();
    assertThat(summary4.getHasVolumes()).isTrue();
    assertThat(summary4.getLocationStatus()).hasSize(1); // One distinct status/location combination

    var inst4Loc1Avail = summary4.getLocationStatus().stream()
      .filter(la -> la.getLibraryId().equals(library1Id) && la.getLocationId().equals(location1Id) && la.getStatus().equals("Available"))
      .findFirst().orElseThrow();
    assertThat(inst4Loc1Avail.getStatusCount()).isEqualTo(1);

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
    rtacHoldingRepository.save(new RtacHoldingEntity(
      new RtacHoldingId(instanceIdNoLocation, TypeEnum.HOLDING, UUID.randomUUID()),
      new RtacHolding()
        .status("Available"), // Only set status, no location or library
      Instant.now()));

    List<UUID> targetInstanceIds = List.of(instanceIdNoLocation);

    RtacHoldingsBatch rtacHoldingsBatch = rtacHoldingStorageService.getRtacHoldingsSummaryForInstanceIds(targetInstanceIds);

    assertThat(rtacHoldingsBatch.getHoldings()).hasSize(1);
    assertThat(rtacHoldingsBatch.getErrors()).isEmpty();

    RtacHoldingsSummary summaryNoLocation = rtacHoldingsBatch.getHoldings().stream()
      .filter(s -> s.getInstanceId().equals(instanceIdNoLocation.toString()))
      .findFirst().orElseThrow();

    assertThat(summaryNoLocation.getHasVolumes()).isFalse(); // Assuming no volume info either
    assertThat(summaryNoLocation.getLocationStatus()).hasSize(1);

    var locationStatus = summaryNoLocation.getLocationStatus().get(0);
    assertThat(locationStatus.getLibraryId()).isNull();
    assertThat(locationStatus.getLocationId()).isNull();
    assertThat(locationStatus.getStatus()).isEqualTo("Available");
    assertThat(locationStatus.getStatusCount()).isEqualTo(1);
  }
}
