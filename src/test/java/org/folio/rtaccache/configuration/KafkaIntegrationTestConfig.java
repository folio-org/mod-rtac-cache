package org.folio.rtaccache.configuration;

import static org.folio.rtaccache.TestConstant.HOLDINGS_TOPIC;
import static org.folio.rtaccache.TestConstant.ITEM_TOPIC;
import static org.folio.rtaccache.TestConstant.LIBRARY_TOPIC;
import static org.folio.rtaccache.TestConstant.LOAN_TOPIC;
import static org.folio.rtaccache.TestConstant.LOCATION_TOPIC;
import static org.folio.rtaccache.TestConstant.PIECE_TOPIC;
import static org.folio.rtaccache.TestConstant.REQUEST_TOPIC;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@Profile("test-kafka")
public class KafkaIntegrationTestConfig {

  @Bean
  public NewTopic holdingsTopic() {
    return TopicBuilder.name(HOLDINGS_TOPIC)
        .build();
  }

  @Bean
  public NewTopic itemTopic() {
    return TopicBuilder.name(ITEM_TOPIC)
      .build();
  }

  @Bean
  public NewTopic loanTopic() {
    return TopicBuilder.name(LOAN_TOPIC)
      .build();
  }

  @Bean
  public NewTopic requestTopic() {
    return TopicBuilder.name(REQUEST_TOPIC)
      .build();
  }

  @Bean
  public NewTopic pieceTopic() {
    return TopicBuilder.name(PIECE_TOPIC)
      .build();
  }

  @Bean
  public NewTopic locationTopic() {
    return TopicBuilder.name(LOCATION_TOPIC)
      .build();
  }

  @Bean
  public NewTopic libraryTopic() {
    return TopicBuilder.name(LIBRARY_TOPIC)
      .build();
  }

}
