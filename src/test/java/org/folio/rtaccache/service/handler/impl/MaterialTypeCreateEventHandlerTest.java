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
class MaterialTypeCreateEventHandlerTest {

  private static final String MATERIAL_TYPES_CACHE_NAME = "materialTypesMap";

  @InjectMocks
  MaterialTypeCreateEventHandler handler;

  @Mock
  CacheUtil cacheUtil;
  @Mock
  InventoryResourceEvent resourceEvent;

  @Test
  void materialTypeCreate_shouldClearCache_whenMaterialTypeIsCreated() {
    handler.handle(resourceEvent);

    verify(cacheUtil).clearCache(MATERIAL_TYPES_CACHE_NAME);
  }
}

