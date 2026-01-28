package org.folio.rtaccache.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class RtacCacheGenerationServiceIT extends BaseIntegrationTest {

  private static final String INSTANCE_ID_1 = "4de861ab-af9e-4247-bc16-c547d982eb5d";
  private static final String INSTANCE_ID_2 = "5dbab2d4-42f6-47e0-b0c6-023040bd19ff";
  private static final String ITEM_WITH_LOANS_AND_REQUESTS_ID = "9a772288-ead3-4033-b07b-87eff643710f";
  private static final String BOUND_WITH_ITEM_ID = "02b9b326-903b-41d7-b947-fc809e9d38c1";
  private static final String PIECE_ID = "aaf7be29-a8cc-4b0a-9975-bf39e9a71696";
  private static final String INSTANCE_FORMAT_ID = "6314d6f5-8f1a-4f2b-9c3d-2e5f6a7b8c9d";

  @Autowired
  private RtacCacheGenerationService rtacCacheGenerationService;
  @Autowired
  private RtacHoldingRepository rtacHoldingRepository;
  @MockitoSpyBean
  private FolioExecutionContext folioExecutionContext;

  @AfterEach
  void tearDown() {
    rtacHoldingRepository.deleteAll();
  }

  @Test
  void generateRtacCache_shouldFetchAndProcessRtacData() {
    //given
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    when(folioExecutionContext.getOkapiUrl()).thenReturn(WIRE_MOCK.baseUrl());
    //when
    var future = rtacCacheGenerationService.generateRtacCache(INSTANCE_ID_1);
    future.join();
    //then
    var rtacHoldings = rtacHoldingRepository.findAllByIdInstanceId(UUID.fromString(INSTANCE_ID_1), PageRequest.of(0, 50));
    var itemWithLoans = rtacHoldings.get()
      .filter(entity -> entity.getRtacHolding().getDueDate() != null).findFirst();
    var piece = rtacHoldings.get()
      .filter(entity -> TypeEnum.PIECE.equals(entity.getRtacHolding().getType())).findFirst();
    var itemWithHoldCount = rtacHoldings.get()
      .filter(entity -> entity.getRtacHolding().getTotalHoldRequests() != null &&
        entity.getRtacHolding().getTotalHoldRequests() > 0).findFirst();
    var holding = rtacHoldings.get()
      .filter(entity -> TypeEnum.HOLDING.equals(entity.getRtacHolding().getType())).findFirst();

    assertEquals(7, rtacHoldings.getTotalElements());
    assertTrue(itemWithLoans.isPresent());
    assertEquals(ITEM_WITH_LOANS_AND_REQUESTS_ID, itemWithLoans.get().getRtacHolding().getId());
    assertEquals(INSTANCE_FORMAT_ID, itemWithLoans.get().getRtacHolding().getInstanceFormatIds().getFirst());
    assertTrue(piece.isPresent());
    assertEquals(PIECE_ID, piece.get().getRtacHolding().getId());
    assertEquals(INSTANCE_FORMAT_ID, piece.get().getRtacHolding().getInstanceFormatIds().getFirst());
    assertTrue(itemWithHoldCount.isPresent());
    assertEquals(ITEM_WITH_LOANS_AND_REQUESTS_ID, itemWithHoldCount.get().getRtacHolding().getId());
    assertEquals(INSTANCE_FORMAT_ID, itemWithHoldCount.get().getRtacHolding().getInstanceFormatIds().getFirst());
    assertTrue(holding.isPresent());
    assertEquals(INSTANCE_FORMAT_ID, holding.get().getRtacHolding().getInstanceFormatIds().getFirst());
    assertTrue(rtacHoldings.get().allMatch(RtacHoldingEntity::isShared));
  }

  @Test
  void generateRtacCache_shouldProcessBoundWithItem() {
    //given
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    when(folioExecutionContext.getOkapiUrl()).thenReturn(WIRE_MOCK.baseUrl());
    //when
    var future = rtacCacheGenerationService.generateRtacCache(INSTANCE_ID_2);
    future.join();
    //then
    var holdings = rtacHoldingRepository.findAllByIdInstanceId(UUID.fromString(INSTANCE_ID_2), PageRequest.of(0, 50));
    assertEquals(2, holdings.getTotalElements());
    var rtacHoldingEntity = holdings.get()
      .filter(entity -> entity.getRtacHolding().getType() == TypeEnum.ITEM)
      .findFirst()
      .get();
    assertEquals(BOUND_WITH_ITEM_ID, rtacHoldingEntity.getRtacHolding().getId());
    assertEquals(INSTANCE_FORMAT_ID, rtacHoldingEntity.getRtacHolding().getInstanceFormatIds().getFirst());
    assertTrue(rtacHoldingEntity.getRtacHolding().getIsBoundWith());
    assertFalse(rtacHoldingEntity.isShared());
  }

}
