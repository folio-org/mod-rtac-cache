package org.folio.rtaccache.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.rtaccache.domain.dto.CirculationResourceEvent;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.PieceResourceEvent;
import org.folio.rtaccache.integration.KafkaMessageListener;
import org.folio.rtaccache.service.ConsortiaService;
import org.folio.rtaccache.service.handler.EventHandlerFactory;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.JacksonMapperUtils;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;

/**
 * Responsible for configuration of kafka consumer bean factories at application startup for kafka listeners.
 */
@Log4j2
@Configuration
@RequiredArgsConstructor
public class KafkaConfiguration {

  private final KafkaProperties kafkaProperties;

  /**
   * Creates and configures {@link ConcurrentKafkaListenerContainerFactory} as Spring bean for consuming resource events
   * from Apache Kafka.
   *
   * @return {@link ConcurrentKafkaListenerContainerFactory} object as Spring bean.
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, InventoryResourceEvent> inventoryKafkaListenerContainerFactory() {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, InventoryResourceEvent>();
    factory.setBatchListener(false);
    factory.setConsumerFactory(getInventoryResourceEventConsumerFactory());
    return factory;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, CirculationResourceEvent> circulationKafkaListenerContainerFactory() {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, CirculationResourceEvent>();
    factory.setBatchListener(false);
    factory.setConsumerFactory(getCirculationResourceEventConsumerFactory());
    return factory;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, PieceResourceEvent> pieceKafkaListenerContainerFactory() {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, PieceResourceEvent>();
    factory.setBatchListener(false);
    factory.setConsumerFactory(getPieceResourceEventConsumerFactory());
    return factory;
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public KafkaMessageListener kafkaMessageListener(SystemUserScopedExecutionService executionService,
    EventHandlerFactory eventHandlerFactory, ConsortiaService consortiaService,
    AsyncTaskExecutor applicationTaskExecutor) {
    return new KafkaMessageListener(executionService, eventHandlerFactory, consortiaService, applicationTaskExecutor);
  }

  private ConsumerFactory<String, InventoryResourceEvent> getInventoryResourceEventConsumerFactory() {
    var deserializer = new JacksonJsonDeserializer<>(InventoryResourceEvent.class, kafkaJsonMapper(), false);
    Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties());
    config.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
    return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
  }

  private ConsumerFactory<String, CirculationResourceEvent> getCirculationResourceEventConsumerFactory() {
    var deserializer = new JacksonJsonDeserializer<>(CirculationResourceEvent.class, kafkaJsonMapper(), false);
    Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties());
    config.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
    return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
  }

  private ConsumerFactory<String, PieceResourceEvent> getPieceResourceEventConsumerFactory() {
    var deserializer = new JacksonJsonDeserializer<>(PieceResourceEvent.class, kafkaJsonMapper(), false);
    Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties());
    config.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
    return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
  }

  /**
   * Adds {@link UnknownEventEnumDeserializationProblemHandler} on top of Spring Kafka's default JsonMapper
   * so that inventory/circulation/piece event-type enums fall back to their UNKNOWN constant instead of
   * failing deserialization of the whole record when an external producer sends a value not in the spec.
   */
  private JsonMapper kafkaJsonMapper() {
    return JacksonMapperUtils.enhancedJsonMapper().rebuild()
      .addHandler(new UnknownEventEnumDeserializationProblemHandler())
      .build();
  }

}
