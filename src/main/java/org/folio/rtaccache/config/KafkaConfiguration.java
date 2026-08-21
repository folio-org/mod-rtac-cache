package org.folio.rtaccache.config;

import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.folio.rtaccache.domain.dto.CirculationResourceEvent;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.PieceResourceEvent;
import org.folio.rtaccache.integration.KafkaMessageListener;
import org.folio.rtaccache.service.ConsortiaService;
import org.folio.rtaccache.service.handler.EventHandlerFactory;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.spring.tools.kafka.FolioKafkaProperties;
import org.folio.spring.tools.kafka.FolioKafkaProperties.KafkaListenerProperties;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.JacksonMapperUtils;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.databind.json.JsonMapper;

/**
 * Responsible for configuration of kafka consumer bean factories at application startup for kafka listeners.
 */
@Log4j2
@Configuration
@RequiredArgsConstructor
public class KafkaConfiguration {

  /**
   * Suffix appended to a source topic name to derive its dead-letter topic.
   */
  public static final String DLT_TOPIC_SUFFIX = ".DLT";

  /**
   * Any partition - the recoverer must not copy the source partition number onto the dead-letter topic, because
   * the DLT partition count is not guaranteed to match the source topic's.
   */
  private static final int DLT_ANY_PARTITION = -1;

  private static final double RETRY_BACKOFF_MULTIPLIER = 2.0;

  private final KafkaProperties kafkaProperties;
  private final FolioKafkaProperties folioKafkaProperties;

  /**
   * Shared by the consumer deserializers and the dead-letter producer so that a record is written to the DLT with
   * the same JSON semantics it was read with.
   */
  private final JsonMapper kafkaJsonMapper = JacksonMapperUtils.enhancedJsonMapper();

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
   * Retries a failed record with a growing delay and, once the attempts are exhausted, republishes it to the source
   * topic's {@value #DLT_TOPIC_SUFFIX} topic so that the consumer can move past it.
   *
   * <p>Deserialization failures need no special handling here: {@code ErrorHandlingDeserializer} defers them to the
   * listener container, and {@code DeserializationException} is part of
   * {@code ExceptionClassifier.defaultFatalExceptionsList()}, so such records are sent to the DLT on the first
   * attempt instead of being retried.</p>
   */
  @Bean
  public DefaultErrorHandler kafkaErrorHandler() {
    var backOff = new ExponentialBackOff(folioKafkaProperties.getRetryIntervalMs(), RETRY_BACKOFF_MULTIPLIER);
    backOff.setMaxAttempts(folioKafkaProperties.getRetryDeliveryAttempts());

    var errorHandler = new DefaultErrorHandler(deadLetterPublishingRecoverer(), backOff);
    // Nothing about a malformed payload or a programming error improves by being retried.
    errorHandler.addNotRetryableExceptions(NullPointerException.class, IllegalArgumentException.class);
    errorHandler.setRetryListeners(loggingRetryListener());
    return errorHandler;
  }

  /**
   * Declares the dead-letter topics up front, because FOLIO clusters usually run with
   * {@code auto.create.topics.enable=false}, and a missing DLT would make the recoverer's send fail and put the
   * container straight back into a retry loop.
   */
  @Bean
  public KafkaAdmin.NewTopics deadLetterTopics() {
    var topics = folioKafkaProperties.getListener().values().stream()
      .map(KafkaListenerProperties::getTopicPattern)
      .filter(topicPattern -> topicPattern != null && !topicPattern.isBlank())
      .filter(KafkaConfiguration::isLiteralTopicPattern)
      .map(KafkaConfiguration::toDeadLetterTopicName)
      .distinct()
      .map(topicName -> TopicBuilder.name(topicName).build())
      .toArray(NewTopic[]::new);

    log.info("Declaring {} kafka dead-letter topic(s)", topics.length);
    return new KafkaAdmin.NewTopics(topics);
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
   * failure is converted into a header on the record rather than being thrown inside {@code Consumer.poll()}, where
   * no error handler could see it and the offset would never advance.
   */
  private <T> ConsumerFactory<String, T> consumerFactory(Class<T> eventType) {
    var jsonDeserializer = new JacksonJsonDeserializer<T>(eventType, kafkaJsonMapper, false);
    var valueDeserializer = new ErrorHandlingDeserializer<>(jsonDeserializer);

    Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties());
    config.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), valueDeserializer);
  }

  private DeadLetterPublishingRecoverer deadLetterPublishingRecoverer() {
    return new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate(),
      (consumerRecord, exception) ->
        new TopicPartition(consumerRecord.topic() + DLT_TOPIC_SUFFIX, DLT_ANY_PARTITION));
  }

  /**
   * Deliberately not exposed as a bean: Boot's auto-configured {@code kafkaTemplate} is
   * {@code @ConditionalOnMissingBean}, so publishing a {@link KafkaTemplate} bean here would silently withdraw it
   * from the rest of the application.
   *
   * <p>The value serializer has to delegate by type, because {@link DeadLetterPublishingRecoverer} forwards the raw
   * {@code byte[]} for a deserialization failure but the already-deserialized event for a handler failure.</p>
   */
  private KafkaOperations<String, Object> deadLetterKafkaTemplate() {
    Map<String, Object> config = new HashMap<>(kafkaProperties.buildProducerProperties());
    // The serializer instances passed below win; leaving the configured classes in the map would only mislead.
    config.remove(KEY_SERIALIZER_CLASS_CONFIG);
    config.remove(VALUE_SERIALIZER_CLASS_CONFIG);

    Map<Class<?>, Serializer<?>> delegates = Map.of(
      byte[].class, new ByteArraySerializer(),
      Object.class, new JacksonJsonSerializer<>(kafkaJsonMapper));

    var producerFactory = new DefaultKafkaProducerFactory<String, Object>(config, new StringSerializer(),
      new DelegatingByTypeSerializer(delegates, true));
    return new KafkaTemplate<>(producerFactory);
  }

  private RetryListener loggingRetryListener() {
    return new RetryListener() {
      @Override
      public void failedDelivery(ConsumerRecord<?, ?> consumerRecord, Exception exception, int deliveryAttempt) {
        log.warn("Kafka record delivery failed [topic: {}, partition: {}, offset: {}, attempt: {}]: {}",
          consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset(), deliveryAttempt,
          exception.getMessage());
      }

      @Override
      public void recovered(ConsumerRecord<?, ?> consumerRecord, Exception exception) {
        log.error("Kafka record sent to dead-letter topic [topic: {}, partition: {}, offset: {}]",
          consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset(), exception);
      }

      @Override
      public void recoveryFailed(ConsumerRecord<?, ?> consumerRecord, Exception exception, Exception failure) {
        log.error("Failed to send kafka record to dead-letter topic [topic: {}, partition: {}, offset: {}]",
          consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset(), failure);
      }
    };
  }

  /**
   * A topic can only be pre-created when its pattern names exactly one topic. {@code KAFKA_EVENTS_CONSUMER_PATTERN}
   * lets an operator supply a genuine multi-topic regex, and turning something like
   * {@code folio\.(a|b)\.inventory\.item} into a topic name would just ask the broker to create a nonsense topic, so
   * those are left to {@code auto.create.topics.enable} instead.
   */
  private static boolean isLiteralTopicPattern(String topicPattern) {
    var withoutEscapedDots = topicPattern.replace("\\.", "");
    if (withoutEscapedDots.matches(".*[\\\\*+?\\[\\]()|^$].*")) {
      log.warn("Not pre-creating a dead-letter topic for non-literal topic pattern [{}]", topicPattern);
      return false;
    }
    return true;
  }

  /**
   * Listener topics are configured as regular expressions, so the escaping has to be undone to get the literal topic
   * name to create, e.g. {@code folio\.ALL\.inventory\.instance} to {@code folio.ALL.inventory.instance.DLT}.
   */
  private static String toDeadLetterTopicName(String topicPattern) {
    return topicPattern.replace("\\", "") + DLT_TOPIC_SUFFIX;
  }

}
