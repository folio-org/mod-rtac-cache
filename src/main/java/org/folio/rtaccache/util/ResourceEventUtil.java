package org.folio.rtaccache.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.dto.CirculationResourceEvent;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResourceEventUtil {

  private final ObjectMapper objectMapper;

  public <T> T getNewFromInventoryEvent(InventoryResourceEvent event, Class<T> type) {
    var payload = event.getNew();
    return convert(payload, type);
  }

  public <T> T getNewFromCirculationEvent(CirculationResourceEvent event, Class<T> type) {
    var payload = event.getData().getNew();
    return convert(payload, type);
  }

  public <T> T getOldFromInventoryEvent(InventoryResourceEvent event, Class<T> type) {
    var payload = event.getOld();
    return convert(payload, type);
  }

  public <T> T getOldFromCirculationEvent(CirculationResourceEvent event, Class<T> type) {
    var payload = event.getData().getOld();
    return convert(payload, type);
  }

  private <T> T convert(Object source, Class<T> type) {
    return objectMapper.convertValue(source, type);
  }
}
