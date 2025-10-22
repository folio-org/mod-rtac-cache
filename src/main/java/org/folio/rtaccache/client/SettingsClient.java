package org.folio.rtaccache.client;

import org.folio.rtaccache.domain.dto.FolioCqlRequest;
import org.folio.rtaccache.domain.dto.Settings;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient("settings")
public interface SettingsClient {

  @GetMapping("/settings/entries")
  Settings getSettings(@SpringQueryMap FolioCqlRequest request);
}
