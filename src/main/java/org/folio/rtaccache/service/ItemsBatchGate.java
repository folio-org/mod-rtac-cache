package org.folio.rtaccache.service;

import java.util.function.Supplier;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Component;

/** Option 2: limits the whole items-batch step (HTTP fetch, mapping and upsert). */
@Component
public class ItemsBatchGate {

  @ConcurrencyLimit(limitString = "${rtac.cache.db-max-concurrency}")
  public <T> T run(Supplier<T> work) {
    return work.get();
  }
}
