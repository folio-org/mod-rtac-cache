package org.folio.rtaccache.service.handler.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.Location;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.util.CacheUtil;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocationUpdateEventHandlerTest {

  private static final String LOCATIONS_CACHE_NAME = "locationsMap";

  @InjectMocks
  LocationUpdateEventHandler handler;

  @Mock
  CacheUtil cacheUtil;
  @Mock
  InventoryResourceEvent resourceEvent;
  @Mock
  RtacHoldingBulkRepository rtacHoldingBulkRepository;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void locationUpdate_updateRtacHolding_withLocationData() throws SQLException {
    var location = new Location().id("locId").name("New Name").code("NCode");
    when(resourceEventUtil.getNewFromInventoryEvent(resourceEvent, Location.class)).thenReturn(location);

    handler.handle(resourceEvent);

    verify(cacheUtil).clearCache(LOCATIONS_CACHE_NAME);
    verify(rtacHoldingBulkRepository).bulkUpdateLocationData(location);
  }

}
