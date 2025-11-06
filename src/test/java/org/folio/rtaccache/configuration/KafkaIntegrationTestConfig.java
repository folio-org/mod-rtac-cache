package org.folio.rtaccache.configuration;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaIntegrationTestConfig {
  public static final String TEST_HOLDING_RECORD_TOPIC = "test.ALL.inventory.holdings-record";
  public static final String TEST_ITEM_RECORD_TOPIC = "test.ALL.inventory.item";

  @Bean
  public NewTopic holdingsTopic() {
    return TopicBuilder.name(TEST_HOLDING_RECORD_TOPIC)
        .build();
  }

  @Bean
  public NewTopic itemTopic() {
    return TopicBuilder.name(TEST_ITEM_RECORD_TOPIC)
      .build();
  }

}
