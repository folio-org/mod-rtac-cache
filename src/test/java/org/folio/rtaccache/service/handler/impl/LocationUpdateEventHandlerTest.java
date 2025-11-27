package org.folio.rtaccache.service.handler.impl;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.Location;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHoldingLocation;
import org.folio.rtaccache.repository.RtacHoldingRepository;
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
  RtacHoldingRepository holdingRepository;
  @Mock
  ResourceEventUtil resourceEventUtil;
  @Mock
  RtacHoldingEntity holdingEntity;
  @Mock
  RtacHolding rtacHolding;
  @Mock
  RtacHoldingLocation rtacLocation;

  @Test
  void locationUpdate_updatesLibraryFields_whenLibraryPresent() {
    var location = new Location().id("locId").name("New Name").code("NCode");
    when(resourceEventUtil.getNewFromInventoryEvent(eq(resourceEvent), eq(Location.class))).thenReturn(location);
    when(holdingEntity.getRtacHolding()).thenReturn(rtacHolding);
    when(rtacHolding.getLocation()).thenReturn(rtacLocation);
    when(holdingRepository.findAllByLocationId("locId")).thenReturn(List.of(holdingEntity));

    handler.handle(resourceEvent);

    verify(cacheUtil).clearCache(LOCATIONS_CACHE_NAME);
    verify(rtacLocation).setName("New Name");
    verify(rtacLocation).setCode("NCode");
    verify(holdingRepository).saveAll(argThat(this::matchUpdatedHoldingEntity));
  }

  @Test
  void locationUpdate_skipsUpdate_whenLibraryMissing() {
    var location = new Location().id("locId").name("New Name").code("NCode");
    when(resourceEventUtil.getNewFromInventoryEvent(eq(resourceEvent), eq(Location.class))).thenReturn(location);
    when(holdingRepository.findAllByLocationId("locId")).thenReturn(List.of());

    handler.handle(resourceEvent);

    verify(cacheUtil).clearCache(LOCATIONS_CACHE_NAME);
    verify(holdingRepository, never()).saveAll(anyList());
  }

  private boolean matchUpdatedHoldingEntity(List<RtacHoldingEntity> argument) {
    return argument.size() == 1 && argument.get(0) == holdingEntity;
  }

}
