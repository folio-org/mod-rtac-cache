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
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
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
      .getRtacHoldingsByInstanceId(targetInstanceId.toString(), 0, 2);

    assertThat(page0.getContent()).hasSize(2);
    assertThat(page0.getTotalElements()).isEqualTo(3);
    assertThat(page0.getNumber()).isZero();
    assertThat(page0.getTotalPages()).isEqualTo(2);

    Page<RtacHolding> page1 = rtacHoldingStorageService
      .getRtacHoldingsByInstanceId(targetInstanceId.toString(), 1, 2);

    assertThat(page1.getContent()).hasSize(1);
    assertThat(page1.getTotalElements()).isEqualTo(3);
    assertThat(page1.getNumber()).isEqualTo(1);
  }
}
