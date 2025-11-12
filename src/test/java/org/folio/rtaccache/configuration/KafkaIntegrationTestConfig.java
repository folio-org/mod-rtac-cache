package org.folio.rtaccache.configuration;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaIntegrationTestConfig {
  public static final String TEST_HOLDING_RECORD_TOPIC = "test.ALL.inventory.holdings-record";
  public static final String TEST_ITEM_TOPIC = "test.ALL.inventory.item";
  private static final String TEST_LOAN_TOPIC = "test.ALL.circulation.loan";
  private static final String TEST_REQUEST_TOPIC = "test.ALL.circulation.request";
  private static final String TEST_PIECE_TOPIC = "test.Default.ALL.ACQ_PIECE_CHANGED";

  @Bean
  public NewTopic holdingsTopic() {
    return TopicBuilder.name(TEST_HOLDING_RECORD_TOPIC)
        .build();
  }

  @Bean
  public NewTopic itemTopic() {
    return TopicBuilder.name(TEST_ITEM_TOPIC)
      .build();
  }

  @Bean
  public NewTopic loanTopic() {
    return TopicBuilder.name(TEST_LOAN_TOPIC)
      .build();
  }

  @Bean
  public NewTopic requestTopic() {
    return TopicBuilder.name(TEST_REQUEST_TOPIC)
      .build();
  }

  @Bean
  public NewTopic pieceTopic() {
    return TopicBuilder.name(TEST_PIECE_TOPIC)
      .build();
  }

}
