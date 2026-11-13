package org.folio.rtaccache.repository;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Running every bulk repository method inside one transaction must check out exactly one
 * connection from the {@link DataSource}: the transaction's own. Uses the default pool size, so a
 * regression fails on the count rather than on a pool timeout.
 */
class RtacHoldingBulkRepositoryConnectionCountIT extends AbstractBulkRepositoryIT {

  @MockitoSpyBean DataSource dataSource;

  @Test
  void bulkMethods_checkOutExactlyOneConnectionFromTheDataSource() throws Exception {
    // folio-spring-base wraps the DataSource bean; the spy is the wrapper's delegate.
    var spy = ((DelegatingDataSource) dataSource).getTargetDataSource();
    clearInvocations(spy);

    inTransactionCallingAllBulkMethods();

    verify(spy, times(1)).getConnection();
  }
}
