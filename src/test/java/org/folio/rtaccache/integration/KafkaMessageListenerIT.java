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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestUtil;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.CirculationResourceEvent;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.PieceResourceEvent;
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
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

@TestMethodOrder(OrderAnnotation.class)
@Log4j2
class KafkaMessageListenerIT extends BaseIntegrationTest {

  private static final String HOLDINGS_TOPIC = "test.ALL.inventory.holdings-record";
  private static final String ITEM_TOPIC = "test.ALL.inventory.item";
  private static final String LOAN_TOPIC = "test.ALL.circulation.loan";
  private static final String REQUEST_TOPIC = "test.ALL.circulation.request";
  private static final String PIECE_TOPIC = "test.Default.ALL.ACQ_PIECE_CHANGED";

  private static final String HOLDINGS_ID_1 = "55fa3746-8176-49c5-9809-b29dd7bb9b47";
  private static final String HOLDINGS_ID_2 = "48525495-05b0-488e-a0c5-0f3ec5c7a0f2";
  private static final String ITEM_ID = "522d41d3-0e04-416d-9f52-90ac67685a78";
  private static final String PIECE_ID = "d892d70b-96be-4e5b-ab11-05839eb5df40";
  private static final String INSTANCE_ID = "843b368d-411c-4dce-bd64-99afc53f508d";

  private static final String CREATE_HOLDINGS_EVENT_PATH = "__files/kafka-events/create-holdings-event.json";
  private static final String DELETE_HOLDINGS_EVENT_PATH = "__files/kafka-events/delete-holdings-event.json";
  private static final String UPDATE_HOLDINGS_EVENT_PATH = "__files/kafka-events/update-holdings-event.json";
  private static final String CREATE_ITEM_EVENT_PATH = "__files/kafka-events/create-item-event.json";
  private static final String DELETE_ITEM_EVENT_PATH = "__files/kafka-events/delete-item-event.json";
  private static final String UPDATE_ITEM_EVENT_PATH = "__files/kafka-events/update-item-event.json";
  private static final String CREATE_LOAN_EVENT_PATH = "__files/kafka-events/create-loan-event.json";
  private static final String UPDATE_LOAN_EVENT_PATH = "__files/kafka-events/update-loan-event.json";
  private static final String CREATE_REQUEST_EVENT_PATH = "__files/kafka-events/create-request-event.json";
  private static final String UPDATE_REQUEST_EVENT_PATH = "__files/kafka-events/update-request-event.json";
  private static final String CREATE_PIECE_EVENT_PATH = "__files/kafka-events/create-piece-event.json";
  private static final String DELETE_PIECE_EVENT_PATH = "__files/kafka-events/delete-piece-event.json";
  private static final String UPDATE_PIECE_EVENT_PATH = "__files/kafka-events/update-piece-event.json";

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
  private static final String NEW_VOLUME = "(Test)";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired
  private KafkaTemplate<String, InventoryResourceEvent> inventoryKafkaTemplate;
  @Autowired
  private KafkaTemplate<String, CirculationResourceEvent> circualationKafkaTemplate;
  @Autowired
  private KafkaTemplate<String, PieceResourceEvent> pieceKafkaTemplate;
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
  @Order(1)
  void shouldCreateRtacHolding_withHoldingType_whenHoldingCreateEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      var event = loadInventoryResourceEvent(CREATE_HOLDINGS_EVENT_PATH);
      createExistingRtacHoldingEntity(HOLDINGS_ID_2, TypeEnum.HOLDING);

      // When
      sendHoldingsKafkaMessage(event, HOLDINGS_ID_1);

      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(HOLDINGS_ID_1));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getId()).isEqualTo(HOLDINGS_ID_1);
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
  @Order(2)
  void shouldUpdateRtacHolding_withHoldingType_whenHoldingsUpdateEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHoldingEntity(HOLDINGS_ID_1, TypeEnum.HOLDING);
      var event = loadInventoryResourceEvent(UPDATE_HOLDINGS_EVENT_PATH);
      // When
      sendHoldingsKafkaMessage(event, HOLDINGS_ID_1);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(HOLDINGS_ID_1));
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
  @Order(3)
  void shouldUpdateRtacHolding_withItemType_whenHoldingsUpdateEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM);
      var event = loadInventoryResourceEvent(UPDATE_HOLDINGS_EVENT_PATH);
      // When
      sendHoldingsKafkaMessage(event, HOLDINGS_ID_1);
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
  @Order(4)
  void shouldUpdateRtacHolding_withPieceType_whenHoldingsUpdateEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHoldingEntity(PIECE_ID, TypeEnum.PIECE);
      var event = loadInventoryResourceEvent(UPDATE_HOLDINGS_EVENT_PATH);
      // When
      sendHoldingsKafkaMessage(event, HOLDINGS_ID_1);
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
  @Order(5)
  void shouldDeleteRtacHolding_whenHoldingsDeleteEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHoldingEntity(HOLDINGS_ID_1, TypeEnum.HOLDING);
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM);
      var event = loadInventoryResourceEvent(DELETE_HOLDINGS_EVENT_PATH);
      // When
      sendHoldingsKafkaMessage(event, HOLDINGS_ID_1);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var count = holdingRepository.count();
        assertThat(count).isZero();
      });
    }
  }

  @Test
  @Order(6)
  void shouldCreateRtacHolding_withItemType_whenItemCreateEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHoldingEntity(HOLDINGS_ID_1, TypeEnum.HOLDING);
      var event = loadInventoryResourceEvent(CREATE_ITEM_EVENT_PATH);
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
  @Order(7)
  void shouldUpdateRtacHolding_withItemType_whenItemUpdateEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM);
      var event = loadInventoryResourceEvent(UPDATE_ITEM_EVENT_PATH);
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
  @Order(8)
  void shouldDeleteRtacHolding_withItemType_whenItemDeleteEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM);
      var event = loadInventoryResourceEvent(DELETE_ITEM_EVENT_PATH);
      // When
      sendItemKafkaMessage(event, ITEM_ID);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var count = holdingRepository.count();
        assertThat(count).isZero();
      });
    }
  }

  @Test
  @Order(9)
  void shouldUpdateRtacHoldingDueDate_withItemType_whenLoanCreateEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM);
      var event = loadCirculationResourceEvent(CREATE_LOAN_EVENT_PATH);
      // When
      sendLoanKafkaMessage(event, ITEM_ID);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(ITEM_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getDueDate()).isEqualTo("2026-01-12T23:59:59.000+00:00");
      });
    }
  }

  @Test
  @Order(10)
  void shouldUpdateRtacHoldingDueDate_withItemType_whenLoanUpdateEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM);
      var event = loadCirculationResourceEvent(UPDATE_LOAN_EVENT_PATH);
      // When
      sendLoanKafkaMessage(event, ITEM_ID);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(ITEM_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getDueDate()).isEqualTo("2026-01-19T23:59:00.000+00:00");
      });
    }
  }

  @Test
  @Order(11)
  void shouldUpdateRtacHoldingRequestCount_withItemType_whenOpenRequestCreateEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM);
      var event = loadCirculationResourceEvent(CREATE_REQUEST_EVENT_PATH);
      // When
      sendRequestKafkaMessage(event, ITEM_ID);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(ITEM_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getTotalHoldRequests()).isEqualTo(2);
      });
    }
  }

  @Test
  @Order(12)
  void shouldDecreaseRtacHoldingRequestCount_withItemType_whenClosedRequestUpdateEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM);
      var event = loadCirculationResourceEvent(UPDATE_REQUEST_EVENT_PATH);
      // When
      sendRequestKafkaMessage(event, ITEM_ID);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(ITEM_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getTotalHoldRequests()).isZero();
      });
    }
  }

  @Test
  @Order(13)
  void shouldCreateRtacHolding_withPieceType_whenPieceCreateEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHoldingEntity(HOLDINGS_ID_1, TypeEnum.HOLDING);
      var event = loadPieceResourceEvent(CREATE_PIECE_EVENT_PATH);
      // When
      sendPieceKafkaMessage(event, PIECE_ID);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(PIECE_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getStatus()).isEqualTo("Expected");
      });
    }
  }

  @Test
  @Order(14)
  void shouldUpdateRtacHolding_withPieceType_whenPieceUpdateEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHoldingEntity(PIECE_ID, TypeEnum.PIECE);
      var event = loadPieceResourceEvent(UPDATE_PIECE_EVENT_PATH);
      // When
      sendPieceKafkaMessage(event, PIECE_ID);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(PIECE_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getVolume()).isEqualTo(NEW_VOLUME);
      });
    }
  }

  @Test
  @Order(15)
  void shouldDeleteRtacHolding_withPieceType_whenPieceDeleteEventIsSent() throws JsonProcessingException {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext())) {
      // Given
      createExistingRtacHoldingEntity(PIECE_ID, TypeEnum.PIECE);
      var event = loadPieceResourceEvent(DELETE_PIECE_EVENT_PATH);
      // When
      sendPieceKafkaMessage(event, PIECE_ID);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var count = holdingRepository.count();
        assertThat(count).isZero();
      });
    }
  }

  private void createExistingRtacHoldingEntity(String id, TypeEnum type) {
    RtacHoldingEntity entity = new RtacHoldingEntity();

    RtacHoldingId rtacHoldingId = new RtacHoldingId();
    rtacHoldingId.setId(UUID.fromString(id));
    rtacHoldingId.setInstanceId(UUID.fromString(INSTANCE_ID));
    rtacHoldingId.setType(type);
    entity.setId(rtacHoldingId);

    var rtacHolding = createRtacHolding(id, type);

    entity.setRtacHolding(rtacHolding);
    entity.setCreatedAt(Instant.now());
    holdingRepository.save(entity);
  }

  private RtacHolding createRtacHolding(String id, TypeEnum type) {
    var rtacHolding = new RtacHolding();
    rtacHolding.setCallNumber(OLD_CALL_NUMBER);
    rtacHolding.setId(id);
    rtacHolding.setInstanceId(INSTANCE_ID);
    rtacHolding.setHoldingsId(HOLDINGS_ID_1);
    rtacHolding.setType(type);
    rtacHolding.setStatus(OLD_STATUS);
    rtacHolding.setHoldingsCopyNumber(OLD_HOLDINGS_COPY_NUMBER);
    rtacHolding.setTotalHoldRequests(1);

    var location = new RtacHoldingLocation();
    location.setId(KafkaMessageListenerIT.OLD_LOCATION_ID);
    rtacHolding.setLocation(location);
    return rtacHolding;
  }

  private InventoryResourceEvent loadInventoryResourceEvent(String path) throws JsonProcessingException {
    var content = TestUtil.readFileContentFromResources(path);
    return OBJECT_MAPPER.readValue(content, InventoryResourceEvent.class);
  }

  private CirculationResourceEvent loadCirculationResourceEvent(String path) throws JsonProcessingException {
    var content = TestUtil.readFileContentFromResources(path);
    return OBJECT_MAPPER.readValue(content, CirculationResourceEvent.class);
  }

  private PieceResourceEvent loadPieceResourceEvent(String path) throws JsonProcessingException {
    var content = TestUtil.readFileContentFromResources(path);
    return OBJECT_MAPPER.readValue(content, PieceResourceEvent.class);
  }

  private void sendHoldingsKafkaMessage(InventoryResourceEvent event, String id) {
    ProducerRecord<String, InventoryResourceEvent> holdingsRecord = new ProducerRecord<>(HOLDINGS_TOPIC, id, event);
    inventoryKafkaTemplate.send(holdingsRecord);
  }

  private void sendItemKafkaMessage(InventoryResourceEvent event, String id) {
    ProducerRecord<String, InventoryResourceEvent> itemRecord = new ProducerRecord<>(ITEM_TOPIC, id, event);
    inventoryKafkaTemplate.send(itemRecord);
  }

  private void sendLoanKafkaMessage(CirculationResourceEvent event, String id) {
    var loanRecord = new ProducerRecord<>(LOAN_TOPIC, id, event);
    circualationKafkaTemplate.send(loanRecord);
  }

  private void sendRequestKafkaMessage(CirculationResourceEvent event, String id) {
    var requestRecord = new ProducerRecord<>(REQUEST_TOPIC, id, event);
    circualationKafkaTemplate.send(requestRecord);
  }

  private void sendPieceKafkaMessage(PieceResourceEvent event, String id) {
    var header = new RecordHeader("folio.tenantId", TEST_TENANT.getBytes());
    var pieceRecord = new ProducerRecord<>(PIECE_TOPIC, 0, id, event, List.of(header));
    pieceKafkaTemplate.send(pieceRecord);
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
