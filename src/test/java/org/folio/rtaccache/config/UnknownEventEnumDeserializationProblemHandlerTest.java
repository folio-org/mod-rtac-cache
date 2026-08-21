package org.folio.rtaccache.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.folio.rtaccache.domain.dto.CirculationEventType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.InventoryResourceEvent;
import org.folio.rtaccache.domain.dto.PieceEventAction;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.JacksonMapperUtils;
import tools.jackson.databind.json.JsonMapper;

class UnknownEventEnumDeserializationProblemHandlerTest {

  private final JsonMapper jsonMapper = JacksonMapperUtils.enhancedJsonMapper().rebuild()
    .addHandler(new UnknownEventEnumDeserializationProblemHandler())
    .build();

  @Test
  void shouldDeserializeUnrecognizedInventoryEventTypeAsUnknown() {
    var result = jsonMapper.readValue("\"REINDEX\"", InventoryEventType.class);

    assertEquals(InventoryEventType.UNKNOWN, result);
  }

  @Test
  void shouldDeserializeUnrecognizedCirculationEventTypeAsUnknown() {
    var result = jsonMapper.readValue("\"SOME_NEW_EVENT\"", CirculationEventType.class);

    assertEquals(CirculationEventType.UNKNOWN, result);
  }

  @Test
  void shouldDeserializeUnrecognizedPieceEventActionAsUnknown() {
    var result = jsonMapper.readValue("\"Reindex\"", PieceEventAction.class);

    assertEquals(PieceEventAction.UNKNOWN, result);
  }

  @Test
  void shouldStillDeserializeKnownInventoryEventTypeNormally() {
    var result = jsonMapper.readValue("\"CREATE\"", InventoryEventType.class);

    assertEquals(InventoryEventType.CREATE, result);
  }

  @Test
  void shouldDeserializeWholeInventoryResourceEventWithUnrecognizedTypeAsUnknown() {
    var json = "{\"eventId\":\"1\",\"type\":\"REINDEX\",\"tenant\":\"diku\"}";

    var result = jsonMapper.readValue(json, InventoryResourceEvent.class);

    assertEquals(InventoryEventType.UNKNOWN, result.getType());
    assertEquals("diku", result.getTenant());
  }

}
