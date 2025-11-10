package org.folio.rtaccache.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class RtacCacheGenerationServiceIT extends BaseIntegrationTest {

  @Autowired
  private RtacCacheGenerationService rtacCacheGenerationService;
  @Autowired
  private RtacHoldingRepository rtacHoldingRepository;
  @MockitoSpyBean
  private FolioExecutionContext folioExecutionContext;
  private static final String INSTANCE_ID = "4de861ab-af9e-4247-bc16-c547d982eb5d";
  private static final String ITEM_WITH_LOANS_AND_REQUESTS_ID = "9a772288-ead3-4033-b07b-87eff643710f";
  private static final String PIECE_ID = "aaf7be29-a8cc-4b0a-9975-bf39e9a71696";

  @Test
  void generateRtacCache_shouldFetchAndProcessRtacData() {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    when(folioExecutionContext.getOkapiUrl()).thenReturn(WIRE_MOCK.baseUrl());

    var future = rtacCacheGenerationService.generateRtacCache(INSTANCE_ID);
    future.join();

    var holdings = rtacHoldingRepository.findAllByIdInstanceId(UUID.fromString(INSTANCE_ID), PageRequest.of(0, 50));
    var itemWithLoans = holdings.get()
      .filter(entity -> entity.getRtacHolding().getDueDate() != null).findFirst();
    var piece = holdings.get()
      .filter(entity -> TypeEnum.PIECE.equals(entity.getRtacHolding().getType())).findFirst();
    var itemWithHoldCount = holdings.get()
      .filter(entity -> entity.getRtacHolding().getTotalHoldRequests() != null &&
        entity.getRtacHolding().getTotalHoldRequests() > 0).findFirst();

    assertEquals(5, holdings.getTotalElements());
    assertTrue(itemWithLoans.isPresent());
    assertEquals(ITEM_WITH_LOANS_AND_REQUESTS_ID, itemWithLoans.get().getRtacHolding().getId());
    assertTrue(piece.isPresent());
    assertEquals(PIECE_ID, piece.get().getRtacHolding().getId());
    assertTrue(itemWithHoldCount.isPresent());
    assertEquals(ITEM_WITH_LOANS_AND_REQUESTS_ID, itemWithHoldCount.get().getRtacHolding().getId());
  }

}
