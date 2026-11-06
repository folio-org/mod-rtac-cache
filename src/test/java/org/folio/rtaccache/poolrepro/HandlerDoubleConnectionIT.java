package org.folio.rtaccache.poolrepro;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.Instance;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.handler.impl.InstanceUpdateEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Theory 2: a @Transactional Kafka handler that calls RtacHoldingBulkRepository methods using
 * dataSource.getConnection() holds TWO pool connections at once (the JPA tx connection plus the
 * bulk repo's own). With a pool of 1 the handler cannot complete.
 */
@TestPropertySource(properties = {
  "spring.datasource.hikari.maximum-pool-size=1",
  "spring.datasource.hikari.connection-timeout=2000"
})
class HandlerDoubleConnectionIT extends BaseIntegrationTest {

  @Autowired InstanceUpdateEventHandler handler;
  @Autowired RtacHoldingRepository repository;
  @Autowired DataSource dataSource;
  @MockitoBean ResourceEventUtil resourceEventUtil;

  @Test
  void transactionalHandler_needsTwoConnections() throws Exception {
    var instanceId = UUID.randomUUID();
    var instance = new Instance().id(instanceId.toString()).source("FOLIO").instanceFormatIds(List.of("f1"));
    when(resourceEventUtil.getOldFromInventoryEvent(any(), eq(Instance.class))).thenReturn(instance);
    when(resourceEventUtil.getNewFromInventoryEvent(any(), eq(Instance.class))).thenReturn(instance);

    withinTenant(TestConstant.TEST_TENANT, () -> repository.save(new RtacHoldingEntity(
      new RtacHoldingId(instanceId, TypeEnum.HOLDING, UUID.randomUUID()), false,
      new RtacHolding().type(TypeEnum.HOLDING), Instant.now())));

    var hikari = dataSource.unwrap(HikariDataSource.class);
    var peak = new AtomicInteger();
    var sampler = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        peak.accumulateAndGet(hikari.getHikariPoolMXBean().getActiveConnections(), Math::max);
        try { Thread.sleep(5); } catch (InterruptedException e) { return; }
      }
    });
    sampler.start();

    Throwable failure = null;
    try {
      withinTenant(TestConstant.TEST_TENANT, () -> handler.handle(new InventoryResourceEvent()));
    } catch (Throwable t) {
      failure = t;
    } finally {
      sampler.interrupt();
    }
    var formatIds = withinTenantWithResult(TestConstant.TEST_TENANT,
      () -> repository.findAllByIdInstanceId(instanceId, org.springframework.data.domain.PageRequest.of(0, 5))
        .get().map(e -> e.getRtacHolding().getInstanceFormatIds()).toList());
    System.out.println("REPRO2 count=" + withinTenantWithResult(TestConstant.TEST_TENANT,
      () -> repository.countByIdInstanceId(instanceId)) + " formatIdsAfter=" + formatIds);
    System.out.println("REPRO2 pool=1 peakActive=" + peak.get() + " failure=" + failure);
    var root = failure;
    while (root != null && root.getCause() != null) root = root.getCause();
    System.out.println("REPRO2 rootCause=" + root);
    // The handler catches the SQLException (pool timeout) and only logs it, so the symptom is a silently skipped update.
    assertThat(formatIds.getFirst()).as("update applied with a pool of 1 (fails => handler needed 2 connections)")
      .containsExactly("f1");
  }
}
