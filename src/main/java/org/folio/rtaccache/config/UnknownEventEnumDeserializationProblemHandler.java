package org.folio.rtaccache.config;

import java.util.Map;
import org.folio.rtaccache.domain.dto.CirculationEventType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.PieceEventAction;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.DeserializationProblemHandler;

/**
 * The OpenAPI-generated {@code fromValue()} factory methods for these enums throw
 * {@link IllegalArgumentException} on any value not present in the spec's enum list (e.g. mod-inventory
 * sending a "REINDEX" event type unknown to inventoryEventType.json). Jackson routes that failure here
 * instead of propagating it, so unrecognized values fall back to each enum's UNKNOWN constant rather than
 * failing deserialization of the whole Kafka record.
 */
public class UnknownEventEnumDeserializationProblemHandler extends DeserializationProblemHandler {

  private static final Map<Class<?>, Object> FALLBACK_VALUES = Map.of(
    InventoryEventType.class, InventoryEventType.UNKNOWN,
    CirculationEventType.class, CirculationEventType.UNKNOWN,
    PieceEventAction.class, PieceEventAction.UNKNOWN
  );

  @Override
  public Object handleInstantiationProblem(DeserializationContext ctxt, Class<?> instClass, Object argument,
                                            Throwable failure) {
    return FALLBACK_VALUES.getOrDefault(instClass, NOT_HANDLED);
  }
}
