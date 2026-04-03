package org.folio.rtaccache.client;

import java.util.Map;
import org.folio.rtaccache.domain.dto.Settings;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface SettingsClient {

  @GetExchange("settings/entries")
  Settings getSettings(@RequestParam Map<String, String> queryParametersMap);
}
