package org.folio.rtaccache.service;

import static org.folio.rtaccache.TestConstant.TEST_CENTRAL_TENANT;
import static org.folio.rtaccache.TestConstant.TEST_MEMBER_TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.folio.rtaccache.BaseEcsIntegrationTest;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

class RtacCacheGenerationServiceEcsIT extends BaseEcsIntegrationTest {

  @Autowired
  private RtacCacheGenerationService rtacCacheGenerationService;
  @Autowired
  private RtacHoldingRepository rtacHoldingRepository;

  private static final String INSTANCE_ID_3 = "1dbab2d4-42f6-47e0-b0c6-013040bd19af";
  private static final String CENTRAL_PIECE_ID = "aaf2be29-a8cc-4b0a-9975-bf39e9a71696";

  @AfterEach
  void tearDown() {
    withinTenant(TEST_MEMBER_TENANT, rtacHoldingRepository::deleteAll);
    withinTenant(TEST_CENTRAL_TENANT, rtacHoldingRepository::deleteAll);
  }

  @Test
  void generateRtacCache_shouldFetchAndProcessRtacData() {
    withinTenant(TEST_MEMBER_TENANT, () -> {
      //when
      rtacCacheGenerationService.generateRtacCache(INSTANCE_ID_3).join();
      //then
      var holdings = rtacHoldingRepository.findAllByIdInstanceId(UUID.fromString(INSTANCE_ID_3), PageRequest.of(0, 50));
      var centralPiece = holdings.get()
        .filter(entity -> TypeEnum.PIECE.equals(entity.getRtacHolding().getType())).findFirst();
      assertEquals(2, holdings.getTotalElements());
      assertTrue(centralPiece.isPresent());
      assertEquals(CENTRAL_PIECE_ID, centralPiece.get().getRtacHolding().getId());
    });
  }
}
