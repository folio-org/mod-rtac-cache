package org.folio.rtaccache.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.rtaccache.domain.dto.CirculationResourceEvent;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.PieceResourceEvent;
import org.folio.rtaccache.integration.KafkaMessageListener;
import org.folio.rtaccache.service.ConsortiaService;
import org.folio.rtaccache.service.handler.EventHandlerFactory;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.spring.tools.kafka.FolioKafkaProperties;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.JacksonMapperUtils;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;

/**
 * Responsible for configuration of kafka consumer bean factories at application startup for kafka listeners.
 */
@Log4j2
@Configuration
@RequiredArgsConstructor
public class KafkaConfiguration {

  private static final double RETRY_BACKOFF_MULTIPLIER = 2.0;

  private final KafkaProperties kafkaProperties;
  private final FolioKafkaProperties folioKafkaProperties;

  /**
   * Creates and configures {@link ConcurrentKafkaListenerContainerFactory} as Spring bean for consuming resource events
   * from Apache Kafka.
   *
   * @return {@link ConcurrentKafkaListenerContainerFactory} object as Spring bean.
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, InventoryResourceEvent> inventoryKafkaListenerContainerFactory(
    CommonErrorHandler kafkaErrorHandler) {
    return listenerContainerFactory(InventoryResourceEvent.class, kafkaErrorHandler);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, CirculationResourceEvent>
      circulationKafkaListenerContainerFactory(CommonErrorHandler kafkaErrorHandler) {
    return listenerContainerFactory(CirculationResourceEvent.class, kafkaErrorHandler);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, PieceResourceEvent> pieceKafkaListenerContainerFactory(
    CommonErrorHandler kafkaErrorHandler) {
    return listenerContainerFactory(PieceResourceEvent.class, kafkaErrorHandler);
  }

  /**
   * Retries a failed record with a growing delay and, once the attempts are exhausted, logs it and lets the container
   * commit past it so that one bad record cannot stall its partition forever. No dead-letter topic is configured, so
   * an exhausted record is dropped - the log line from {@link #loggingRetryListener()} is the only trace of it.
   *
   * <p>Deserialization failures are not retried at all: {@link ErrorHandlingDeserializer} defers them to the listener
   * container rather than throwing inside {@code Consumer.poll()}, and {@code DeserializationException} is part of
   * {@code ExceptionClassifier.defaultFatalExceptionsList()}, so such records are skipped on the first attempt
   * instead of burning the retry budget.</p>
   *
   * <p>{@code setMaxAttempts(n)} permits n retries on top of the initial delivery, so the configured
   * {@code retry-delivery-attempts: 6} means seven deliveries in all. With the default 2000ms interval and a
   * multiplier of 2 that is roughly 90 seconds - capped by {@code ExponentialBackOff.DEFAULT_MAX_INTERVAL} of 30s -
   * during which the partition makes no progress, so raising either property has a direct throughput cost.</p>
   */
  @Bean
  public DefaultErrorHandler kafkaErrorHandler() {
    var backOff = new ExponentialBackOff(folioKafkaProperties.getRetryIntervalMs(), RETRY_BACKOFF_MULTIPLIER);
    backOff.setMaxAttempts(folioKafkaProperties.getRetryDeliveryAttempts());

    var errorHandler = new DefaultErrorHandler(backOff);
    // Nothing about a malformed payload or a programming error improves by being retried.
    errorHandler.addNotRetryableExceptions(NullPointerException.class, IllegalArgumentException.class);
    errorHandler.setRetryListeners(loggingRetryListener());
    return errorHandler;
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public KafkaMessageListener kafkaMessageListener(SystemUserScopedExecutionService executionService,
    EventHandlerFactory eventHandlerFactory, ConsortiaService consortiaService,
    AsyncTaskExecutor applicationTaskExecutor) {
    return new KafkaMessageListener(executionService, eventHandlerFactory, consortiaService, applicationTaskExecutor);
  }

  private <T> ConcurrentKafkaListenerContainerFactory<String, T> listenerContainerFactory(Class<T> eventType,
    CommonErrorHandler errorHandler) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, T>();
    factory.setBatchListener(false);
    factory.setConsumerFactory(consumerFactory(eventType));
    factory.setCommonErrorHandler(errorHandler);
    return factory;
  }

  /**
   * Wrapping the JSON deserializer in {@link ErrorHandlingDeserializer} is what makes a poison pill recoverable: the
   * failure is handed to the listener container instead of being thrown inside {@code Consumer.poll()}, where no error
   * handler can see it and the offset never advances.
   */
  private <T> ConsumerFactory<String, T> consumerFactory(Class<T> eventType) {
    var jsonDeserializer = new JacksonJsonDeserializer<T>(eventType, kafkaJsonMapper(), false);
    var valueDeserializer = new ErrorHandlingDeserializer<>(jsonDeserializer);

    Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties());
    config.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), valueDeserializer);
  }

  /**
   * Since an exhausted record is dropped rather than parked anywhere, these are the only record of it - hence ERROR
   * rather than WARN once the retries are used up.
   */
  RetryListener loggingRetryListener() {
    return new RetryListener() {
      @Override
      public void failedDelivery(ConsumerRecord<?, ?> consumerRecord, Exception exception, int deliveryAttempt) {
        log.warn("Kafka record delivery failed [topic: {}, partition: {}, offset: {}, attempt: {}]: {}",
          consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset(), deliveryAttempt,
          exception.getMessage());
      }

      @Override
      public void recovered(ConsumerRecord<?, ?> consumerRecord, Exception exception) {
        log.error("Retries exhausted, dropping kafka record [topic: {}, partition: {}, offset: {}]",
          consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset(), exception);
      }

      @Override
      public void recoveryFailed(ConsumerRecord<?, ?> consumerRecord, Exception exception, Exception failure) {
        log.error("Failed to drop kafka record after exhausting retries [topic: {}, partition: {}, offset: {}]",
          consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset(), failure);
      }
    };
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
