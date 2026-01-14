package org.folio.rtaccache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rtac.cache.invalidation")
public class CacheInvalidationJobProperties {

  private String cron;
  private int retentionDays;

}