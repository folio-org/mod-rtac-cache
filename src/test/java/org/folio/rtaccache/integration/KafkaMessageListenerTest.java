package org.folio.rtaccache.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.folio.rtaccache.domain.dto.Piece;
import org.folio.rtaccache.domain.dto.PieceResourceEvent;
import org.folio.rtaccache.service.ConsortiaService;
import org.folio.rtaccache.service.handler.EventHandlerFactory;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;

/**
 * Unit coverage for the tombstone guard added to every listener method and for piece tenant resolution -
 * {@link KafkaMessageListenerIT} covers the same behavior end-to-end through a real broker, but only exercises a
 * subset of the listener methods and never the "snapshot carries its own receiving tenant" branch.
 */
@ExtendWith(MockitoExtension.class)
class KafkaMessageListenerTest {

  private static final String FOLIO_TENANT_ID_HEADER = "folio.tenantId";
  private static final String TENANT_ID = "diku";

  @Mock
  private SystemUserScopedExecutionService executionService;
  @Mock
  private EventHandlerFactory eventHandlerFactory;
  @Mock
  private ConsortiaService consortiaService;
  @Mock
  private AsyncTaskExecutor taskExecutor;

  private KafkaMessageListener listener;

  @BeforeEach
  void setUp() {
    listener = new KafkaMessageListener(executionService, eventHandlerFactory, consortiaService, taskExecutor);
  }

  @Test
  void handleInstanceRecord_shouldSkip_whenRecordIsTombstone() {
    listener.handleInstanceRecord(tombstone());

    verifyNoInteractions(executionService);
  }

  @Test
  void handleHoldingsRecord_shouldSkip_whenRecordIsTombstone() {
    listener.handleHoldingsRecord(tombstone());

    verifyNoInteractions(executionService);
  }

  @Test
  void handleItemRecord_shouldSkip_whenRecordIsTombstone() {
    listener.handleItemRecord(tombstone());

    verifyNoInteractions(executionService);
  }

  @Test
  void handleLoanRecord_shouldSkip_whenRecordIsTombstone() {
    listener.handleLoanRecord(tombstone());

    verifyNoInteractions(executionService);
  }

  @Test
  void handleRequestRecord_shouldSkip_whenRecordIsTombstone() {
    listener.handleRequestRecord(tombstone());

    verifyNoInteractions(executionService);
  }

  @Test
  void handleLocationRecord_shouldSkip_whenRecordIsTombstone() {
    listener.handleLocationRecord(tombstone());

    verifyNoInteractions(executionService);
  }

  @Test
  void handleLibraryRecord_shouldSkip_whenRecordIsTombstone() {
    listener.handleLibraryRecord(tombstone());

    verifyNoInteractions(executionService);
  }

  @Test
  void handleMaterialTypeRecord_shouldSkip_whenRecordIsTombstone() {
    listener.handleMaterialTypeRecord(tombstone());

    verifyNoInteractions(executionService);
  }

  @Test
  void handleLoanTypeRecord_shouldSkip_whenRecordIsTombstone() {
    listener.handleLoanTypeRecord(tombstone());

    verifyNoInteractions(executionService);
  }

  @Test
  void handleBoundWithRecord_shouldSkip_whenRecordIsTombstone() {
    listener.handleBoundWithRecord(tombstone());

    verifyNoInteractions(executionService);
  }

  @Test
  void handlePieceRecord_shouldSkip_whenRecordIsTombstone() {
    listener.handlePieceRecord(pieceTombstone());

    verifyNoInteractions(executionService);
  }

  @Test
  void handlePieceRecord_shouldUseSnapshotReceivingTenantId_whenPresent() {
    var snapshotTenantId = "member_tenant";
    var event = new PieceResourceEvent().pieceSnapshot(new Piece().receivingTenantId(snapshotTenantId));
    var consumerRecord = pieceRecord(event, null);

    listener.handlePieceRecord(consumerRecord);

    verify(executionService).executeAsyncSystemUserScoped(eq(snapshotTenantId), any());
  }

  @Test
  void handlePieceRecord_shouldFallBackToHeaderTenantId_whenSnapshotHasNoReceivingTenantId() {
    var event = new PieceResourceEvent().pieceSnapshot(new Piece());
    var consumerRecord = pieceRecord(event, TENANT_ID);

    listener.handlePieceRecord(consumerRecord);

    verify(executionService).executeAsyncSystemUserScoped(eq(TENANT_ID), any());
  }

  @Test
  void handlePieceRecord_shouldSkipAndLogWarning_whenTenantCannotBeResolved() {
    var event = new PieceResourceEvent().pieceSnapshot(new Piece());
    var consumerRecord = pieceRecord(event, null);

    listener.handlePieceRecord(consumerRecord);

    verifyNoInteractions(executionService);
  }

  private static <T> ConsumerRecord<String, T> tombstone() {
    return new ConsumerRecord<>("test-topic", 0, 0L, "key", null);
  }

  private static ConsumerRecord<String, PieceResourceEvent> pieceTombstone() {
    return new ConsumerRecord<>("piece-topic", 0, 0L, "key", null);
  }

  private static ConsumerRecord<String, PieceResourceEvent> pieceRecord(PieceResourceEvent event, String tenantId) {
    var consumerRecord = new ConsumerRecord<>("piece-topic", 0, 0L, "key", event);
    if (tenantId != null) {
      consumerRecord.headers().add(new RecordHeader(FOLIO_TENANT_ID_HEADER, tenantId.getBytes(StandardCharsets.UTF_8)));
    }
    return consumerRecord;
  }

}
