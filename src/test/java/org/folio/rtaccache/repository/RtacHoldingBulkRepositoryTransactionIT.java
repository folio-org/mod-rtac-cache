package org.folio.rtaccache.repository;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Bulk repository methods must use the connection of the surrounding transaction. With a pool of
 * one connection, a method that asks the pool for another connection throws a connection timeout.
 * See {@link RtacHoldingBulkRepositoryConnectionCountIT} for the same property asserted by counting.
 */
@TestPropertySource(properties = {
  "spring.datasource.hikari.maximum-pool-size=1",
  "spring.datasource.hikari.connection-timeout=2000"
})
class RtacHoldingBulkRepositoryTransactionIT extends AbstractBulkRepositoryIT {

  @Test
  void bulkMethods_useTheTransactionConnection() {
    assertThatCode(this::inTransactionCallingAllBulkMethods).doesNotThrowAnyException();
  }
}
