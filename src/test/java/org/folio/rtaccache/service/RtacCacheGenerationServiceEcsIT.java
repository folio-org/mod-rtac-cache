package org.folio.rtaccache.service;

import static org.folio.rtaccache.TestConstant.TEST_CENTRAL_TENANT;
import static org.folio.rtaccache.TestConstant.TEST_MEMBER_TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.folio.rtaccache.BaseEcsIntegrationTest;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class RtacCacheGenerationServiceEcsIT extends BaseEcsIntegrationTest {

  @Autowired
  private RtacCacheGenerationService rtacCacheGenerationService;
  @Autowired
  private RtacHoldingRepository rtacHoldingRepository;
  @Autowired
  private FolioModuleMetadata folioModuleMetadata;
  @MockitoSpyBean
  private FolioExecutionContext folioExecutionContext;

  private static final String INSTANCE_ID_3 = "1dbab2d4-42f6-47e0-b0c6-013040bd19af";
  private static final String CENTRAL_PIECE_ID = "aaf2be29-a8cc-4b0a-9975-bf39e9a71696";

  @AfterEach
  void tearDown()
  {
    when(folioExecutionContext.getTenantId()).thenReturn(TEST_MEMBER_TENANT);
    rtacHoldingRepository.deleteAll();
    Mockito.reset(folioExecutionContext);
    when(folioExecutionContext.getTenantId()).thenReturn(TEST_CENTRAL_TENANT);
    rtacHoldingRepository.deleteAll();
    Mockito.reset(folioExecutionContext);
  }

  @Test
  void generateRtacCache_shouldFetchAndProcessRtacData() {

    Map<String, Collection<String>> headers = new HashMap<>(defaultHeaders(TEST_MEMBER_TENANT, MediaType.APPLICATION_JSON));
    var executionContext = new FolioExecutionContextSetter(folioModuleMetadata, headers);

    var future = rtacCacheGenerationService.generateRtacCache(INSTANCE_ID_3);
    future.join();

    executionContext.close();


    doReturn(TEST_MEMBER_TENANT).when(folioExecutionContext).getTenantId();
    var holdings = rtacHoldingRepository.findAllByIdInstanceId(UUID.fromString(INSTANCE_ID_3), PageRequest.of(0, 50));
    var centralPiece = holdings.get()
      .filter(entity -> TypeEnum.PIECE.equals(entity.getRtacHolding().getType())).findFirst();

    assertEquals(2, holdings.getTotalElements());
    assertTrue(centralPiece.isPresent());
    assertEquals(CENTRAL_PIECE_ID, centralPiece.get().getRtacHolding().getId());
    Mockito.reset(folioExecutionContext);
  }

}
