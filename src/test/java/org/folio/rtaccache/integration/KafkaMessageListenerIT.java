package org.folio.rtaccache.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.folio.rtaccache.TestConstant.TEST_TENANT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestUtil;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.ResourceEvent;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.domain.dto.RtacHoldingLocation;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.spring.DefaultFolioExecutionContext;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Log4j2
class KafkaMessageListenerIT extends BaseIntegrationTest {

  private static final String HOLDINGS_TOPIC = "test.ALL.inventory.holdings-record";
  private static final String ITEM_TOPIC = "test.ALL.inventory.item";

  private static final String HOLDINGS_ID = "55fa3746-8176-49c5-9809-b29dd7bb9b47";
  private static final String ITEM_ID = "522d41d3-0e04-416d-9f52-90ac67685a78";
  private static final String PIECE_ID = "d892d70b-96be-4e5b-ab11-05839eb5df40";
  private static final String INSTANCE_ID = "843b368d-411c-4dce-bd64-99afc53f508d";

  private static final String CREATE_HOLDINGS_EVENT_PATH = "__files/kafka-events/create-holdings-event.json";
  private static final String DELETE_HOLDINGS_EVENT_PATH = "__files/kafka-events/delete-holdings-event.json";
  private static final String UPDATE_HOLDINGS_EVENT_PATH = "__files/kafka-events/update-holdings-event.json";
  private static final String CREATE_ITEM_EVENT_PATH = "__files/kafka-events/create-item-event.json";
  private static final String DELETE_ITEM_EVENT_PATH = "__files/kafka-events/delete-item-event.json";
  private static final String UPDATE_ITEM_EVENT_PATH = "__files/kafka-events/update-item-event.json";

  private static final String OLD_CALL_NUMBER = "OLD-CALL-123";
  private static final String NEW_CALL_NUMBER = "NEW-CALL-456";
  private static final String OLD_LOCATION_ID = "1c54d084-4639-45dd-b9c9-4473df6bd28a";
  private static final String NEW_LOCATION_ID = "2d65e095-5750-56ee-ca60-5584eg7ce39b";
  private static final String NEW_HOLDINGS_COPY_NUMBER = "Test";
  private static final String OLD_HOLDINGS_COPY_NUMBER = "Old copy number";
  private static final String NEW_NOTE_VALUE = "Test";
  private static final String NEW_STATEMENT = "Test";
  private static final String NEW_STATUS = "Checked out";
  private static final String OLD_STATUS = "Available";
  private static final String NEW_MATERIAL_TYPE_NAME = "book";
  private static final String NEW_BARCODE = "1232323232";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final long ZERO_COUNT = 0L;


  @Autowired
  private KafkaTemplate<String, ResourceEvent> kafkaTemplate;
  @Autowired
  private RtacHoldingRepository holdingRepository;
  @Autowired
  private FolioModuleMetadata folioModuleMetadata;


  @BeforeEach
  void setUp() {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      holdingRepository.deleteAll();
    } catch (Exception e) {
      log.warn("Failed to clean up rtac_holdings table: {}", e.getMessage());
    }
  }

  @Test
  void shouldCreateRtacHolding_withHoldingType_whenHoldingCreateEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      ResourceEvent event = loadResourceEvent(CREATE_HOLDINGS_EVENT_PATH);

      // When
      sendHoldingsKafkaMessage(event, HOLDINGS_ID);

      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(HOLDINGS_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getId()).isEqualTo(HOLDINGS_ID);
        assertThat(holding.get().getRtacHolding().getType()).isEqualTo(TypeEnum.HOLDING);
        assertThat(holding.get().getRtacHolding().getStatus()).isEqualTo(NEW_STATEMENT);
        assertThat(holding.get().getRtacHolding().getCallNumber()).isEqualTo(NEW_CALL_NUMBER);
        assertThat(holding.get().getRtacHolding().getLocation().getId()).isEqualTo(NEW_LOCATION_ID);
        assertThat(holding.get().getRtacHolding().getHoldingsStatements().getFirst().getStatement()).isEqualTo(NEW_STATEMENT);
        assertThat(holding.get().getRtacHolding().getNotes().getFirst().getNote()).isEqualTo(NEW_NOTE_VALUE);
        assertThat(holding.get().getRtacHolding().getHoldingsCopyNumber()).isEqualTo(NEW_HOLDINGS_COPY_NUMBER);
      });
    }
  }

  @Test
  void shouldUpdateRtacHolding_withHoldingType_whenHoldingsUpdateEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHolding(HOLDINGS_ID, TypeEnum.HOLDING);
      ResourceEvent event = loadResourceEvent(UPDATE_HOLDINGS_EVENT_PATH);
      // When
      sendHoldingsKafkaMessage(event, HOLDINGS_ID);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(HOLDINGS_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getStatus()).isEqualTo(NEW_STATEMENT);
        assertThat(holding.get().getRtacHolding().getCallNumber()).isEqualTo(NEW_CALL_NUMBER);
        assertThat(holding.get().getRtacHolding().getLocation().getId()).isEqualTo(NEW_LOCATION_ID);
        assertThat(holding.get().getRtacHolding().getHoldingsStatements().getFirst().getStatement()).isEqualTo(
          NEW_STATEMENT);
        assertThat(holding.get().getRtacHolding().getNotes().getFirst().getNote()).isEqualTo(NEW_NOTE_VALUE);
        assertThat(holding.get().getRtacHolding().getHoldingsCopyNumber()).isEqualTo(NEW_HOLDINGS_COPY_NUMBER);
      });
    }
  }

  @Test
  void shouldUpdateRtacHolding_withItemType_whenHoldingsUpdateEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHolding(ITEM_ID, TypeEnum.ITEM);
      ResourceEvent event = loadResourceEvent(UPDATE_HOLDINGS_EVENT_PATH);
      // When
      sendHoldingsKafkaMessage(event, HOLDINGS_ID);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(ITEM_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getCallNumber()).isEqualTo(OLD_CALL_NUMBER);
        assertThat(holding.get().getRtacHolding().getLocation().getId()).isEqualTo(OLD_LOCATION_ID);
        assertThat(holding.get().getRtacHolding().getHoldingsStatements().getFirst().getStatement()).isEqualTo(
          NEW_STATEMENT);
        assertThat(holding.get().getRtacHolding().getNotes().getFirst().getNote()).isEqualTo(NEW_NOTE_VALUE);
        assertThat(holding.get().getRtacHolding().getHoldingsCopyNumber()).isEqualTo(NEW_HOLDINGS_COPY_NUMBER);
      });
    }
  }

  @Test
  void shouldUpdateRtacHolding_withPieceType_whenHoldingsUpdateEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHolding(PIECE_ID, TypeEnum.PIECE);
      ResourceEvent event = loadResourceEvent(UPDATE_HOLDINGS_EVENT_PATH);
      // When
      sendHoldingsKafkaMessage(event, HOLDINGS_ID);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(PIECE_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getHoldingsCopyNumber()).isEqualTo(OLD_HOLDINGS_COPY_NUMBER);
        assertThat(holding.get().getRtacHolding().getStatus()).isEqualTo(OLD_STATUS);
        assertThat(holding.get().getRtacHolding().getCallNumber()).isEqualTo(NEW_CALL_NUMBER);
        assertThat(holding.get().getRtacHolding().getLocation().getId()).isEqualTo(NEW_LOCATION_ID);
        assertThat(holding.get().getRtacHolding().getHoldingsStatements().getFirst().getStatement()).isEqualTo(
          NEW_STATEMENT);
        assertThat(holding.get().getRtacHolding().getNotes().getFirst().getNote()).isEqualTo(NEW_NOTE_VALUE);
      });
    }
  }

  @Test
  void shouldDeleteRtacHolding_whenHoldingsDeleteEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHolding(HOLDINGS_ID, TypeEnum.HOLDING);
      createExistingRtacHolding(ITEM_ID, TypeEnum.ITEM);
      ResourceEvent event = loadResourceEvent(DELETE_HOLDINGS_EVENT_PATH);
      // When
      sendHoldingsKafkaMessage(event, HOLDINGS_ID);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var count = holdingRepository.count();
        assertThat(ZERO_COUNT).isEqualTo(count);
      });
    }
  }

  @Test
  void shouldCreateRtacHolding_withItemType_whenItemCreateEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHolding(HOLDINGS_ID, TypeEnum.HOLDING);
      ResourceEvent event = loadResourceEvent(CREATE_ITEM_EVENT_PATH);
      // When
      sendItemKafkaMessage(event, ITEM_ID);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(ITEM_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getCallNumber()).isEqualTo(NEW_CALL_NUMBER);
        assertThat(holding.get().getRtacHolding().getLocation().getId()).isEqualTo(NEW_LOCATION_ID);
        assertThat(holding.get().getRtacHolding().getMaterialType().getName()).isEqualTo(NEW_MATERIAL_TYPE_NAME);
        assertThat(holding.get().getRtacHolding().getBarcode()).isEqualTo(NEW_BARCODE);
        assertThat(holding.get().getRtacHolding().getStatus()).isEqualTo(NEW_STATUS);
      });
    }
  }

  @Test
  void shouldUpdateRtacHolding_withItemType_whenItemUpdateEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHolding(ITEM_ID, TypeEnum.ITEM);
      ResourceEvent event = loadResourceEvent(UPDATE_ITEM_EVENT_PATH);
      // When
      sendItemKafkaMessage(event, ITEM_ID);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(ITEM_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getCallNumber()).isEqualTo(NEW_CALL_NUMBER);
        assertThat(holding.get().getRtacHolding().getLocation().getId()).isEqualTo(NEW_LOCATION_ID);
        assertThat(holding.get().getRtacHolding().getMaterialType().getName()).isEqualTo(NEW_MATERIAL_TYPE_NAME);
        assertThat(holding.get().getRtacHolding().getBarcode()).isEqualTo(NEW_BARCODE);
        assertThat(holding.get().getRtacHolding().getStatus()).isEqualTo(NEW_STATUS);
      });
    }
  }

  @Test
  void shouldDeleteRtacHolding_withItemType_whenItemDeleteEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHolding(ITEM_ID, TypeEnum.ITEM);
      ResourceEvent event = loadResourceEvent(DELETE_ITEM_EVENT_PATH);
      // When
      sendItemKafkaMessage(event, ITEM_ID);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var count = holdingRepository.count();
        assertThat(ZERO_COUNT).isEqualTo(count);
      });
    }
  }

  private void createExistingRtacHolding(String id, TypeEnum type) {
    RtacHoldingEntity entity = new RtacHoldingEntity();

    RtacHoldingId rtacHoldingId = new RtacHoldingId();
    rtacHoldingId.setId(UUID.fromString(id));
    rtacHoldingId.setInstanceId(UUID.fromString(INSTANCE_ID));
    rtacHoldingId.setType(type);
    entity.setId(rtacHoldingId);

    var rtacHolding = new RtacHolding();
    rtacHolding.setCallNumber(KafkaMessageListenerIT.OLD_CALL_NUMBER);
    rtacHolding.setId(id);
    rtacHolding.setInstanceId(INSTANCE_ID);
    rtacHolding.setHoldingsId(KafkaMessageListenerIT.HOLDINGS_ID);
    rtacHolding.setType(type);
    rtacHolding.setStatus(OLD_STATUS);
    rtacHolding.setHoldingsCopyNumber(OLD_HOLDINGS_COPY_NUMBER);

    var location = new RtacHoldingLocation();
    location.setId(KafkaMessageListenerIT.OLD_LOCATION_ID);
    rtacHolding.setLocation(location);

    entity.setRtacHolding(rtacHolding);
    entity.setCreatedAt(Instant.now());
    holdingRepository.save(entity);
  }

  private ResourceEvent loadResourceEvent(String path) throws JsonProcessingException {
    var content = TestUtil.readFileContentFromResources(path);
    return OBJECT_MAPPER.readValue(content, ResourceEvent.class);

  }

  private void sendHoldingsKafkaMessage(ResourceEvent event, String id) {
    ProducerRecord<String, ResourceEvent> record = new ProducerRecord<>(HOLDINGS_TOPIC, id, event);
    kafkaTemplate.send(record);
  }

  private void sendItemKafkaMessage(ResourceEvent event, String id) {
    ProducerRecord<String, ResourceEvent> record = new ProducerRecord<>(ITEM_TOPIC, id, event);
    kafkaTemplate.send(record);
  }

  private FolioExecutionContext folioExecutionContext() {
    var headersMap = (Map<String, Collection<String>>) (Map) Map.of(
      XOkapiHeaders.TENANT, Lists.newArrayList(TEST_TENANT),
      XOkapiHeaders.URL, Lists.newArrayList(WIRE_MOCK.baseUrl()),
      XOkapiHeaders.TOKEN, Lists.newArrayList(TOKEN)
    );
    return new DefaultFolioExecutionContext(folioModuleMetadata,  headersMap);
  }
}
