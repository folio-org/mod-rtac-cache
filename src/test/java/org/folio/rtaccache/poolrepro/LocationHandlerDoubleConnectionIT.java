package org.folio.rtaccache.poolrepro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.Location;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.handler.impl.LocationUpdateEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Same as HandlerDoubleConnectionIT, for a handler that makes NO JPA call before the bulk update. */
@TestPropertySource(properties = {
  "spring.datasource.hikari.maximum-pool-size=1",
  "spring.datasource.hikari.connection-timeout=2000"
})
class LocationHandlerDoubleConnectionIT extends BaseIntegrationTest {

  @Autowired LocationUpdateEventHandler handler;
  @Autowired RtacHoldingRepository repository;
  @MockitoBean ResourceEventUtil resourceEventUtil;

  @Test
  void handlerWithoutJpaCall_needsTwoConnections() {
    var locId = UUID.randomUUID().toString();
    var instanceId = UUID.randomUUID();
    var loc = new Location().id(locId).name("new-name").code("new-code");
    when(resourceEventUtil.getNewFromInventoryEvent(any(), eq(Location.class))).thenReturn(loc);
    var holding = new RtacHolding().type(TypeEnum.HOLDING)
      .location(new org.folio.rtaccache.domain.dto.RtacHoldingLocation().id(locId).name("old-name").code("old-code"));
    withinTenant(TestConstant.TEST_TENANT, () -> repository.save(new RtacHoldingEntity(
      new RtacHoldingId(instanceId, TypeEnum.HOLDING, UUID.randomUUID()), false, holding, Instant.now())));

    withinTenant(TestConstant.TEST_TENANT, () -> handler.handle(new InventoryResourceEvent()));

    var name = withinTenantWithResult(TestConstant.TEST_TENANT, () -> repository
      .findAllByIdInstanceId(instanceId, PageRequest.of(0, 5)).get()
      .map(e -> e.getRtacHolding().getLocation().getName()).findFirst().orElseThrow());
    System.out.println("REPRO3 locationNameAfter=" + name);
    assertThat(name).as("update applied with pool of 1").isEqualTo("new-name");
  }
}
