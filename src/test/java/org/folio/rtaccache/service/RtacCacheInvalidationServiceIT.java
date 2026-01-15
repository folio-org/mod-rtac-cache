package org.folio.rtaccache.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.SneakyThrows;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RtacCacheInvalidationServiceIT extends BaseIntegrationTest {

  @Autowired
  private RtacCacheInvalidationService service;

  @Autowired
  private RtacHoldingRepository repository;

  @Autowired
  private RtacHoldingBulkRepository bulkRepository;


  @AfterEach
  void tearDown() {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext(TestConstant.TEST_TENANT))) {
      repository.deleteAll();
    }
  }


  @Test
  @SneakyThrows
  void shouldDeleteAllHoldingsForInstanceWhenOneHoldingIsExpired() {
    UUID instanceA = UUID.randomUUID();
    UUID instanceB = UUID.randomUUID();

    // Given
    // Instance A has 1 expired and 2 recent holdings
    // Instance B has all recent holdings
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext(TestConstant.TEST_TENANT))) {
      bulkRepository.bulkUpsert(List.of(
        createHolding(instanceA, UUID.randomUUID(), Instant.now().minus(10, ChronoUnit.DAYS)),
        createHolding(instanceA, UUID.randomUUID(), Instant.now().minus(3, ChronoUnit.DAYS)),
        createHolding(instanceA, UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.DAYS))
      ));

      bulkRepository.bulkUpsert(List.of(
        createHolding(instanceB, UUID.randomUUID(), Instant.now().minus(2, ChronoUnit.DAYS)),
        createHolding(instanceB, UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.DAYS))
      ));

      assertThat(repository.count()).isEqualTo(5);
    }

    // When
    service.invalidateOldHoldingEntries();

    // Then - only instance B holdings remain
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext(TestConstant.TEST_TENANT))) {
      assertThat(repository.count()).isEqualTo(2);
      assertThat(repository.countByIdInstanceId(instanceA)).isEqualTo(0);
      assertThat(repository.countByIdInstanceId(instanceB)).isEqualTo(2);
    }
  }

  @Test
  @SneakyThrows
  void shouldNotDeleteAnythingWhenAllHoldingsAreRecent() {
    UUID instanceA = UUID.randomUUID();
    UUID instanceB = UUID.randomUUID();

    // Given
    // Both instances have all recent holdings
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext(TestConstant.TEST_TENANT))) {
      bulkRepository.bulkUpsert(List.of(
        createHolding(instanceA, UUID.randomUUID(), Instant.now().minus(3, ChronoUnit.DAYS)),
        createHolding(instanceA, UUID.randomUUID(), Instant.now().minus(2, ChronoUnit.DAYS))
      ));

      bulkRepository.bulkUpsert(List.of(
        createHolding(instanceB, UUID.randomUUID(), Instant.now().minus(5, ChronoUnit.DAYS)),
        createHolding(instanceB, UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.DAYS))
      ));

      assertThat(repository.count()).isEqualTo(4);
    }

    // When
    service.invalidateOldHoldingEntries();

    // Then - nothing was deleted
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext(TestConstant.TEST_TENANT))) {
      assertThat(repository.count()).isEqualTo(4);
      assertThat(repository.countByIdInstanceId(instanceA)).isEqualTo(2);
      assertThat(repository.countByIdInstanceId(instanceB)).isEqualTo(2);
    }
  }

  @Test
  @SneakyThrows
  void shouldHandleMultipleInstancesWithExpiredHoldings() {
    UUID instanceA = UUID.randomUUID();
    UUID instanceB = UUID.randomUUID();
    UUID instanceC = UUID.randomUUID();

    // Given
    // Instance A has 1 expired + 1 recent
    // Instance B has all recent holdings
    // Instance C has 1 expired + 2 recent
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext(TestConstant.TEST_TENANT))) {
      bulkRepository.bulkUpsert(List.of(
        createHolding(instanceA, UUID.randomUUID(), Instant.now().minus(10, ChronoUnit.DAYS)),
        createHolding(instanceA, UUID.randomUUID(), Instant.now().minus(2, ChronoUnit.DAYS))
      ));

      bulkRepository.bulkUpsert(List.of(
        createHolding(instanceB, UUID.randomUUID(), Instant.now().minus(3, ChronoUnit.DAYS)),
        createHolding(instanceB, UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.DAYS))
      ));

      bulkRepository.bulkUpsert(List.of(
        createHolding(instanceC, UUID.randomUUID(), Instant.now().minus(8, ChronoUnit.DAYS)),
        createHolding(instanceC, UUID.randomUUID(), Instant.now().minus(4, ChronoUnit.DAYS)),
        createHolding(instanceC, UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.DAYS))
      ));

      assertThat(repository.count()).isEqualTo(7);
    }

    // When
    service.invalidateOldHoldingEntries();

    // Then - only instance B holdings remain
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext(TestConstant.TEST_TENANT))) {
      assertThat(repository.count()).isEqualTo(2);
      assertThat(repository.countByIdInstanceId(instanceA)).isEqualTo(0);
      assertThat(repository.countByIdInstanceId(instanceB)).isEqualTo(2);
      assertThat(repository.countByIdInstanceId(instanceC)).isEqualTo(0);
    }
  }

  private RtacHoldingEntity createHolding(UUID instanceId, UUID holdingId, Instant createdAt) {
    RtacHoldingId id = new RtacHoldingId();
    id.setId(holdingId);
    id.setInstanceId(instanceId);
    id.setType(RtacHolding.TypeEnum.ITEM);

    RtacHolding rtacHolding = new RtacHolding();
    rtacHolding.setId(holdingId.toString());
    rtacHolding.setInstanceId(instanceId.toString());
    rtacHolding.setType(RtacHolding.TypeEnum.ITEM);
    rtacHolding.setStatus("Available");

    return new RtacHoldingEntity(id, false, rtacHolding, createdAt);
  }
}