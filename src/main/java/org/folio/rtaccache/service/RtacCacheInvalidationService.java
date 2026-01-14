package org.folio.rtaccache.service;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.folio.rtaccache.config.CacheInvalidationJobProperties;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RtacCacheInvalidationService {

  private final RtacHoldingRepository rtacHoldingRepository;
  private final CacheInvalidationJobProperties properties;

  @Scheduled(cron = "#{@cacheInvalidationJobProperties.cron}")
  @Transactional
  public void invalidateOldHoldingEntries() {
    Instant cutoffTime = Instant.now().minus(Duration.ofDays(properties.getRetentionDays()));
    log.info("Starting cache invalidation for entries older than {} retention: {} days", cutoffTime, properties.getRetentionDays());

    int deletedCount = rtacHoldingRepository.deleteOldHoldingsAllTenants(cutoffTime);

    log.info("Cache invalidation completed: {} old entries deleted across all tenants", deletedCount);
  }
}