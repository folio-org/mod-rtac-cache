package org.folio.rtaccache.service.handler.impl;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.Loclib;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHoldingLibrary;
import org.folio.rtaccache.repository.RtacHoldingRepository;
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
  RtacHoldingRepository holdingRepository;
  @Mock
  ResourceEventUtil resourceEventUtil;
  @Mock
  RtacHoldingEntity holdingEntity;
  @Mock
  RtacHolding rtacHolding;
  @Mock
  RtacHoldingLibrary rtacLibrary;

  @Test
  void libraryUpdate_updatesLibraryFields_whenLibraryPresent() {
    var loclib = new Loclib().id("libId").name("New Name").code("NCode");
    when(resourceEventUtil.getNewFromInventoryEvent(resourceEvent, Loclib.class)).thenReturn(loclib);
    when(holdingEntity.getRtacHolding()).thenReturn(rtacHolding);
    when(rtacHolding.getLibrary()).thenReturn(rtacLibrary);
    when(holdingRepository.findAllByLibraryId("libId")).thenReturn(List.of(holdingEntity));

    handler.handle(resourceEvent);

    verify(cacheUtil).clearCache(LIBRARY_CACHE_NAME);
    verify(rtacLibrary).setName("New Name");
    verify(rtacLibrary).setCode("NCode");
    verify(holdingRepository).saveAll(argThat(this::matchUpdatedHoldingEntity));
  }

  @Test
  void libraryUpdate_skipsUpdate_whenRtacHoldingsWithLibraryIdMissing() {
    var loclib = new Loclib().id("libId2").name("Another").code("ACode");
    when(resourceEventUtil.getNewFromInventoryEvent(resourceEvent, Loclib.class)).thenReturn(loclib);
    when(holdingRepository.findAllByLibraryId("libId2")).thenReturn(List.of());

    handler.handle(resourceEvent);

    verify(cacheUtil).clearCache(LIBRARY_CACHE_NAME);
    verify(holdingRepository, never()).saveAll(anyList());
  }

  private boolean matchUpdatedHoldingEntity(List<RtacHoldingEntity> argument) {
    return argument.size() == 1 && argument.get(0) == holdingEntity;
  }

}
