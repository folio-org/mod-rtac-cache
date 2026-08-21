package org.folio.rtaccache.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.folio.rtaccache.TestConstant.TEST_CENTRAL_TENANT;
import static org.folio.rtaccache.TestConstant.TEST_MEMBER_TENANT;
import static org.folio.rtaccache.TestConstant.TEST_TENANT;
import static org.folio.rtaccache.config.KafkaConfiguration.DLT_TOPIC_SUFFIX;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.TestUtil;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.CirculationResourceEvent;
import org.folio.rtaccache.domain.dto.InventoryEntityType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.PieceResourceEvent;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.domain.dto.RtacHoldingLibrary;
import org.folio.rtaccache.domain.dto.RtacHoldingLocation;
import org.folio.rtaccache.domain.dto.RtacHoldingMaterialType;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.InventoryReferenceDataService;
import org.folio.rtaccache.service.handler.EventHandlerFactory;
import org.folio.rtaccache.service.handler.InventoryEventHandler;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Log4j2
@TestMethodOrder(OrderAnnotation.class)
@ActiveProfiles({"test-kafka"})
class KafkaMessageListenerIT extends BaseIntegrationTest {

  private static final String HOLDINGS_ID_1 = "55fa3746-8176-49c5-9809-b29dd7bb9b47";
  private static final String HOLDINGS_ID_2 = "48525495-05b0-488e-a0c5-0f3ec5c7a0f2";
  private static final String ITEM_ID = "522d41d3-0e04-416d-9f52-90ac67685a78";
  private static final String PIECE_ID = "d892d70b-96be-4e5b-ab11-05839eb5df40";
  private static final String INSTANCE_ID_1 = "843b368d-411c-4dce-bd64-99afc53f508d";
  private static final String INSTANCE_ID_2 = "4b0559ef-6738-4d51-98fb-16db8cbf935e";

  private static final String UPDATE_INSTANCE_MEMBER_TENANT_EVENT_PATH = "__files/kafka-events/update-instance-member-tenant-event.json";
  private static final String UPDATE_INSTANCE_CENTRAL_TENANT_EVENT_PATH = "__files/kafka-events/update-instance-central-tenant-event.json";
  private static final String CREATE_HOLDINGS_EVENT_PATH = "__files/kafka-events/create-holdings-event.json";
  private static final String DELETE_HOLDINGS_EVENT_PATH = "__files/kafka-events/delete-holdings-event.json";
  private static final String UPDATE_HOLDINGS_EVENT_PATH = "__files/kafka-events/update-holdings-event.json";
  private static final String UPDATE_HOLDINGS_MOVE_TO_CACHED_INSTANCE_EVENT_PATH = "__files/kafka-events/update-holdings-move-to-cached-instance-event.json";
  private static final String CREATE_ITEM_EVENT_PATH = "__files/kafka-events/create-item-event.json";
  private static final String DELETE_ITEM_EVENT_PATH = "__files/kafka-events/delete-item-event.json";
  private static final String UPDATE_ITEM_EVENT_PATH = "__files/kafka-events/update-item-event.json";
  private static final String UPDATE_ITEM_MOVE_TO_ANOTHER_HOLDING_EVENT_PATH = "__files/kafka-events/update-item-move-to-another-holding-event.json";
  private static final String CREATE_LOAN_EVENT_PATH = "__files/kafka-events/create-loan-event.json";
  private static final String UPDATE_LOAN_EVENT_PATH = "__files/kafka-events/update-loan-event.json";
  private static final String CREATE_REQUEST_EVENT_PATH = "__files/kafka-events/create-request-event.json";
  private static final String UPDATE_REQUEST_EVENT_PATH = "__files/kafka-events/update-request-event.json";
  private static final String CREATE_PIECE_EVENT_PATH = "__files/kafka-events/create-piece-event.json";
  private static final String DELETE_PIECE_EVENT_PATH = "__files/kafka-events/delete-piece-event.json";
  private static final String UPDATE_PIECE_EVENT_PATH = "__files/kafka-events/update-piece-event.json";
  private static final String CREATE_LOCATION_EVENT_PATH = "__files/kafka-events/create-location-event.json";
  private static final String DELETE_LOCATION_EVENT_PATH = "__files/kafka-events/delete-location-event.json";
  private static final String UPDATE_LOCATION_EVENT_PATH = "__files/kafka-events/update-location-event.json";
  private static final String CREATE_LIBRARY_EVENT_PATH = "__files/kafka-events/create-library-event.json";
  private static final String DELETE_LIBRARY_EVENT_PATH = "__files/kafka-events/delete-library-event.json";
  private static final String UPDATE_LIBRARY_EVENT_PATH = "__files/kafka-events/update-library-event.json";
  private static final String CREATE_MATERIAL_TYPE_EVENT_PATH = "__files/kafka-events/create-material-type-event.json";
  private static final String UPDATE_MATERIAL_TYPE_EVENT_PATH = "__files/kafka-events/update-material-type-event.json";
  private static final String CREATE_LOAN_TYPE_EVENT_PATH = "__files/kafka-events/create-loan-type-event.json";
  private static final String UPDATE_LOAN_TYPE_EVENT_PATH = "__files/kafka-events/update-loan-type-event.json";
  private static final String CREATE_BOUND_WITH_EVENT_PATH = "__files/kafka-events/create-bound-with-event.json";
  private static final String DELETE_BOUND_WITH_EVENT_PATH = "__files/kafka-events/delete-bound-with-event.json";
  private static final String MALFORMED_ITEM_EVENT_PATH = "__files/kafka-events/malformed-item-event.json";
  private static final String MALFORMED_EVENT_ID_PLACEHOLDER = "7f2c0f0e-1c1d-4b7a-9a3c-2b6f1d0b9f11";

  /**
   * Kept small so the retry test stays well inside the awaitility budget: with a multiplier of 2 the three retries are
   * spaced ~400ms, ~800ms and ~1600ms apart.
   */
  private static final long RETRY_INTERVAL_MS = 400L;
  private static final long RETRY_DELIVERY_ATTEMPTS = 3L;

  /**
   * {@code ExponentialBackOff.setMaxAttempts(n)} permits n retries on top of the initial delivery, so the handler is
   * invoked n+1 times before the record is recovered to the dead-letter topic.
   */
  private static final int EXPECTED_DELIVERIES = (int) RETRY_DELIVERY_ATTEMPTS + 1;
  private static final Duration DLT_POLL_TIMEOUT = Duration.ofSeconds(60);

  private static final String OLD_CALL_NUMBER = "OLD-CALL-123";
  private static final String NEW_CALL_NUMBER = "NEW-CALL-456";
  private static final String OLD_LOCATION_ID = "1c54d084-4639-45dd-b9c9-4473df6bd28a";
  private static final String NEW_LOCATION_ID = "2d65e095-5750-56ee-ca60-5584eg7ce39b";
  private static final String LIBRARY_ID = "79f1cd00-09cc-4c9c-99c1-d8ad1b77d128";
  private static final String MATERIAL_TYPE_ID = "1a54b431-2e4f-452d-9cae-9cee66c9a892";
  private static final String NEW_HOLDINGS_COPY_NUMBER = "Test";
  private static final String OLD_HOLDINGS_COPY_NUMBER = "Old copy number";
  private static final String NEW_NOTE_VALUE = "Test";
  private static final String NEW_STATEMENT = "Test";
  private static final String NEW_STATUS = "Checked out";
  private static final String OLD_STATUS = "Available";
  private static final Date OLD_DUE_DATE = Date.from(Instant.parse("2026-03-18T16:28:32.811+00:00"));
  private static final String OLD_LOAN_TYPE_NAME = "Can circulate";
  private static final String NEW_LOAN_TYPE_NAME = "Can circulate updated";
  private static final String UNCHANGED_LOAN_TYPE_NAME = "In-library use";
  private static final String OLD_MATERIAL_TYPE_NAME = "book";
  private static final String UPDATED_MATERIAL_TYPE_NAME = "book updated";
  private static final String NEW_MATERIAL_TYPE_NAME = "book";
  private static final String NEW_BARCODE = "1232323232";
  private static final String NEW_VOLUME = "(Test)";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String KAFKA_IMAGE_VERSION = "apache/kafka-native:4.2.0";
  private static final KafkaContainer kafkaContainer = new KafkaContainer(
    DockerImageName.parse(KAFKA_IMAGE_VERSION));
  private static final String INSTANCE_FORMAT_ID = "549e3381-7d49-44f6-8232-37af1cb5ecf3";
  private static final String INSTANCE_FORMAT_ID_2 = "f95fe9e7-0475-4c5c-bb13-56af8d017f33";

  static {
    kafkaContainer.start();
  }

  @Autowired
  private KafkaTemplate<String, InventoryResourceEvent> inventoryKafkaTemplate;
  @Autowired
  private KafkaTemplate<String, CirculationResourceEvent> circualationKafkaTemplate;
  @Autowired
  private KafkaTemplate<String, PieceResourceEvent> pieceKafkaTemplate;
  @Autowired
  private RtacHoldingRepository holdingRepository;
  @Autowired
  private InventoryReferenceDataService inventoryReferenceDataService;
  @Autowired
  private CacheManager cacheManager;
  /**
   * Spied rather than mocked so every other test keeps using the real handlers; Spring resets the stubbing after each
   * test method.
   */
  @MockitoSpyBean
  private EventHandlerFactory eventHandlerFactory;

  @DynamicPropertySource
  static void dynamicProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    registry.add("folio.kafka.retry-interval-ms", () -> RETRY_INTERVAL_MS);
    registry.add("folio.kafka.retry-delivery-attempts", () -> RETRY_DELIVERY_ATTEMPTS);
  }

  @AfterEach
  void tearDown() {
    try (var ignored = new FolioExecutionContextSetter(folioExecutionContext(TEST_TENANT))) {
      holdingRepository.deleteAll();
    } catch (Exception e) {
      log.warn("Failed to clean up rtac_holdings table: {}", e.getMessage());
    }
  }

  @Test
  @Order(1)
  void shouldCreateRtacHolding_withHoldingType_whenHoldingCreateEventIsSent() throws JsonProcessingException {
    withinTenant(TEST_TENANT, () -> {
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
        assertThat(holding.get().getRtacHolding().getInstanceFormatIds().getFirst()).isEqualTo(INSTANCE_FORMAT_ID);
        assertThat(holding.get().getRtacHolding().getType()).isEqualTo(TypeEnum.HOLDING);
        assertThat(holding.get().getRtacHolding().getStatus()).isEqualTo(NEW_STATEMENT);
        assertThat(holding.get().getRtacHolding().getCallNumber()).isEqualTo(NEW_CALL_NUMBER);
        assertThat(holding.get().getRtacHolding().getLocation().getId()).isEqualTo(NEW_LOCATION_ID);
        assertThat(holding.get().getRtacHolding().getHoldingsStatements().getFirst().getStatement()).isEqualTo(
          NEW_STATEMENT);
        assertThat(holding.get().getRtacHolding().getNotes().getFirst().getNote()).isEqualTo(NEW_NOTE_VALUE);
        assertThat(holding.get().getRtacHolding().getHoldingsCopyNumber()).isEqualTo(NEW_HOLDINGS_COPY_NUMBER);
      });
    });
  }

  @Test
  @Order(2)
  void shouldUpdateRtacHolding_withHoldingType_whenHoldingsUpdateEventIsSent() throws JsonProcessingException {
    withinTenant(TEST_TENANT, () -> {
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
    });
  }

  @Test
  @Order(3)
  void shouldUpdateRtacHolding_withItemType_whenHoldingsUpdateEventIsSent() throws JsonProcessingException {
    withinTenant(TEST_TENANT, () -> {
      // Given
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM);
      var event = loadInventoryResourceEvent(UPDATE_HOLDINGS_EVENT_PATH);
      // When
      sendHoldingsKafkaMessage(event, HOLDINGS_ID_1);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(ITEM_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getHoldingsCopyNumber()).isEqualTo(NEW_HOLDINGS_COPY_NUMBER);
      });
    });
  }

  @Test
  @Order(4)
  void shouldUpdateRtacHolding_withPieceType_whenHoldingsUpdateEventIsSent() throws JsonProcessingException {
    withinTenant(TEST_TENANT, () -> {
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
      });
    });
  }

  @Test
  @Order(6)
  void shouldDeleteRtacHolding_whenHoldingsDeleteEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
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
    });
  }

  @Test
  @Order(7)
  void shouldCreateRtacHolding_withItemType_whenItemCreateEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      createExistingRtacHoldingEntity(HOLDINGS_ID_1, TypeEnum.HOLDING);
      var event = loadInventoryResourceEvent(CREATE_ITEM_EVENT_PATH);
      // When
      sendItemKafkaMessage(event, ITEM_ID);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(ITEM_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getInstanceFormatIds().getFirst()).isEqualTo(INSTANCE_FORMAT_ID);
        assertThat(holding.get().getRtacHolding().getCallNumber()).isEqualTo(NEW_CALL_NUMBER);
        assertThat(holding.get().getRtacHolding().getLocation().getId()).isEqualTo(NEW_LOCATION_ID);
        assertThat(holding.get().getRtacHolding().getMaterialType().getName()).isEqualTo(NEW_MATERIAL_TYPE_NAME);
        assertThat(holding.get().getRtacHolding().getBarcode()).isEqualTo(NEW_BARCODE);
        assertThat(holding.get().getRtacHolding().getStatus()).isEqualTo(NEW_STATUS);
      });
    });
  }

  @Test
  @Order(8)
  void shouldUpdateRtacHolding_withItemType_whenItemUpdateEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM);
      createExistingRtacHoldingEntity(HOLDINGS_ID_1, TypeEnum.HOLDING);
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
    });
  }

  @Test
  @Order(10)
  void shouldDeleteRtacHolding_withItemType_whenItemDeleteEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
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
    });
  }

  @Test
  @Order(11)
  void shouldUpdateRtacHoldingDueDate_withItemType_whenLoanCreateEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
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
    });
  }

  @Test
  @Order(12)
  void shouldUpdateRtacHoldingDueDate_withItemType_whenLoanUpdateEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM);
      var event = loadCirculationResourceEvent(UPDATE_LOAN_EVENT_PATH);
      // When
      sendLoanKafkaMessage(event, ITEM_ID);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(ITEM_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getDueDate()).isNull();
      });
    });
  }

  @Test
  @Order(13)
  void shouldUpdateRtacHoldingRequestCount_withItemType_whenOpenRequestCreateEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
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
    });
  }

  @Test
  @Order(14)
  void shouldDecreaseRtacHoldingRequestCount_withItemType_whenClosedRequestUpdateEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
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
    });
  }

  @Test
  @Order(15)
  void shouldCreateRtacHolding_withPieceType_whenPieceCreateEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      createExistingRtacHoldingEntity(HOLDINGS_ID_1, TypeEnum.HOLDING);
      var event = loadPieceResourceEvent(CREATE_PIECE_EVENT_PATH);
      // When
      sendPieceKafkaMessage(event, PIECE_ID);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(PIECE_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getInstanceFormatIds().getFirst()).isEqualTo(INSTANCE_FORMAT_ID);
        assertThat(holding.get().getRtacHolding().getStatus()).isEqualTo("Expected");
      });
    });
  }

  @Test
  @Order(16)
  void shouldUpdateRtacHolding_withPieceType_whenPieceUpdateEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
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
    });
  }

  @Test
  @Order(17)
  void shouldDeleteRtacHolding_withPieceType_whenPieceDeleteEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
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
    });
  }

  @Test
  @Order(18)
  void shouldClearLocationsCache_whenLocationCreateEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      //preload cache
      inventoryReferenceDataService.getLocationsMap();
      var event = loadInventoryResourceEvent(CREATE_LOCATION_EVENT_PATH);
      // When
      sendLocationKafkaMessage(event);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var updatedCache = cacheManager.getCache("locationsMap");
        assertThat(updatedCache).isNotNull();
        assertThat(updatedCache.get("locations_" + TEST_TENANT)).isNull();
      });
    });
  }

  @Test
  @Order(19)
  void shouldUpdateRtacHolding_whenLocationUpdateEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM);
      var event = loadInventoryResourceEvent(UPDATE_LOCATION_EVENT_PATH);
      // When
      sendLocationKafkaMessage(event);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(ITEM_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getLocation().getName()).isEqualTo("New location name");
        assertThat(holding.get().getRtacHolding().getLocation().getCode()).isEqualTo("New location code");
      });
    });
  }

  @Test
  @Order(20)
  @Execution(ExecutionMode.SAME_THREAD)
  void shouldClearLocationsCache_whenLocationDeleteEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      //preload cache
      inventoryReferenceDataService.getLocationsMap();
      var event = loadInventoryResourceEvent(DELETE_LOCATION_EVENT_PATH);
      // When
      sendLocationKafkaMessage(event);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var updatedCache = cacheManager.getCache("locationsMap");
        assertThat(updatedCache).isNotNull();
        assertThat(updatedCache.get("locations_" + TEST_TENANT)).isNull();
      });
    });
  }

  @Test
  @Order(21)
  @Execution(ExecutionMode.SAME_THREAD)
  void shouldClearLibraryCache_whenLibraryCreateEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      //preload cache
      inventoryReferenceDataService.getLibraryMap();
      var event = loadInventoryResourceEvent(CREATE_LIBRARY_EVENT_PATH);
      // When
      sendLibraryKafkaMessage(event);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var updatedCache = cacheManager.getCache("libraryMap");
        assertThat(updatedCache).isNotNull();
        assertThat(updatedCache.get("library_" + TEST_TENANT)).isNull();
      });
    });
  }

  @Test
  @Order(22)
  void shouldUpdateRtacHolding_whenLibraryUpdateEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM);
      var event = loadInventoryResourceEvent(UPDATE_LIBRARY_EVENT_PATH);
      // When
      sendLibraryKafkaMessage(event);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(ITEM_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getLibrary().getName()).isEqualTo("New library name");
        assertThat(holding.get().getRtacHolding().getLibrary().getCode()).isEqualTo("New library code");
      });
    });
  }

  @Test
  @Order(23)
  @Execution(ExecutionMode.SAME_THREAD)
  void shouldClearLibraryCache_whenLibraryDeleteEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      //preload cache
      inventoryReferenceDataService.getLibraryMap();
      var event = loadInventoryResourceEvent(DELETE_LIBRARY_EVENT_PATH);
      // When
      sendLibraryKafkaMessage(event);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var updatedCache = cacheManager.getCache("libraryMap");
        assertThat(updatedCache).isNotNull();
        assertThat(updatedCache.get("library_" + TEST_TENANT)).isNull();
      });
    });
  }

  @Test
  @Order(24)
  @Execution(ExecutionMode.SAME_THREAD)
  void shouldClearMaterialTypesCache_whenMaterialTypeCreateEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      inventoryReferenceDataService.getMaterialTypesMap();
      var event = loadInventoryResourceEvent(CREATE_MATERIAL_TYPE_EVENT_PATH);

      // When
      sendMaterialTypeKafkaMessage(event);

      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var updatedCache = cacheManager.getCache("materialTypesMap");
        assertThat(updatedCache).isNotNull();
        assertThat(updatedCache.get("materialTypes_" + TEST_TENANT)).isNull();
      });
    });
  }

  @Test
  @Order(25)
  void shouldUpdateRtacHoldingMaterialTypeAndClearCache_whenMaterialTypeUpdateEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM);
      var holding = holdingRepository.findByIdId(UUID.fromString(ITEM_ID)).orElseThrow();
      holding.getRtacHolding().setMaterialType(new RtacHoldingMaterialType().id(MATERIAL_TYPE_ID).name(OLD_MATERIAL_TYPE_NAME));
      holdingRepository.save(holding);
      inventoryReferenceDataService.getMaterialTypesMap();
      var event = loadInventoryResourceEvent(UPDATE_MATERIAL_TYPE_EVENT_PATH);

      // When
      sendMaterialTypeKafkaMessage(event);

      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var updatedHolding = holdingRepository.findByIdId(UUID.fromString(ITEM_ID)).orElseThrow();
        assertThat(updatedHolding.getRtacHolding().getMaterialType().getName()).isEqualTo(UPDATED_MATERIAL_TYPE_NAME);
        var updatedCache = cacheManager.getCache("materialTypesMap");
        assertThat(updatedCache).isNotNull();
        assertThat(updatedCache.get("materialTypes_" + TEST_TENANT)).isNull();
      });
    });
  }

  @Test
  @Order(26)
  @Execution(ExecutionMode.SAME_THREAD)
  void shouldClearLoanTypesCache_whenLoanTypeCreateEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      inventoryReferenceDataService.getLoanTypesMap();
      var event = loadInventoryResourceEvent(CREATE_LOAN_TYPE_EVENT_PATH);

      // When
      sendLoanTypeKafkaMessage(event);

      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var updatedCache = cacheManager.getCache("loanTypesMap");
        assertThat(updatedCache).isNotNull();
        assertThat(updatedCache.get("loanTypes_" + TEST_TENANT)).isNull();
      });
    });
  }

  @Test
  @Order(27)
  void shouldUpdateTemporaryAndPermanentLoanTypesAndClearCache_whenLoanTypeUpdateEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM);
      createExistingRtacHoldingEntity(HOLDINGS_ID_2, TypeEnum.HOLDING);

      var updatedTargetHolding = holdingRepository.findByIdId(UUID.fromString(ITEM_ID)).orElseThrow();
      updatedTargetHolding.getRtacHolding().setTemporaryLoanType(OLD_LOAN_TYPE_NAME);
      updatedTargetHolding.getRtacHolding().setPermanentLoanType(OLD_LOAN_TYPE_NAME);
      holdingRepository.save(updatedTargetHolding);

      var unchangedHolding = holdingRepository.findByIdId(UUID.fromString(HOLDINGS_ID_2)).orElseThrow();
      unchangedHolding.getRtacHolding().setTemporaryLoanType(UNCHANGED_LOAN_TYPE_NAME);
      unchangedHolding.getRtacHolding().setPermanentLoanType(UNCHANGED_LOAN_TYPE_NAME);
      holdingRepository.save(unchangedHolding);

      inventoryReferenceDataService.getLoanTypesMap();
      var event = loadInventoryResourceEvent(UPDATE_LOAN_TYPE_EVENT_PATH);

      // When
      sendLoanTypeKafkaMessage(event);

      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var targetHolding = holdingRepository.findByIdId(UUID.fromString(ITEM_ID)).orElseThrow();
        assertThat(targetHolding.getRtacHolding().getTemporaryLoanType()).isEqualTo(NEW_LOAN_TYPE_NAME);
        assertThat(targetHolding.getRtacHolding().getPermanentLoanType()).isEqualTo(NEW_LOAN_TYPE_NAME);

        var sameHolding = holdingRepository.findByIdId(UUID.fromString(HOLDINGS_ID_2)).orElseThrow();
        assertThat(sameHolding.getRtacHolding().getTemporaryLoanType()).isEqualTo(UNCHANGED_LOAN_TYPE_NAME);
        assertThat(sameHolding.getRtacHolding().getPermanentLoanType()).isEqualTo(UNCHANGED_LOAN_TYPE_NAME);

        var updatedCache = cacheManager.getCache("loanTypesMap");
        assertThat(updatedCache).isNotNull();
        assertThat(updatedCache.get("loanTypes_" + TEST_TENANT)).isNull();
      });
    });
  }

  @Test
  @Order(28)
  void shouldClearCache_whenBoundWithCreateEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM);
      createExistingRtacHoldingEntity(HOLDINGS_ID_2, TypeEnum.HOLDING);
      var event = loadInventoryResourceEvent(CREATE_BOUND_WITH_EVENT_PATH);
      // When
      sendBoundWithEvent(event);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var holdings = holdingRepository.findAllByIdInstanceId(UUID.fromString(INSTANCE_ID_2), Pageable.ofSize(10));
        assertThat(holdings).isEmpty();
      });
    });
  }

  @Test
  @Order(29)
  void shouldDeleteRtacHolding_withItemType_whenBoundWithDeleteEventIsSent() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM, true);
      var event = loadInventoryResourceEvent(DELETE_BOUND_WITH_EVENT_PATH);
      // When
      sendBoundWithEvent(event);
      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var count = holdingRepository.count();
        assertThat(count).isZero();
      });
    });
  }

  @Test
  @Order(30)
  void shouldUpdateRtacHolding_whenInstanceUpdateEventIsSent_forMemberTenant() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM);
      var event = loadInventoryResourceEvent(UPDATE_INSTANCE_MEMBER_TENANT_EVENT_PATH);

      // When
      sendInstanceKafkaMessage(event);

      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var rtacHoldingId = new RtacHoldingId(UUID.fromString(INSTANCE_ID_1), TypeEnum.ITEM, UUID.fromString(ITEM_ID));
        var holding = holdingRepository.findById(rtacHoldingId);
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getInstanceFormatIds().size()).isEqualTo(1);
        assertThat(holding.get().getRtacHolding().getInstanceFormatIds().getFirst()).isEqualTo(INSTANCE_FORMAT_ID);
      });
    });
  }

  @Test
  @Order(31)
  void shouldUpdateEcsRtacHoldings_whenInstanceUpdateEventIsSent_forCentralTenant() throws Exception {
    // Given
    setUpTenant(mockMvc, TEST_CENTRAL_TENANT);
    setUpTenant(mockMvc, TEST_MEMBER_TENANT);

    withinTenant(TEST_CENTRAL_TENANT, () -> createExistingRtacHoldingEntity(PIECE_ID, TypeEnum.PIECE));
    withinTenant(TEST_MEMBER_TENANT, () -> createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM));

    // When
    var event = loadInventoryResourceEvent(UPDATE_INSTANCE_CENTRAL_TENANT_EVENT_PATH);
    sendInstanceKafkaMessage(event);

    // Then
    assertInstanceFormatIdsUpdated(TEST_MEMBER_TENANT, TypeEnum.ITEM, ITEM_ID);
    assertInstanceFormatIdsUpdated(TEST_CENTRAL_TENANT, TypeEnum.PIECE, PIECE_ID);
  }

  @Test
  @Order(32)
  void shouldMoveHoldingsHierarchyToCachedInstance_whenHoldingsInstanceIdChanged() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      createExistingRtacHoldingEntity(HOLDINGS_ID_1, TypeEnum.HOLDING, INSTANCE_ID_1, HOLDINGS_ID_1, INSTANCE_FORMAT_ID);
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM, INSTANCE_ID_1, HOLDINGS_ID_1, INSTANCE_FORMAT_ID);
      createExistingRtacHoldingEntity(PIECE_ID, TypeEnum.PIECE, INSTANCE_ID_1, HOLDINGS_ID_1, INSTANCE_FORMAT_ID);
      createExistingRtacHoldingEntity(HOLDINGS_ID_2, TypeEnum.HOLDING, INSTANCE_ID_2, HOLDINGS_ID_2, INSTANCE_FORMAT_ID_2);
      var event = loadInventoryResourceEvent(UPDATE_HOLDINGS_MOVE_TO_CACHED_INSTANCE_EVENT_PATH);

      // When
      sendHoldingsKafkaMessage(event, HOLDINGS_ID_1);

      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        assertHoldingMovedToInstance(HOLDINGS_ID_1, TypeEnum.HOLDING, INSTANCE_ID_2, HOLDINGS_ID_1, INSTANCE_FORMAT_ID_2);
        assertHoldingMovedToInstance(ITEM_ID, TypeEnum.ITEM, INSTANCE_ID_2, HOLDINGS_ID_1, INSTANCE_FORMAT_ID_2);
        assertHoldingMovedToInstance(PIECE_ID, TypeEnum.PIECE, INSTANCE_ID_2, HOLDINGS_ID_1, INSTANCE_FORMAT_ID_2);

        var oldItemId = new RtacHoldingId(UUID.fromString(INSTANCE_ID_1), TypeEnum.ITEM, UUID.fromString(ITEM_ID));
        assertThat(holdingRepository.findById(oldItemId)).isEmpty();
      });
    });
  }

  @Test
  @Order(33)
  void shouldMoveItemToAnotherHolding_whenItemHoldingChanged() {
    withinTenant(TEST_TENANT, () -> {
      // Given
      createExistingRtacHoldingEntity(HOLDINGS_ID_1, TypeEnum.HOLDING, INSTANCE_ID_1, HOLDINGS_ID_1, INSTANCE_FORMAT_ID);
      createExistingRtacHoldingEntity(HOLDINGS_ID_2, TypeEnum.HOLDING, INSTANCE_ID_1, HOLDINGS_ID_2, INSTANCE_FORMAT_ID_2);
      createExistingRtacHoldingEntity(ITEM_ID, TypeEnum.ITEM, INSTANCE_ID_1, HOLDINGS_ID_1, INSTANCE_FORMAT_ID);
      var event = loadInventoryResourceEvent(UPDATE_ITEM_MOVE_TO_ANOTHER_HOLDING_EVENT_PATH);

      // When
      sendItemKafkaMessage(event, ITEM_ID);

      // Then
      await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
        var itemId = new RtacHoldingId(UUID.fromString(INSTANCE_ID_1), TypeEnum.ITEM, UUID.fromString(ITEM_ID));
        var movedItem = holdingRepository.findById(itemId);

        assertThat(movedItem).isPresent();
        assertThat(movedItem.get().getRtacHolding().getHoldingsId()).isEqualTo(HOLDINGS_ID_2);
        assertThat(movedItem.get().getRtacHolding().getInstanceFormatIds()).containsExactly(INSTANCE_FORMAT_ID_2);
        assertThat(movedItem.get().getRtacHolding().getStatus()).isEqualTo(NEW_STATUS);
      });
    });
  }

  @Test
  @Order(34)
  @Execution(ExecutionMode.SAME_THREAD)
  void shouldSendMalformedRecordToDeadLetterTopic_andKeepConsumingFollowingRecords() throws Exception {
    // Given - a unique marker keeps this assertion independent of anything else already on the dead-letter topic
    var malformedPayload = TestUtil.readFileContentFromResources(MALFORMED_ITEM_EVENT_PATH)
      .replace(MALFORMED_EVENT_ID_PLACEHOLDER, UUID.randomUUID().toString());
    var validEvent = loadInventoryResourceEvent(CREATE_ITEM_EVENT_PATH);

    withinTenant(TEST_TENANT, () -> {
      createExistingRtacHoldingEntity(HOLDINGS_ID_1, TypeEnum.HOLDING);

      // When - the poison pill is committed to the partition first, then a valid record behind it
      sendRawItemKafkaMessage(ITEM_ID, malformedPayload);
      sendItemKafkaMessage(validEvent, ITEM_ID);

      // Then - the unparseable record is parked on the dead-letter topic with its bytes untouched
      var deadLetterRecord = awaitDeadLetterRecord(TestConstant.ITEM_TOPIC + DLT_TOPIC_SUFFIX,
        malformedPayload::equals);
      assertThat(new String(deadLetterRecord.value(), StandardCharsets.UTF_8)).isEqualTo(malformedPayload);

      // ...and, crucially, the consumer advanced past it instead of re-polling the same offset forever
      await().atMost(DLT_POLL_TIMEOUT).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(ITEM_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getType()).isEqualTo(TypeEnum.ITEM);
      });
    });
  }

  @Test
  @Order(35)
  @Execution(ExecutionMode.SAME_THREAD)
  void shouldRetryFailingHandlerWithGrowingDelays_thenSendRecordToDeadLetterTopic() throws Exception {
    // Given - a handler that keeps failing with a retryable exception, recording when each attempt happened
    var attemptTimestamps = Collections.synchronizedList(new ArrayList<Long>());
    doReturn(Optional.of(alwaysFailingItemHandler(attemptTimestamps)))
      .when(eventHandlerFactory).getInventoryHandler(any(), eq(InventoryEntityType.ITEM));

    // A unique event id makes sure the dead-letter lookup below can only match this test's own record
    var uniqueEventId = UUID.randomUUID().toString();
    var event = loadInventoryResourceEvent(CREATE_ITEM_EVENT_PATH).eventId(uniqueEventId);

    // When
    withinTenant(TEST_TENANT, () -> sendItemKafkaMessage(event, ITEM_ID));

    // Then - the record is delivered the configured number of times, with a growing pause between attempts.
    // Waiting for the DLT record first means the retries are known to be exhausted, so the count below is final
    // rather than a value the assertion might catch mid-flight.
    var deadLetterRecord = awaitDeadLetterRecord(TestConstant.ITEM_TOPIC + DLT_TOPIC_SUFFIX,
      value -> value.contains(uniqueEventId));

    assertThat(attemptTimestamps).hasSize(EXPECTED_DELIVERIES);
    var firstGapNanos = attemptTimestamps.get(1) - attemptTimestamps.get(0);
    var secondGapNanos = attemptTimestamps.get(2) - attemptTimestamps.get(1);
    assertThat(firstGapNanos).isGreaterThan(Duration.ofMillis(RETRY_INTERVAL_MS).toNanos());
    assertThat(secondGapNanos).isGreaterThan(firstGapNanos);

    // The handler failure path forwards the deserialized event, so the DLT gets JSON rather than the raw bytes
    assertThat(new String(deadLetterRecord.value(), StandardCharsets.UTF_8)).contains(uniqueEventId);
  }

  @Test
  @Order(36)
  @Execution(ExecutionMode.SAME_THREAD)
  void shouldSkipTombstoneRecord_andKeepConsumingFollowingRecords() throws Exception {
    // Given
    var validEvent = loadInventoryResourceEvent(CREATE_ITEM_EVENT_PATH);

    withinTenant(TEST_TENANT, () -> {
      createExistingRtacHoldingEntity(HOLDINGS_ID_1, TypeEnum.HOLDING);

      // When - a real tombstone (null value) is committed ahead of a valid record
      sendRawItemKafkaMessage(ITEM_ID, null);
      sendItemKafkaMessage(validEvent, ITEM_ID);

      // Then - the tombstone is skipped without tripping the listener, and the record behind it still lands.
      // Before the null guard this NPE'd on consumerRecord.value().getTenant() and the record was retried to the DLT.
      await().atMost(DLT_POLL_TIMEOUT).untilAsserted(() -> {
        var holding = holdingRepository.findByIdId(UUID.fromString(ITEM_ID));
        assertThat(holding).isPresent();
        assertThat(holding.get().getRtacHolding().getType()).isEqualTo(TypeEnum.ITEM);
      });
    });
  }

  private InventoryEventHandler alwaysFailingItemHandler(List<Long> attemptTimestamps) {
    return new InventoryEventHandler() {
      @Override
      public void handle(InventoryResourceEvent resourceEvent) {
        attemptTimestamps.add(System.nanoTime());
        throw new IllegalStateException("Simulated transient failure");
      }

      @Override
      public InventoryEventType getEventType() {
        return InventoryEventType.CREATE;
      }

      @Override
      public InventoryEntityType getEntityType() {
        return InventoryEntityType.ITEM;
      }
    };
  }

  /**
   * Publishes an arbitrary string so the payload reaches the consumer exactly as written, bypassing the
   * JSON-serializing template used everywhere else. The send is awaited so the poison pill is guaranteed to sit
   * before the valid record on the partition.
   */
  private void sendRawItemKafkaMessage(String key, String payload) throws Exception {
    Map<String, Object> config = Map.of(
      ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
      ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
      ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

    var producerFactory = new DefaultKafkaProducerFactory<String, String>(config);
    try {
      new KafkaTemplate<>(producerFactory)
        .send(new ProducerRecord<>(TestConstant.ITEM_TOPIC, key, payload))
        .get(30, TimeUnit.SECONDS);
    } finally {
      producerFactory.destroy();
    }
  }

  /**
   * The dead-letter topic keeps records from earlier tests, so the caller has to say which record it is waiting for
   * rather than taking whatever happens to be at the head of the topic.
   */
  private ConsumerRecord<String, byte[]> awaitDeadLetterRecord(String deadLetterTopic, Predicate<String> matcher) {
    Map<String, Object> config = Map.of(
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
      ConsumerConfig.GROUP_ID_CONFIG, "dlt-assertion-" + UUID.randomUUID(),
      ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
      ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
      ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

    try (var consumer = new KafkaConsumer<String, byte[]>(config)) {
      consumer.subscribe(List.of(deadLetterTopic));
      var deadline = Instant.now().plus(DLT_POLL_TIMEOUT);
      while (Instant.now().isBefore(deadline)) {
        for (var polled : consumer.poll(Duration.ofMillis(500)).records(deadLetterTopic)) {
          if (polled.value() != null && matcher.test(new String(polled.value(), StandardCharsets.UTF_8))) {
            return polled;
          }
        }
      }
    }
    throw new AssertionError("No matching record arrived on dead-letter topic " + deadLetterTopic);
  }

  private void assertHoldingMovedToInstance(String id, TypeEnum type, String instanceId, String holdingsId,
                                            String instanceFormatId) {
    var rtacHoldingId = new RtacHoldingId(UUID.fromString(instanceId), type, UUID.fromString(id));
    var holding = holdingRepository.findById(rtacHoldingId);

    assertThat(holding).isPresent();
    assertThat(holding.get().getRtacHolding().getInstanceId()).isEqualTo(instanceId);
    assertThat(holding.get().getRtacHolding().getHoldingsId()).isEqualTo(holdingsId);
    assertThat(holding.get().getRtacHolding().getInstanceFormatIds()).containsExactly(instanceFormatId);
  }

  private void assertInstanceFormatIdsUpdated(String tenant, TypeEnum type, String holdingId) {
    withinTenant(tenant, () -> await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
      var rtacHoldingId = new RtacHoldingId(UUID.fromString(INSTANCE_ID_1), type, UUID.fromString(holdingId));
      var holding = holdingRepository.findById(rtacHoldingId);

      assertThat(holding).isPresent();
      assertThat(holding.get().getRtacHolding().getInstanceFormatIds()).hasSize(1);
      assertThat(holding.get().getRtacHolding().getInstanceFormatIds().getFirst()).isEqualTo(
        INSTANCE_FORMAT_ID);
    }));
  }

  private void createExistingRtacHoldingEntity(String id, TypeEnum type) {
    createExistingRtacHoldingEntity(id, type, false);
  }

  private void createExistingRtacHoldingEntity(String id, TypeEnum type, boolean isBoundWith) {
    createExistingRtacHoldingEntity(id, type, isBoundWith, INSTANCE_ID_1, HOLDINGS_ID_1, INSTANCE_FORMAT_ID, OLD_CALL_NUMBER);
  }

  private void createExistingRtacHoldingEntity(String id, TypeEnum type, String instanceId, String holdingsId,
                                               String instanceFormatId) {
    createExistingRtacHoldingEntity(id, type, false, instanceId, holdingsId, instanceFormatId, OLD_CALL_NUMBER);
  }

  private void createExistingRtacHoldingEntity(String id, TypeEnum type, boolean isBoundWith, String instanceId,
                                               String holdingsId, String instanceFormatId, String callNumber) {
    var entity = createGeneralRtacHoldingEntity(id, type, instanceId);
    var rtacHolding = createRtacHolding(id, type, isBoundWith, callNumber, instanceId, holdingsId, instanceFormatId);
    entity.setRtacHolding(rtacHolding);
    holdingRepository.save(entity);
  }

  private RtacHoldingEntity createGeneralRtacHoldingEntity(String id, TypeEnum type, String instanceId) {
    RtacHoldingEntity entity = new RtacHoldingEntity();
    RtacHoldingId rtacHoldingId = new RtacHoldingId();
    rtacHoldingId.setId(UUID.fromString(id));
    rtacHoldingId.setInstanceId(UUID.fromString(instanceId));
    rtacHoldingId.setType(type);
    entity.setId(rtacHoldingId);
    entity.setCreatedAt(Instant.now());
    return entity;
  }

  private RtacHolding createRtacHolding(String id, TypeEnum type, boolean isBoundWith, String callNumber,
                                        String instanceId, String holdingsId, String instanceFormatId) {
    var rtacHolding = new RtacHolding();
    rtacHolding.setCallNumber(callNumber);
    rtacHolding.setId(id);
    rtacHolding.setInstanceId(instanceId);
    rtacHolding.setHoldingsId(holdingsId);
    rtacHolding.setType(type);
    rtacHolding.setDueDate(OLD_DUE_DATE);
    rtacHolding.setStatus(OLD_STATUS);
    rtacHolding.setHoldingsCopyNumber(OLD_HOLDINGS_COPY_NUMBER);
    rtacHolding.setTotalHoldRequests(1);
    rtacHolding.setIsBoundWith(isBoundWith);
    rtacHolding.setInstanceFormatIds(List.of(instanceFormatId));

    var location = new RtacHoldingLocation();
    location.setId(OLD_LOCATION_ID);
    rtacHolding.setLocation(location);

    var library = new RtacHoldingLibrary();
    library.setId(LIBRARY_ID);
    rtacHolding.setLibrary(library);

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

  private void sendInstanceKafkaMessage(InventoryResourceEvent event) {
    ProducerRecord<String, InventoryResourceEvent> holdingsRecord = new ProducerRecord<>(TestConstant.INSTANCE_TOPIC,
      event.getEventId(), event);
    inventoryKafkaTemplate.send(holdingsRecord);
  }


  private void sendHoldingsKafkaMessage(InventoryResourceEvent event, String id) {
    ProducerRecord<String, InventoryResourceEvent> holdingsRecord = new ProducerRecord<>(TestConstant.HOLDINGS_TOPIC,
      id, event);
    inventoryKafkaTemplate.send(holdingsRecord);
  }

  private void sendItemKafkaMessage(InventoryResourceEvent event, String id) {
    ProducerRecord<String, InventoryResourceEvent> itemRecord = new ProducerRecord<>(TestConstant.ITEM_TOPIC, id,
      event);
    inventoryKafkaTemplate.send(itemRecord);
  }

  private void sendLocationKafkaMessage(InventoryResourceEvent event) {
    ProducerRecord<String, InventoryResourceEvent> itemRecord = new ProducerRecord<>(TestConstant.LOCATION_TOPIC,
      OLD_LOCATION_ID, event);
    inventoryKafkaTemplate.send(itemRecord);
  }

  private void sendLibraryKafkaMessage(InventoryResourceEvent event) {
    ProducerRecord<String, InventoryResourceEvent> itemRecord = new ProducerRecord<>(TestConstant.LIBRARY_TOPIC,
      LIBRARY_ID, event);
    inventoryKafkaTemplate.send(itemRecord);
  }

  private void sendMaterialTypeKafkaMessage(InventoryResourceEvent event) {
    ProducerRecord<String, InventoryResourceEvent> itemRecord = new ProducerRecord<>(TestConstant.MATERIAL_TYPE_TOPIC,
      MATERIAL_TYPE_ID, event);
    inventoryKafkaTemplate.send(itemRecord);
  }

  private void sendLoanTypeKafkaMessage(InventoryResourceEvent event) {
    ProducerRecord<String, InventoryResourceEvent> itemRecord = new ProducerRecord<>(TestConstant.LOAN_TYPE_TOPIC,
      event.getEventId(), event);
    inventoryKafkaTemplate.send(itemRecord);
  }

  private void sendLoanKafkaMessage(CirculationResourceEvent event, String id) {
    var loanRecord = new ProducerRecord<>(TestConstant.LOAN_TOPIC, id, event);
    circualationKafkaTemplate.send(loanRecord);
  }

  private void sendRequestKafkaMessage(CirculationResourceEvent event, String id) {
    var requestRecord = new ProducerRecord<>(TestConstant.REQUEST_TOPIC, id, event);
    circualationKafkaTemplate.send(requestRecord);
  }

  private void sendPieceKafkaMessage(PieceResourceEvent event, String id) {
    var header = new RecordHeader("folio.tenantId", TEST_TENANT.getBytes());
    var pieceRecord = new ProducerRecord<>(TestConstant.PIECE_TOPIC, 0, id, event, List.of(header));
    pieceKafkaTemplate.send(pieceRecord);
  }

  private void sendBoundWithEvent(InventoryResourceEvent event) {
    var boundWithRecord = new ProducerRecord<>(TestConstant.BOUND_WITH_TOPIC, ITEM_ID, event);
    inventoryKafkaTemplate.send(boundWithRecord);
  }
}
