package org.folio.rtaccache.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.domain.dto.RtacHoldingLibrary;
import org.folio.rtaccache.domain.dto.RtacHoldingLocation;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.data.OffsetRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class RtacHoldingStorageServiceSearchTest extends BaseIntegrationTest {

  @Autowired
  private RtacHoldingStorageService rtacHoldingStorageService;
  @Autowired
  private RtacHoldingRepository rtacHoldingRepository;
  @MockitoSpyBean
  private FolioExecutionContext folioExecutionContext;

  @AfterEach
  void tearDown() {
    rtacHoldingRepository.deleteAll();
  }

  private RtacHoldingEntity createRtacHoldingEntity(UUID instanceId, String volume, String callNumber, String locationName, String libraryName, String status) {
    final var rtacHolding = new RtacHolding()
      .instanceId(instanceId.toString())
      .status(status)
      .volume(volume)
      .callNumber(callNumber)
      .location(new RtacHoldingLocation().name(locationName))
      .library(new RtacHoldingLibrary().name(libraryName));
    return new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.ITEM, UUID.randomUUID()), rtacHolding, Instant.now());
  }

  @Test
  void testSearchRtacHoldings() {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    var instanceId1 = UUID.randomUUID();
    var instanceId2 = UUID.randomUUID();

    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId1,"vol1", "call1", "loc1", "lib1", "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId1,"vol2", "call2", "loc2", "lib2", "Checked out"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId2,"vol3", "call3", "loc3", "lib3", "Available"));

    Page<RtacHolding> page = rtacHoldingStorageService.searchRtacHoldings(instanceId1, "vol1 call1", null, OffsetRequest.of(0, 10));
    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent().getFirst().getVolume()).isEqualTo("vol1");
    assertThat(page.getContent().getFirst().getInstanceId()).isEqualTo(instanceId1.toString());

    page = rtacHoldingStorageService.searchRtacHoldings(instanceId1, "call2 loc2", null, OffsetRequest.of(0, 10));
    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent().getFirst().getCallNumber()).isEqualTo("call2");
    assertThat(Objects.requireNonNull(page.getContent().getFirst().getLocation()).getName()).isEqualTo("loc2");

    page = rtacHoldingStorageService.searchRtacHoldings(instanceId1,"loc2 call2", null, OffsetRequest.of(0, 10)); // Out of order
    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent().getFirst().getCallNumber()).isEqualTo("call2");
    assertThat(Objects.requireNonNull(page.getContent().getFirst().getLocation()).getName()).isEqualTo("loc2");

    page = rtacHoldingStorageService.searchRtacHoldings(instanceId2,"loc3 lib3", null, OffsetRequest.of(0, 10));
    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(Objects.requireNonNull(page.getContent().getFirst().getLocation()).getName()).isEqualTo("loc3");
    assertThat(Objects.requireNonNull(page.getContent().getFirst().getLibrary()).getName()).isEqualTo("lib3");
    assertThat(page.getContent().getFirst().getInstanceId()).isEqualTo(instanceId2.toString());

    page = rtacHoldingStorageService.searchRtacHoldings(instanceId1,"lib1 vol1", null, OffsetRequest.of(0, 10));
    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(Objects.requireNonNull(page.getContent().getFirst().getLibrary()).getName()).isEqualTo("lib1");
    assertThat(page.getContent().getFirst().getCallNumber()).isEqualTo("call1");

    page = rtacHoldingStorageService.searchRtacHoldings(instanceId1, "vol", null, OffsetRequest.of(0, 10));
    assertThat(page.getTotalElements()).isEqualTo(2);

    page = rtacHoldingStorageService.searchRtacHoldings(instanceId1, "vol", true, OffsetRequest.of(0, 10));
    assertThat(page.getTotalElements()).isEqualTo(1);

    page = rtacHoldingStorageService.searchRtacHoldings(instanceId1,"vol1 call2", null, OffsetRequest.of(0, 10));
    assertThat(page.getTotalElements()).isEqualTo(0);
  }

  @Test
  void testSearchRtacHoldingsWithPaging() {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    var instanceId = UUID.randomUUID();

    // Create 5 holdings that match the search query "loc1"
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, "vol1", "call1", "loc1", "lib1", "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, "vol2", "call2", "loc1", "lib1", "Checked out"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, "vol3", "call3", "loc1", "lib1", "On order"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, "vol4", "call4", "loc1", "lib1", "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, "vol5", "call5", "loc1", "lib1", "In process"));

    // Create 2 holdings that do not match the search query
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, "vol6", "call6", "loc2", "lib2", "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, "vol7", "call7", "loc2", "lib2", "Checked out"));

    // Search for holdings with "loc1" and test paging
    String query = "loc1";

    // Test fetching the first page (3 out of 5 results)
    Page<RtacHolding> page0 = rtacHoldingStorageService
      .searchRtacHoldings(instanceId, query, null, OffsetRequest.of(0, 3));

    assertThat(page0.getContent()).hasSize(3);
    assertThat(page0.getTotalElements()).isEqualTo(5); // Verifies the query found 5 total records
    assertThat(page0.getTotalPages()).isEqualTo(2);
    assertThat(page0.getNumber()).isZero();

    // Test fetching the second page (the remaining 2 out of 5 results)
    Page<RtacHolding> page1 = rtacHoldingStorageService
      .searchRtacHoldings(instanceId, query, null, OffsetRequest.of(3, 3));

    assertThat(page1.getContent()).hasSize(2);
    assertThat(page1.getTotalElements()).isEqualTo(5);
    assertThat(page1.getNumber()).isEqualTo(1);

    // Test fetching with a query that returns no results
    Page<RtacHolding> emptyPage = rtacHoldingStorageService
      .searchRtacHoldings(instanceId, "nonexistent", null, OffsetRequest.of(0, 10));

    assertThat(emptyPage.getTotalElements()).isZero();
    assertThat(emptyPage.getContent()).isEmpty();
  }
}
