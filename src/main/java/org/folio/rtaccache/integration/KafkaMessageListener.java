package org.folio.rtaccache.integration;

import static org.folio.rtaccache.domain.dto.CirculationEntityType.LOAN;
import static org.folio.rtaccache.domain.dto.CirculationEntityType.REQUEST;
import static org.folio.rtaccache.domain.dto.InventoryEntityType.HOLDINGS;
import static org.folio.rtaccache.domain.dto.InventoryEntityType.ITEM;
import static org.folio.rtaccache.domain.dto.InventoryEntityType.ITEM_BOUND_WITH;
import static org.folio.rtaccache.domain.dto.InventoryEntityType.LIBRARY;
import static org.folio.rtaccache.domain.dto.InventoryEntityType.LOCATION;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.rtaccache.domain.dto.CirculationResourceEvent;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.PieceResourceEvent;
import org.folio.rtaccache.service.handler.EventHandlerFactory;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.kafka.annotation.KafkaListener;


@Log4j2
@RequiredArgsConstructor
public class KafkaMessageListener {

  public static final String HOLDINGS_RECORD_LISTENER_ID = "mod-rtac-cache-holdings-record-listener";
  private static final String ITEM_LISTENER_ID = "mod-rtac-cache-item-listener";
  private static final String LOAN_LISTENER_ID = "mod-rtac-cache-loan-listener";
  private static final String REQUEST_LISTENER_ID = "mod-rtac-cache-request-listener";
  private static final String PIECE_LISTENER_ID = "mod-rtac-cache-piece-listener";
  private static final String LOCATIONS_LISTENER_ID = "mod-rtac-cache-location-listener";
  private static final String LIBRARIES_LISTENER_ID = "mod-rtac-cache-library-listener";
  private static final String BOUND_WITH_LISTENER_ID = "mod-rtac-cache-bound-with-listener";
  private static final String FOLIO_TENANT_ID_HEADER = "folio.tenantId";

  private final SystemUserScopedExecutionService executionService;
  private final EventHandlerFactory eventHandlerFactory;


  @KafkaListener(
      id = HOLDINGS_RECORD_LISTENER_ID,
      containerFactory = "inventoryKafkaListenerContainerFactory",
      groupId = "#{folioKafkaProperties.listener['holdings-record'].groupId}",
      concurrency = "#{folioKafkaProperties.listener['holdings-record'].concurrency}",
      topicPattern = "#{folioKafkaProperties.listener['holdings-record'].topicPattern}")
  public void handleHoldingsRecord(ConsumerRecord<String, InventoryResourceEvent> consumerRecord) {
    var tenantId = consumerRecord.value().getTenant();
    executionService.executeAsyncSystemUserScoped(tenantId, () -> {
      var resourceEvent = consumerRecord.value();
      eventHandlerFactory.getInventoryHandler(resourceEvent.getType(), HOLDINGS)
        .handle(resourceEvent);
    } );
  }

  @KafkaListener(
    id = ITEM_LISTENER_ID,
    containerFactory = "inventoryKafkaListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['item'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['item'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['item'].topicPattern}")
  public void handleItemRecord(ConsumerRecord<String, InventoryResourceEvent> consumerRecord) {
    var tenantId = consumerRecord.value().getTenant();
    executionService.executeAsyncSystemUserScoped(tenantId, () -> {
      var resourceEvent = consumerRecord.value();
      eventHandlerFactory.getInventoryHandler(resourceEvent.getType(), ITEM)
        .handle(resourceEvent);
    } );
  }

  @KafkaListener(
    id = LOAN_LISTENER_ID,
    containerFactory = "circulationKafkaListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['loan'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['loan'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['loan'].topicPattern}")
  public void handleLoanRecord(ConsumerRecord<String, CirculationResourceEvent> consumerRecord) {
    var tenantId = consumerRecord.value().getTenant();
    executionService.executeAsyncSystemUserScoped(tenantId, () -> {
      var resourceEvent = consumerRecord.value();
      eventHandlerFactory.getCirculationHandler(resourceEvent.getType(), LOAN)
        .handle(resourceEvent);
    } );
  }

  @KafkaListener(
    id = REQUEST_LISTENER_ID,
    containerFactory = "circulationKafkaListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['request'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['request'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['request'].topicPattern}")
  public void handleRequestRecord(ConsumerRecord<String, CirculationResourceEvent> consumerRecord) {
    var tenantId = consumerRecord.value().getTenant();
    executionService.executeAsyncSystemUserScoped(tenantId, () -> {
      var resourceEvent = consumerRecord.value();
      eventHandlerFactory.getCirculationHandler(resourceEvent.getType(), REQUEST)
        .handle(resourceEvent);
    } );
  }

  @KafkaListener(
    id = PIECE_LISTENER_ID,
    containerFactory = "pieceKafkaListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['piece'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['piece'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['piece'].topicPattern}")
  public void handlePieceRecord(ConsumerRecord<String, PieceResourceEvent> consumerRecord) {
    var tenantId = getFolioTenantFromHeader(consumerRecord);
    executionService.executeAsyncSystemUserScoped(tenantId, () -> {
      var resourceEvent = consumerRecord.value();
      eventHandlerFactory.getPieceEventHandler(resourceEvent.getAction())
        .handle(resourceEvent);
    } );
  }

  @KafkaListener(
    id = LOCATIONS_LISTENER_ID,
    containerFactory = "inventoryKafkaListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['location'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['location'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['location'].topicPattern}",
    autoStartup = "false")
  public void handleLocationRecord(ConsumerRecord<String, InventoryResourceEvent> consumerRecord) {
    var tenantId = consumerRecord.value().getTenant();
    executionService.executeAsyncSystemUserScoped(tenantId, () -> {
      var resourceEvent = consumerRecord.value();
      eventHandlerFactory.getInventoryHandler(resourceEvent.getType(), LOCATION)
        .handle(resourceEvent);
    } );
  }

  @KafkaListener(
    id = LIBRARIES_LISTENER_ID,
    containerFactory = "inventoryKafkaListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['library'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['library'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['library'].topicPattern}",
    autoStartup = "false")
  public void handleLibraryRecord(ConsumerRecord<String, InventoryResourceEvent> consumerRecord) {
    var tenantId = consumerRecord.value().getTenant();
    executionService.executeAsyncSystemUserScoped(tenantId, () -> {
      var resourceEvent = consumerRecord.value();
      eventHandlerFactory.getInventoryHandler(resourceEvent.getType(), LIBRARY)
        .handle(resourceEvent);
    } );
  }

  @KafkaListener(
    id = BOUND_WITH_LISTENER_ID,
    containerFactory = "inventoryKafkaListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['bound-with'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['bound-with'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['bound-with'].topicPattern}")
  public void handleBoundWithRecord(ConsumerRecord<String, InventoryResourceEvent> consumerRecord) {
    var tenantId = consumerRecord.value().getTenant();
    executionService.executeAsyncSystemUserScoped(tenantId, () -> {
      var resourceEvent = consumerRecord.value();
      eventHandlerFactory.getInventoryHandler(resourceEvent.getType(), ITEM_BOUND_WITH)
        .handle(resourceEvent);
    } );
  }

  private String getFolioTenantFromHeader(ConsumerRecord<String, PieceResourceEvent> consumerRecord) {
    return new String(consumerRecord
      .headers()
      .lastHeader(FOLIO_TENANT_ID_HEADER)
      .value());
  }

}
