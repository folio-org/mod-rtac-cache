package org.folio.rtaccache.service;

import static org.folio.rtaccache.constant.RtacCacheConstant.LOAN_TENANT_CACHE_NAME;
import static org.folio.rtaccache.constant.RtacCacheConstant.LOAN_TENANT_SETTING_KEY;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.rtaccache.client.SettingsClient;
import org.folio.rtaccache.domain.dto.FolioCqlRequest;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class SettingsService {

  private final SettingsClient settingsClient;

  @Cacheable(
    value = LOAN_TENANT_CACHE_NAME,
    key = "'loanTenant'.concat('_').concat(@folioExecutionContext.getTenantId())"
  )
  public String getLoanTenant() {
    var cql = String.format("key==\"%s\"", LOAN_TENANT_SETTING_KEY);
    var request = new FolioCqlRequest(cql, 1, 0);
    var settings = settingsClient.getSettings(request);

    var loanTenant = (settings == null || settings.getItems().isEmpty())
      ? null
      : String.valueOf(settings.getItems().getFirst().getValue());

    log.info("Resolved loan tenant from settings: {}", loanTenant);

    return loanTenant;
  }
}
