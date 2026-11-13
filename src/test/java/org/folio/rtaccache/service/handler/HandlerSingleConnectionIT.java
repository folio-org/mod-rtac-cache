package org.folio.rtaccache.service.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.Instance;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.LoanType;
import org.folio.rtaccache.domain.dto.Loclib;
import org.folio.rtaccache.domain.dto.Location;
import org.folio.rtaccache.domain.dto.MaterialType;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.domain.dto.RtacHoldingLibrary;
import org.folio.rtaccache.domain.dto.RtacHoldingLocation;
import org.folio.rtaccache.domain.dto.RtacHoldingMaterialType;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.handler.impl.InstanceUpdateEventHandler;
import org.folio.rtaccache.service.handler.impl.LibraryUpdateEventHandler;
import org.folio.rtaccache.service.handler.impl.LoanTypeUpdateEventHandler;
import org.folio.rtaccache.service.handler.impl.LocationUpdateEventHandler;
import org.folio.rtaccache.service.handler.impl.MaterialTypeUpdateEventHandler;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The {@code @Transactional} handlers that run bulk updates must do their work on the transaction's
 * own connection. With a pool of one connection, a handler that asks the pool for a second
 * connection times out, and the handler only logs the failure, silently dropping the update.
 */
@TestPropertySource(properties = {
  "spring.datasource.hikari.maximum-pool-size=1",
  "spring.datasource.hikari.connection-timeout=2000"
})
class HandlerSingleConnectionIT extends BaseIntegrationTest {

  @Autowired RtacHoldingRepository repository;
  @Autowired InstanceUpdateEventHandler instanceHandler;
  @Autowired LocationUpdateEventHandler locationHandler;
  @Autowired LibraryUpdateEventHandler libraryHandler;
  @Autowired MaterialTypeUpdateEventHandler materialTypeHandler;
  @Autowired LoanTypeUpdateEventHandler loanTypeHandler;
  @MockitoBean ResourceEventUtil resourceEventUtil;

  @Test
  void instanceUpdate_appliesFormatIds() {
    var instance = new Instance().id(UUID.randomUUID().toString()).source("FOLIO").instanceFormatIds(List.of("f1"));
    when(resourceEventUtil.getOldFromInventoryEvent(any(), eq(Instance.class))).thenReturn(instance);
    when(resourceEventUtil.getNewFromInventoryEvent(any(), eq(Instance.class))).thenReturn(instance);

    var after = runHandler(UUID.fromString(instance.getId()), new RtacHolding().type(TypeEnum.HOLDING),
      instanceHandler::handle, RtacHolding::getInstanceFormatIds);

    assertThat(after).containsExactly("f1");
  }

  @Test
  void instanceBecomesShared_marksHoldingsShared() {
    var id = UUID.randomUUID();
    var oldInstance = new Instance().id(id.toString()).source("FOLIO");
    var newInstance = new Instance().id(id.toString()).source("CONSORTIUM-FOLIO").instanceFormatIds(List.of());
    when(resourceEventUtil.getOldFromInventoryEvent(any(), eq(Instance.class))).thenReturn(oldInstance);
    when(resourceEventUtil.getNewFromInventoryEvent(any(), eq(Instance.class))).thenReturn(newInstance);
    seed(id, new RtacHolding().type(TypeEnum.HOLDING));

    withinTenant(TestConstant.TEST_TENANT, () -> instanceHandler.handle(new InventoryResourceEvent()));

    var shared = withinTenantWithResult(TestConstant.TEST_TENANT, () -> repository
      .findAllByIdInstanceId(id, PageRequest.of(0, 5)).get().map(RtacHoldingEntity::isShared).findFirst().orElseThrow());
    assertThat(shared).isTrue();
  }

  @Test
  void locationUpdate_appliesName() {
    var loc = new Location().id(UUID.randomUUID().toString()).name("new-name").code("new-code");
    when(resourceEventUtil.getNewFromInventoryEvent(any(), eq(Location.class))).thenReturn(loc);
    var holding = new RtacHolding().type(TypeEnum.HOLDING)
      .location(new RtacHoldingLocation().id(loc.getId()).name("old-name").code("old-code"));

    var after = runHandler(UUID.randomUUID(), holding, locationHandler::handle, h -> h.getLocation().getName());

    assertThat(after).isEqualTo("new-name");
  }

  @Test
  void libraryUpdate_appliesName() {
    var lib = new Loclib().id(UUID.randomUUID().toString()).name("new-name").code("new-code");
    when(resourceEventUtil.getNewFromInventoryEvent(any(), eq(Loclib.class))).thenReturn(lib);
    var holding = new RtacHolding().type(TypeEnum.HOLDING)
      .library(new RtacHoldingLibrary().id(lib.getId()).name("old-name").code("old-code"));

    var after = runHandler(UUID.randomUUID(), holding, libraryHandler::handle, h -> h.getLibrary().getName());

    assertThat(after).isEqualTo("new-name");
  }

  @Test
  void materialTypeUpdate_appliesName() {
    var id = UUID.randomUUID().toString();
    when(resourceEventUtil.getOldFromInventoryEvent(any(), eq(MaterialType.class)))
      .thenReturn(new MaterialType().id(id).name("old-name"));
    when(resourceEventUtil.getNewFromInventoryEvent(any(), eq(MaterialType.class)))
      .thenReturn(new MaterialType().id(id).name("new-name"));
    var holding = new RtacHolding().type(TypeEnum.HOLDING)
      .materialType(new RtacHoldingMaterialType().id(id).name("old-name"));

    var after = runHandler(UUID.randomUUID(), holding, materialTypeHandler::handle, h -> h.getMaterialType().getName());

    assertThat(after).isEqualTo("new-name");
  }

  @Test
  void loanTypeUpdate_appliesName() {
    when(resourceEventUtil.getOldFromInventoryEvent(any(), eq(LoanType.class)))
      .thenReturn(new LoanType().name("old-loan-type"));
    when(resourceEventUtil.getNewFromInventoryEvent(any(), eq(LoanType.class)))
      .thenReturn(new LoanType().name("new-loan-type"));
    var holding = new RtacHolding().type(TypeEnum.HOLDING).permanentLoanType("old-loan-type");

    var after = runHandler(UUID.randomUUID(), holding, loanTypeHandler::handle, RtacHolding::getPermanentLoanType);

    assertThat(after).isEqualTo("new-loan-type");
  }

  /** Seeds one holding, runs the handler within the tenant, and returns the value read back from the row. */
  private <T> T runHandler(UUID instanceId, RtacHolding holding, Consumer<InventoryResourceEvent> handler,
    Function<RtacHolding, T> read) {
    seed(instanceId, holding);
    withinTenant(TestConstant.TEST_TENANT, () -> handler.accept(new InventoryResourceEvent()));
    return withinTenantWithResult(TestConstant.TEST_TENANT, () -> read.apply(repository
      .findAllByIdInstanceId(instanceId, PageRequest.of(0, 5)).get()
      .map(RtacHoldingEntity::getRtacHolding).findFirst().orElseThrow()));
  }

  private void seed(UUID instanceId, RtacHolding holding) {
    withinTenant(TestConstant.TEST_TENANT, () -> repository.save(new RtacHoldingEntity(
      new RtacHoldingId(instanceId, TypeEnum.HOLDING, UUID.randomUUID()), false, holding, Instant.now())));
  }
}
