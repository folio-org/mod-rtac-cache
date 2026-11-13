package org.folio.rtaccache.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.Instance;
import org.folio.rtaccache.domain.dto.LoanType;
import org.folio.rtaccache.domain.dto.Loclib;
import org.folio.rtaccache.domain.dto.Location;
import org.folio.rtaccache.domain.dto.MaterialType;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** Shared helper for tests that run every bulk repository method inside one transaction. */
abstract class AbstractBulkRepositoryIT extends BaseIntegrationTest {

  @Autowired RtacHoldingBulkRepository bulkRepository;
  @Autowired RtacHoldingRepository repository;
  @Autowired TransactionTemplate transactionTemplate;

  void inTransactionCallingAllBulkMethods() {
    var instanceId = UUID.randomUUID();
    var entity = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.HOLDING, UUID.randomUUID()),
      false, new RtacHolding().type(TypeEnum.HOLDING), Instant.now());

    withinTenant(TestConstant.TEST_TENANT, () -> transactionTemplate.executeWithoutResult(status -> {
      // Touch the transaction first so its connection is definitely checked out.
      repository.countByIdInstanceId(instanceId);
      try {
        bulkRepository.bulkUpsert(List.of(entity));
        bulkRepository.bulkUpdateLocationData(new Location().id(UUID.randomUUID().toString()).name("n").code("c"));
        bulkRepository.bulkUpdateLibraryData(new Loclib().id(UUID.randomUUID().toString()).name("n").code("c"));
        bulkRepository.bulkUpdateMaterialTypeData(new MaterialType().id(UUID.randomUUID().toString()).name("n"));
        bulkRepository.bulkUpdateLoanTypeData(new LoanType().name("old"), new LoanType().name("new"));
        bulkRepository.bulkUpdateInstanceFormatIds(
          new Instance().id(instanceId.toString()).instanceFormatIds(List.of("f1")));
        bulkRepository.bulkMarkHoldingsAsSharedByInstanceId(instanceId);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }));
  }
}
