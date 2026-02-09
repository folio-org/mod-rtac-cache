package org.folio.rtaccache.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.client.SettingsClient;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.dto.FolioCqlRequest;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.domain.dto.Settings;
import org.folio.rtaccache.domain.dto.SettingsItemsInner;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class RtacCacheGenerationServiceIT extends BaseIntegrationTest {

  @Autowired
  private RtacCacheGenerationService rtacCacheGenerationService;

  @Autowired
  private RtacHoldingRepository rtacHoldingRepository;

  @MockitoSpyBean
  private FolioExecutionContext folioExecutionContext;

  @MockitoBean
  private SettingsClient settingsClient;

  @Autowired(required = false)
  private CacheManager cacheManager;

  private static final String INSTANCE_ID_1 = "4de861ab-af9e-4247-bc16-c547d982eb5d";
  private static final String INSTANCE_ID_2 = "5dbab2d4-42f6-47e0-b0c6-023040bd19ff";
  private static final String ITEM_WITH_LOANS_AND_REQUESTS_ID = "9a772288-ead3-4033-b07b-87eff643710f";
  private static final String BOUND_WITH_ITEM_ID = "02b9b326-903b-41d7-b947-fc809e9d38c1";
  private static final String PIECE_ID = "aaf7be29-a8cc-4b0a-9975-bf39e9a71696";

  @BeforeEach
  void setUp() {
    // Because of the loan tenant settings cache, we need to clear it before each test to ensure different scenarios are tested properly
    if (cacheManager != null) {
      cacheManager.getCacheNames().forEach(name -> {
        var cache = cacheManager.getCache(name);
        if (cache != null) {
          cache.clear();
        }
      });
    }

    // No loan-tenant configured for tests by default. Individual tests will override this if needed.
    lenient().when(settingsClient.getSettings(ArgumentMatchers.any(FolioCqlRequest.class)))
      .thenReturn(new Settings().items(List.of()));
  }

  @AfterEach
  void tearDown() {
    rtacHoldingRepository.deleteAll();
  }

  @Test
  void generateRtacCache_shouldFetchAndProcessRtacData() {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    when(folioExecutionContext.getOkapiUrl()).thenReturn(WIRE_MOCK.baseUrl());

    var future = rtacCacheGenerationService.generateRtacCache(INSTANCE_ID_1);
    future.join();

    var holdings = rtacHoldingRepository.findAllByIdInstanceId(UUID.fromString(INSTANCE_ID_1), PageRequest.of(0, 50));
    var itemWithLoans = holdings.get()
      .filter(entity -> entity.getRtacHolding().getDueDate() != null).findFirst();
    var piece = holdings.get()
      .filter(entity -> TypeEnum.PIECE.equals(entity.getRtacHolding().getType())).findFirst();
    var itemWithHoldCount = holdings.get()
      .filter(entity -> entity.getRtacHolding().getTotalHoldRequests() != null &&
        entity.getRtacHolding().getTotalHoldRequests() > 0).findFirst();

    assertEquals(7, holdings.getTotalElements());
    assertTrue(itemWithLoans.isPresent());
    assertEquals(ITEM_WITH_LOANS_AND_REQUESTS_ID, itemWithLoans.get().getRtacHolding().getId());
    assertTrue(piece.isPresent());
    assertEquals(PIECE_ID, piece.get().getRtacHolding().getId());
    assertTrue(itemWithHoldCount.isPresent());
    assertEquals(ITEM_WITH_LOANS_AND_REQUESTS_ID, itemWithHoldCount.get().getRtacHolding().getId());
    assertTrue(holdings.get().allMatch(RtacHoldingEntity::isShared));
  }

  @Test
  void generateRtacCache_shouldProcessBoundWithItem() {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    when(folioExecutionContext.getOkapiUrl()).thenReturn(WIRE_MOCK.baseUrl());

    var future = rtacCacheGenerationService.generateRtacCache(INSTANCE_ID_2);
    future.join();

    var holdings = rtacHoldingRepository.findAllByIdInstanceId(UUID.fromString(INSTANCE_ID_2), PageRequest.of(0, 50));
    assertEquals(2, holdings.getTotalElements());
    var rtacHoldingEntity = holdings.get()
      .filter(entity -> entity.getRtacHolding().getType() == TypeEnum.ITEM)
      .findFirst()
      .get();
    assertEquals(BOUND_WITH_ITEM_ID, rtacHoldingEntity.getRtacHolding().getId());
    assertTrue(rtacHoldingEntity.getRtacHolding().getIsBoundWith());
    assertFalse(rtacHoldingEntity.isShared());
  }

  @Test
  void generateRtacCache_shouldUseLoanTenantFromSettingsAndUseCache() {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    when(folioExecutionContext.getOkapiUrl()).thenReturn(WIRE_MOCK.baseUrl());

    var settingsWithLoanTenant = new Settings()
      .items(List.of(new SettingsItemsInner().value(TestConstant.TEST_CENTRAL_TENANT)));

    when(settingsClient.getSettings(ArgumentMatchers.any(FolioCqlRequest.class)))
      .thenReturn(settingsWithLoanTenant);

    // Generate two different instances under the same tenant
    var future1 = rtacCacheGenerationService.generateRtacCache(INSTANCE_ID_1);
    future1.join();

    var future2 = rtacCacheGenerationService.generateRtacCache(INSTANCE_ID_2);
    future2.join();

    // SettingsClient.getSettings should have been called only once because of cache
    verify(settingsClient, times(1))
      .getSettings(ArgumentMatchers.any(FolioCqlRequest.class));
  }

}
