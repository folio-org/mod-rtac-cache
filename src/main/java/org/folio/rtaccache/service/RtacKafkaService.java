package org.folio.rtaccache.service;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.HoldingsRecord;
import org.folio.rtaccache.domain.dto.Item;
import org.folio.rtaccache.domain.dto.ResourceEvent;
import org.folio.rtaccache.domain.dto.ResourceEventType;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class RtacKafkaService {

  private final Map<ResourceEventType, Consumer<ResourceEvent>> holdingsHandlers = Map.of(
    ResourceEventType.CREATE, this::handleHoldingsCreateEvent,
    ResourceEventType.UPDATE, this::handleHoldingsUpdateEvent,
    ResourceEventType.DELETE, this::handleHoldingsDeleteEvent
  );

  private final Map<ResourceEventType, Consumer<ResourceEvent>> itemHandlers = Map.of(
    ResourceEventType.CREATE, this::handleItemCreateEvent,
    ResourceEventType.UPDATE, this::handleItemUpdateEvent,
    ResourceEventType.DELETE, this::handleItemDeleteEvent
  );

  private final RtacHoldingRepository holdingRepository;
  private final ObjectMapper objectMapper;
  private final RtacHoldingMappingService rtacHoldingMappingService;

  private Map<TypeEnum, BiFunction<RtacHolding, HoldingsRecord, RtacHolding>> holdingsUpdateHandlers;

  @PostConstruct
  void init() {
    holdingsUpdateHandlers = Map.of(
      TypeEnum.HOLDING, this::mapHoldingType,
      TypeEnum.ITEM, rtacHoldingMappingService::mapForItemTypeFrom,
      TypeEnum.PIECE, rtacHoldingMappingService::mapForPieceTypeFrom
    );
  }

  /**
   * Handle holdings record event.
   *
   * @param resourceEvent {@link ResourceEvent}
   */
  @Transactional
  public void handleHoldingsResourceEvent(ResourceEvent resourceEvent, String tenantId) {
    log.info("Handling holdings record event for tenant {}: {}", tenantId, resourceEvent);
    var type = resourceEvent.getType();
    var handler = holdingsHandlers.get(type);
    if (handler == null) {
      log.warn("Unsupported or null holdings event type: {}", type);
      return;
    }
    handler.accept(resourceEvent);
  }

  /**
   * Handle item record event.
   *
   * @param resourceEvent {@link ResourceEvent}
   */
  @Transactional
  public void handleItemResourceEvent(ResourceEvent resourceEvent, String tenantId) {
    log.info("Handling item record event for tenant {}: {}", tenantId, resourceEvent);
    var type = resourceEvent.getType();
    var handler = itemHandlers.get(type);
    if (handler == null) {
      log.warn("Unsupported or null item event type: {}", type);
      return;
    }
    handler.accept(resourceEvent);
  }

  private void handleHoldingsCreateEvent(ResourceEvent resourceEvent) {
    var holdingsData = getNew(resourceEvent, HoldingsRecord.class);
    if (holdingsData == null) {
      log.warn("New holdings data is null. Skipping create.");
      return;
    }
    var rtacHoldingId = createRtacHoldingIdFromHoldings(holdingsData);
    var rtacHolding = rtacHoldingMappingService.mapFrom(holdingsData);
    var rtacHoldingEntity = new RtacHoldingEntity();
    rtacHoldingEntity.setId(rtacHoldingId);
    rtacHoldingEntity.setCreatedAt(Instant.now());
    rtacHoldingEntity.setRtacHolding(rtacHolding);
    holdingRepository.save(rtacHoldingEntity);
  }

  private void handleHoldingsUpdateEvent(ResourceEvent resourceEvent) {
    var holdingsData = getNew(resourceEvent, HoldingsRecord.class);
    if (holdingsData == null) {
      log.warn("New holdings data is null. Skipping update.");
      return;
    }
    var updatedEntities = holdingRepository.findAllByHoldingsId(holdingsData.getId())
      .stream()
      .peek(entity -> {
        var existingRtacHolding = entity.getRtacHolding();
        var handler = holdingsUpdateHandlers.get(existingRtacHolding.getType());
        entity.setRtacHolding(handler.apply(existingRtacHolding, holdingsData));
      })
      .toList();

    if (isNotEmpty(updatedEntities)) {
      holdingRepository.saveAll(updatedEntities);
    }
  }

  private void handleHoldingsDeleteEvent(ResourceEvent resourceEvent) {
    var holdingsData = getOld(resourceEvent, HoldingsRecord.class);
    if (holdingsData == null) {
      log.warn("Old holdings data is null. Skipping delete.");
      return;
    }
    var holdingsId = holdingsData.getId();
    holdingRepository.deleteAllByHoldingsId(holdingsId);
  }

  private void handleItemCreateEvent(ResourceEvent resourceEvent) {
    var item = getNew(resourceEvent, Item.class);
    holdingRepository.findByIdIdAndIdType(UUID.fromString(item.getHoldingsRecordId()), TypeEnum.HOLDING)
      .ifPresent(existingHoldingsEntity -> {
        var existingRtacHolding = existingHoldingsEntity.getRtacHolding();
        var newRtacHoldingId = createRtacHoldingIdFromItem(item, existingRtacHolding.getInstanceId());
        var newRtacHolding = rtacHoldingMappingService.mapForItemTypeFrom(existingRtacHolding, item);
        var newHoldingEntity = new RtacHoldingEntity();
        newHoldingEntity.setId(newRtacHoldingId);
        newHoldingEntity.setCreatedAt(Instant.now());
        newHoldingEntity.setRtacHolding(newRtacHolding);
        holdingRepository.save(newHoldingEntity);
      });
  }

  private void handleItemUpdateEvent(ResourceEvent resourceEvent) {
    var item = getNew(resourceEvent, Item.class);
    holdingRepository.findByIdIdAndIdType(UUID.fromString(item.getId()), TypeEnum.ITEM)
      .ifPresent(existingItemEntity -> {
        var existingRtacHolding = existingItemEntity.getRtacHolding();
        var updatedRtacHolding = rtacHoldingMappingService.mapForItemTypeFrom(existingRtacHolding, item);
        existingItemEntity.setRtacHolding(updatedRtacHolding);
        holdingRepository.save(existingItemEntity);
      });

  }

  private void handleItemDeleteEvent(ResourceEvent resourceEvent) {
    var item = getOld(resourceEvent, Item.class);
    holdingRepository.deleteByIdId(UUID.fromString(item.getId()));
  }

  private <T> T getNew(ResourceEvent event, Class<T> type) {
    var payload = event.getNew();
    if (payload == null) {
      return null;
    }
    return convert(payload, type);
  }

  private <T> T getOld(ResourceEvent event, Class<T> type) {
    var payload = event.getOld();
    if (payload == null) {
      return null;
    }
    return convert(payload, type);
  }

  private <T> T convert(Object source, Class<T> type) {
    return objectMapper.convertValue(source, type);
  }

  private RtacHoldingId createRtacHoldingIdFromHoldings(HoldingsRecord holdingsData) {
    var rtacHoldingId = new RtacHoldingId();
    rtacHoldingId.setId(UUID.fromString(holdingsData.getId()));
    rtacHoldingId.setType(TypeEnum.HOLDING);
    rtacHoldingId.setInstanceId(UUID.fromString(holdingsData.getInstanceId()));
    return rtacHoldingId;
  }

  private RtacHoldingId createRtacHoldingIdFromItem(Item item, String instanceId) {
    var newRtacHoldingId = new RtacHoldingId();
    newRtacHoldingId.setId(UUID.fromString(item.getId()));
    newRtacHoldingId.setType(TypeEnum.ITEM);
    newRtacHoldingId.setInstanceId(UUID.fromString(instanceId));
    return newRtacHoldingId;
  }

  private RtacHolding mapHoldingType(RtacHolding rtacHolding, HoldingsRecord holdings) {
    return rtacHoldingMappingService.mapFrom(holdings);
  }

}
