package org.folio.rtaccache.config;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.spring.tools.kafka.FolioKafkaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;

/**
 * {@code recoveryFailed} is only invoked by Spring Kafka's {@code DefaultErrorHandler} when the recoverer itself
 * throws while giving up on an exhausted record - a path {@link org.folio.rtaccache.integration.KafkaMessageListenerIT}
 * cannot trigger through a real broker, so it is exercised directly here instead.
 */
@ExtendWith(MockitoExtension.class)
class KafkaConfigurationTest {

  @Mock
  private KafkaProperties kafkaProperties;
  @Mock
  private FolioKafkaProperties folioKafkaProperties;

  private KafkaConfiguration kafkaConfiguration;

  @BeforeEach
  void setUp() {
    kafkaConfiguration = new KafkaConfiguration(kafkaProperties, folioKafkaProperties);
  }

  @Test
  void loggingRetryListener_failedDelivery_shouldLogWithoutThrowing() {
    var retryListener = kafkaConfiguration.loggingRetryListener();
    var consumerRecord = new ConsumerRecord<>("topic", 0, 0L, "key", "value");

    assertThatCode(() -> retryListener.failedDelivery(consumerRecord, new RuntimeException("transient"), 1))
      .doesNotThrowAnyException();
  }

  @Test
  void loggingRetryListener_recovered_shouldLogWithoutThrowing() {
    var retryListener = kafkaConfiguration.loggingRetryListener();
    var consumerRecord = new ConsumerRecord<>("topic", 0, 0L, "key", "value");

    assertThatCode(() -> retryListener.recovered(consumerRecord, new RuntimeException("exhausted")))
      .doesNotThrowAnyException();
  }

  @Test
  void loggingRetryListener_recoveryFailed_shouldLogWithoutThrowing() {
    var retryListener = kafkaConfiguration.loggingRetryListener();
    var consumerRecord = new ConsumerRecord<>("topic", 0, 0L, "key", "value");

    assertThatCode(() -> retryListener.recoveryFailed(consumerRecord, new RuntimeException("exhausted"),
      new RuntimeException("recovery failed")))
      .doesNotThrowAnyException();
  }

}
