package org.folio.rtaccache.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.rtaccache.service.RtacKafkaService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.folio.rtaccache.domain.dto.ResourceEvent;


@Log4j2
@RequiredArgsConstructor
public class KafkaMessageListener {

  public static final String HOLDINGS_RECORD_LISTENER_ID = "mod-rtac-cache-holdings-record-listener";
  private static final String ITEM_LISTENER_ID = "mod-rtac-cache-item-listener";
  private static final String LOCATIONS_LISTENER_ID = "mod-rtac-cache-location-listener";
  private static final String LIBRARIES_LISTENER_ID = "mod-rtac-cache-library-listener";

  private final SystemUserScopedExecutionService executionService;
  private final RtacKafkaService rtacKafkaService;


  @KafkaListener(
      id = HOLDINGS_RECORD_LISTENER_ID,
      containerFactory = "kafkaListenerContainerFactory",
      groupId = "#{folioKafkaProperties.listener['holdings-record'].groupId}",
      concurrency = "#{folioKafkaProperties.listener['holdings-record'].concurrency}",
      topicPattern = "#{folioKafkaProperties.listener['holdings-record'].topicPattern}")
  public void handleHoldingsRecord(ConsumerRecord<String, ResourceEvent> consumerRecord) {
    var tenantId = consumerRecord.value().getTenant();
    executionService.executeAsyncSystemUserScoped(tenantId, () -> {
      var resourceEvent = consumerRecord.value();
      rtacKafkaService.handleHoldingsResourceEvent(resourceEvent, tenantId);
    } );
  }

  @KafkaListener(
    id = ITEM_LISTENER_ID,
    containerFactory = "kafkaListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['item'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['item'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['item'].topicPattern}")
  public void handleItemRecord(ConsumerRecord<String, ResourceEvent> consumerRecord) {
    var tenantId = consumerRecord.value().getTenant();
    executionService.executeAsyncSystemUserScoped(tenantId, () -> {
      var resourceEvent = consumerRecord.value();
      rtacKafkaService.handleItemResourceEvent(resourceEvent, tenantId);
    } );
  }

  @KafkaListener(
    id = LOCATIONS_LISTENER_ID,
    containerFactory = "kafkaListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['location'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['location'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['location'].topicPattern}",
    autoStartup = "false")
  public void handleLocation(ConsumerRecord<String, ResourceEvent> consumerRecord) {
    //TODO implement Location events handling once Location cache is added.
    // The implementation should clear Location cache.
  }

  @KafkaListener(
    id = LIBRARIES_LISTENER_ID,
    containerFactory = "kafkaListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['library'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['library'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['library'].topicPattern}",
    autoStartup = "false")
  public void handleLibrary(ConsumerRecord<String, ResourceEvent> consumerRecord) {
    //TODO implement Library events handling once Library cache is added.
    // The implementation should clear Library cache.
  }

}
