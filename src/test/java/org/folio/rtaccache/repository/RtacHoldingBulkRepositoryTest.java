package org.folio.rtaccache.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class RtacHoldingBulkRepositoryTest extends BaseIntegrationTest {

  @Autowired
  private RtacHoldingBulkRepository rtacHoldingBulkRepository;
  @Autowired
  private RtacHoldingRepository rtacHoldingRepository;
  @MockitoSpyBean
  private FolioExecutionContext folioExecutionContext;
  private static final String instanceId = UUID.randomUUID().toString();
  private static final String itemId1 = UUID.randomUUID().toString();
  private static final String itemId2 = UUID.randomUUID().toString();

  @Test
  void testBulkUpsert_insertsRecords() throws SQLException, JsonProcessingException {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    var rtacHolding1 = new RtacHolding();
    rtacHolding1.setId(itemId1);
    rtacHolding1.setType(TypeEnum.ITEM);
    rtacHolding1.setInstanceId(instanceId);

    var rtacHolding2 = new RtacHolding();
    rtacHolding2.setId(itemId2);
    rtacHolding2.setType(TypeEnum.ITEM);
    rtacHolding2.setInstanceId(instanceId);

    var rtacHoldingEntity1 = new RtacHoldingEntity(RtacHoldingId.from(rtacHolding1), rtacHolding1, Instant.now());
    var rtacHoldingEntity2 = new RtacHoldingEntity(RtacHoldingId.from(rtacHolding2), rtacHolding2, Instant.now());
    var holdings = List.of(rtacHoldingEntity1, rtacHoldingEntity2);


    rtacHoldingBulkRepository.bulkUpsert(holdings);

    var retrieved = rtacHoldingRepository.findAll();
    assertEquals(2, retrieved.size());
    assertIterableEquals(holdings.stream().map(RtacHoldingEntity::getId).toList(),
      retrieved.stream().map(RtacHoldingEntity::getId).toList());
  }

  @Test
  void testBulkUpsert_updatesRecords() throws SQLException, JsonProcessingException {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    var rtacHolding1 = new RtacHolding();
    rtacHolding1.setId(itemId1);
    rtacHolding1.setType(TypeEnum.ITEM);
    rtacHolding1.setInstanceId(instanceId);
    rtacHolding1.setBarcode("test");

    var rtacHolding2 = new RtacHolding();
    rtacHolding2.setId(itemId2);
    rtacHolding2.setType(TypeEnum.ITEM);
    rtacHolding2.setInstanceId(instanceId);
    rtacHolding2.setBarcode("test");

    var rtacHoldingEntity1 = new RtacHoldingEntity(RtacHoldingId.from(rtacHolding1), rtacHolding1, Instant.now());
    var rtacHoldingEntity2 = new RtacHoldingEntity(RtacHoldingId.from(rtacHolding2), rtacHolding2, Instant.now());
    var holdings = List.of(rtacHoldingEntity1, rtacHoldingEntity2);


    rtacHoldingBulkRepository.bulkUpsert(holdings);

    var retrieved = rtacHoldingRepository.findAll();
    assertEquals(2, retrieved.size());
    assertIterableEquals(holdings.stream().map(RtacHoldingEntity::getId).toList(),
      retrieved.stream().map(RtacHoldingEntity::getId).toList());
    assertEquals("test", retrieved.get(0).getRtacHolding().getBarcode());
    assertEquals("test", retrieved.get(1).getRtacHolding().getBarcode());
  }

}
