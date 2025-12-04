package org.folio.rtaccache.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
@Profile("!test|test-kafka")
public class RtacKafkaAdminService implements ApplicationRunner {

  private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
  private final ApplicationContext applicationContext;

  /**
   * Restarts kafka event listeners in mod-rtac-cache application.
   */
  public void restartEventListeners() {
    log.info("Restarting kafka consumer to update topic pattern");
    kafkaListenerEndpointRegistry.getAllListenerContainers().forEach(existingContainer -> {
      if (existingContainer != null) {
        log.info("Restarting kafka consumer to update topic pattern [ids: {}]", existingContainer.getListenerId());
        existingContainer.stop();
        existingContainer.destroy();
        kafkaListenerEndpointRegistry.unregisterListenerContainer(existingContainer.getListenerId());
      }
    });
    applicationContext.getBean(KafkaMessageListener.class);
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    restartEventListeners();
  }
}
