package org.folio.rtaccache.service.handler;

import org.folio.rtaccache.domain.dto.CirculationEntityType;
import org.folio.rtaccache.domain.dto.CirculationEventType;
import org.folio.rtaccache.domain.dto.CirculationResourceEvent;

public interface CirculationEventHandler extends EventHandler<CirculationEventType, CirculationResourceEvent> {

  CirculationEventType getEventType();

  void handle(CirculationResourceEvent resourceEvent);

  CirculationEntityType getEntityType();

}
