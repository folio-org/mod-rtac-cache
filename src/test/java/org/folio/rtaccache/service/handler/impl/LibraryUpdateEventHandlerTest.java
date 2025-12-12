package org.folio.rtaccache.service.handler.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.Loclib;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.util.CacheUtil;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LibraryUpdateEventHandlerTest {

  private static final String LIBRARY_CACHE_NAME = "libraryMap";

  @InjectMocks
  LibraryUpdateEventHandler handler;

  @Mock
  CacheUtil cacheUtil;
  @Mock
  InventoryResourceEvent resourceEvent;
  @Mock
  RtacHoldingBulkRepository rtacHoldingBulkRepository;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void libraryUpdate_updateRtacHolding_withLibraryData() throws SQLException {
    var loclib = new Loclib().id("libId").name("New Name").code("NCode");
    when(resourceEventUtil.getNewFromInventoryEvent(resourceEvent, Loclib.class)).thenReturn(loclib);

    handler.handle(resourceEvent);

    verify(cacheUtil).clearCache(LIBRARY_CACHE_NAME);
    verify(rtacHoldingBulkRepository).bulkUpdateLibraryData(loclib);
  }

}
