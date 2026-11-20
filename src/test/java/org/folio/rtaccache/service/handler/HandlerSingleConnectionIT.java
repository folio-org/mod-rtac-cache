package org.folio.rtaccache.service.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * End-to-end example of the MODRTACHCE-73 symptom, using one representative handler. A
 * {@code @Transactional} handler that runs a bulk update must do its work on the transaction's own
 * connection. With a pool of one connection, a handler that asks the pool for a second connection
 * times out, and the handler only logs the failure, silently dropping the update.
 *
 * <p>All handlers share the same bulk repository methods; those are covered directly, for every
 * method, by {@code RtacHoldingBulkRepositoryTransactionIT}.
 */
@TestPropertySource(properties = {
  "spring.datasource.hikari.maximum-pool-size=1",
  "spring.datasource.hikari.connection-timeout=2000"
})
class HandlerSingleConnectionIT extends BaseIntegrationTest {

  @Autowired RtacHoldingRepository repository;
  @Autowired InstanceUpdateEventHandler instanceHandler;
  @MockitoBean ResourceEventUtil resourceEventUtil;

  @Test
  void instanceUpdate_appliesFormatIds() {
    var instanceId = UUID.randomUUID();
    var instance = new Instance().id(instanceId.toString()).source("FOLIO").instanceFormatIds(List.of("f1"));
    when(resourceEventUtil.getOldFromInventoryEvent(any(), eq(Instance.class))).thenReturn(instance);
    when(resourceEventUtil.getNewFromInventoryEvent(any(), eq(Instance.class))).thenReturn(instance);
    withinTenant(TestConstant.TEST_TENANT, () -> repository.save(new RtacHoldingEntity(
      new RtacHoldingId(instanceId, TypeEnum.HOLDING, UUID.randomUUID()), false,
      new RtacHolding().type(TypeEnum.HOLDING), Instant.now())));

    withinTenant(TestConstant.TEST_TENANT, () -> instanceHandler.handle(new InventoryResourceEvent()));

    var formatIds = withinTenantWithResult(TestConstant.TEST_TENANT, () -> repository
      .findAllByIdInstanceId(instanceId, PageRequest.of(0, 5)).get()
      .map(RtacHoldingEntity::getRtacHolding).findFirst().orElseThrow().getInstanceFormatIds());
    assertThat(formatIds).containsExactly("f1");
  }
}
