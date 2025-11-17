package org.folio.rtaccache.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
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

  private RtacHoldingEntity createRtacHoldingEntity(String volume, String callNumber, String locationName, String libraryName, String status) {
    final var rtacHolding = new RtacHolding().status(status)
      .volume(volume)
      .callNumber(callNumber)
      .location(new RtacHoldingLocation().name(locationName))
      .library(new RtacHoldingLibrary().name(libraryName));
    return new RtacHoldingEntity(new RtacHoldingId(UUID.randomUUID(), TypeEnum.ITEM, UUID.randomUUID()), rtacHolding, Instant.now());
  }

  @Test
  void testSearchRtacHoldings() {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    rtacHoldingRepository.save(createRtacHoldingEntity("vol1", "call1", "loc1", "lib1", "Available"));
    rtacHoldingRepository.save(createRtacHoldingEntity("vol2", "call2", "loc2", "lib2", "Checked out"));
    rtacHoldingRepository.save(createRtacHoldingEntity("vol3", "call3", "loc3", "lib3", "Available"));

    Page<RtacHolding> page = rtacHoldingStorageService.searchRtacHoldings("vol1 call1", null, OffsetRequest.of(0, 10));
    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent().getFirst().getVolume()).isEqualTo("vol1");

    page = rtacHoldingStorageService.searchRtacHoldings("call2 loc2", null, OffsetRequest.of(0, 10));
    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent().getFirst().getCallNumber()).isEqualTo("call2");
    assertThat(page.getContent().getFirst().getLocation().getName()).isEqualTo("loc2");

    page = rtacHoldingStorageService.searchRtacHoldings("loc2 call2", null, OffsetRequest.of(0, 10)); // Out of order
    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent().getFirst().getCallNumber()).isEqualTo("call2");
    assertThat(page.getContent().getFirst().getLocation().getName()).isEqualTo("loc2");

    page = rtacHoldingStorageService.searchRtacHoldings("loc3 lib3", null, OffsetRequest.of(0, 10));
    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent().getFirst().getLocation().getName()).isEqualTo("loc3");
    assertThat(page.getContent().getFirst().getLibrary().getName()).isEqualTo("lib3");

    page = rtacHoldingStorageService.searchRtacHoldings("lib1 vol1", null, OffsetRequest.of(0, 10));
    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent().getFirst().getLibrary().getName()).isEqualTo("lib1");
    assertThat(page.getContent().getFirst().getCallNumber()).isEqualTo("call1");

    page = rtacHoldingStorageService.searchRtacHoldings("vol", null, OffsetRequest.of(0, 10));
    assertThat(page.getTotalElements()).isEqualTo(3);

    page = rtacHoldingStorageService.searchRtacHoldings("vol", true, OffsetRequest.of(0, 10));
    assertThat(page.getTotalElements()).isEqualTo(2);

    page = rtacHoldingStorageService.searchRtacHoldings("vol1 call2", null, OffsetRequest.of(0, 10));
    assertThat(page.getTotalElements()).isEqualTo(0);
  }
}
