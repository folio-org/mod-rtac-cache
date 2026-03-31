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
class MaterialTypeDeleteEventHandlerTest {

  private static final String MATERIAL_TYPES_CACHE_NAME = "materialTypesMap";

  @InjectMocks
  MaterialTypeDeleteEventHandler handler;

  @Mock
  CacheUtil cacheUtil;
  @Mock
  InventoryResourceEvent resourceEvent;

  @Test
  void materialTypeDelete_shouldClearCache_whenMaterialTypeIsDeleted() {
    handler.handle(resourceEvent);

    verify(cacheUtil).clearCache(MATERIAL_TYPES_CACHE_NAME);
  }
}

