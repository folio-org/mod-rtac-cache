package org.folio.rtaccache.service.handler.impl;

import static org.mockito.Mockito.verify;

import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.util.CacheUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LibraryCreateEventHandlerTest {

  private static final String LOCATIONS_CACHE_NAME = "libraryMap";

  @InjectMocks
  LibraryCreateEventHandler handler;

  @Mock
  CacheUtil cacheUtil;
  @Mock
  InventoryResourceEvent resourceEvent;

  @Test
  void createLocation_shouldClearCache_whenLocationIsCreated() {
    handler.handle(resourceEvent);

    verify(cacheUtil).clearCache(LOCATIONS_CACHE_NAME);
  }

}
