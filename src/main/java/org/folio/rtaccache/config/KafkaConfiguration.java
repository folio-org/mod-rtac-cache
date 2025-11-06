package org.folio.rtaccache.config;

import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.rtaccache.domain.dto.ResourceEvent;
import org.folio.rtaccache.integration.KafkaMessageListener;
import org.folio.rtaccache.service.RtacKafkaService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

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
  public ConcurrentKafkaListenerContainerFactory<String, ResourceEvent> kafkaListenerContainerFactory(
    ConsumerFactory<String, ResourceEvent> jsonNodeConsumerFactory) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, ResourceEvent>();
    factory.setBatchListener(false);
    factory.setConsumerFactory(jsonNodeConsumerFactory);
    return factory;
  }

  /**
   * Creates and configures {@link ConsumerFactory} as Spring bean.
   *
   * <p>Key type - {@link String}, value - {@link ResourceEvent}.</p>
   *
   * @return typed {@link ConsumerFactory} object as Spring bean.
   */
  @Bean
  public ConsumerFactory<String, ResourceEvent> jsonNodeConsumerFactory() {
    var deserializer = new JsonDeserializer<>(ResourceEvent.class, false);
    Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
    config.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
    return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public KafkaMessageListener kafkaMessageListener(RtacKafkaService rtacKafkaService,
    SystemUserScopedExecutionService executionService) {
    return new KafkaMessageListener(executionService, rtacKafkaService);
  }

}
