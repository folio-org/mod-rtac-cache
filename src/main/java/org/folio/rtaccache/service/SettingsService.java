package org.folio.rtaccache.service;

import static org.folio.rtaccache.constant.RtacCacheConstant.LOAN_TENANT_SETTING_KEY;

import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.client.SettingsClient;
import org.folio.rtaccache.domain.dto.FolioCqlRequest;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettingsService {

  private final SettingsClient settingsClient;

  @Cacheable(
    value = "loanTenantCache",
    key = "'loanTenant'.concat('_').concat(@folioExecutionContext.getTenantId())"
  )
  public String getLoanTenant() {
    var cql = String.format("key==\"%s\"", LOAN_TENANT_SETTING_KEY);
    var request = new FolioCqlRequest(cql, 1, 0);
    var settings = settingsClient.getSettings(request);

    return (settings == null || settings.getItems().isEmpty())
      ? null
      : String.valueOf(settings.getItems().getFirst().getValue());
  }
}
