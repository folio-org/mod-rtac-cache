package org.folio.rtaccache.service.handler.impl;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.MaterialType;
import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.util.CacheUtil;
import org.folio.rtaccache.util.ResourceEventUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaterialTypeUpdateEventHandlerTest {

  private static final String MATERIAL_TYPES_CACHE_NAME = "materialTypesMap";

  @InjectMocks
  MaterialTypeUpdateEventHandler handler;

  @Mock
  CacheUtil cacheUtil;
  @Mock
  InventoryResourceEvent resourceEvent;
  @Mock
  RtacHoldingBulkRepository rtacHoldingBulkRepository;
  @Mock
  ResourceEventUtil resourceEventUtil;

  @Test
  void materialTypeUpdate_shouldUpdateRtacHoldings_whenNameChanged() throws SQLException {
    var oldMaterialType = new MaterialType().id("m-id").name("old");
    var newMaterialType = new MaterialType().id("m-id").name("new");
    when(resourceEventUtil.getOldFromInventoryEvent(resourceEvent, MaterialType.class)).thenReturn(oldMaterialType);
    when(resourceEventUtil.getNewFromInventoryEvent(resourceEvent, MaterialType.class)).thenReturn(newMaterialType);

    handler.handle(resourceEvent);

    verify(cacheUtil).clearCache(MATERIAL_TYPES_CACHE_NAME);
    verify(rtacHoldingBulkRepository).bulkUpdateMaterialTypeData(newMaterialType);
  }

  @Test
  void materialTypeUpdate_shouldSkipRtacUpdate_whenNameNotChanged() throws SQLException {
    var oldMaterialType = new MaterialType().id("m-id").name("same");
    var newMaterialType = new MaterialType().id("m-id").name("same");
    when(resourceEventUtil.getOldFromInventoryEvent(resourceEvent, MaterialType.class)).thenReturn(oldMaterialType);
    when(resourceEventUtil.getNewFromInventoryEvent(resourceEvent, MaterialType.class)).thenReturn(newMaterialType);

    handler.handle(resourceEvent);

    verify(cacheUtil).clearCache(MATERIAL_TYPES_CACHE_NAME);
    verify(rtacHoldingBulkRepository, never()).bulkUpdateMaterialTypeData(newMaterialType);
  }
}

