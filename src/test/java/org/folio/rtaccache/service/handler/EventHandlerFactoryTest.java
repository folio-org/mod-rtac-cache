package org.folio.rtaccache.service.handler;

import static org.folio.rtaccache.domain.dto.CirculationEntityType.LOAN;
import static org.folio.rtaccache.domain.dto.InventoryEntityType.ITEM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.folio.rtaccache.domain.dto.CirculationEventType;
import org.folio.rtaccache.domain.dto.InventoryEventType;
import org.folio.rtaccache.domain.dto.PieceEventAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventHandlerFactoryTest {

  private final EventHandlerFactory factory = new EventHandlerFactory(List.of(), List.of(), List.of());
  private final CapturingAppender appender = new CapturingAppender();
  private Logger logger;

  @BeforeEach
  void setUp() {
    logger = (Logger) LogManager.getLogger(EventHandlerFactory.class);
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.WARN);
  }

  @AfterEach
  void tearDown() {
    logger.removeAppender(appender);
    appender.stop();
  }

  @Test
  void getInventoryHandler_shouldReturnEmptyAndLogWarning_whenEventTypeIsUnknown() {
    var result = factory.getInventoryHandler(InventoryEventType.UNKNOWN, ITEM);

    assertTrue(result.isEmpty());
    assertTrue(appender.hasWarnMessageContaining("ITEM"));
  }

  @Test
  void getCirculationHandler_shouldReturnEmptyAndLogWarning_whenEventTypeIsUnknown() {
    var result = factory.getCirculationHandler(CirculationEventType.UNKNOWN, LOAN);

    assertTrue(result.isEmpty());
    assertTrue(appender.hasWarnMessageContaining("LOAN"));
  }

  @Test
  void getPieceEventHandler_shouldReturnEmptyAndLogWarning_whenEventTypeIsUnknown() {
    var result = factory.getPieceEventHandler(PieceEventAction.UNKNOWN);

    assertTrue(result.isEmpty());
    assertFalse(appender.getEvents().isEmpty());
    assertEquals(Level.WARN, appender.getEvents().get(0).getLevel());
  }

  private static final class CapturingAppender extends AbstractAppender {

    private final List<LogEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();

    CapturingAppender() {
      super("capturing-appender", null, null, false, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
      events.add(event.toImmutable());
    }

    List<LogEvent> getEvents() {
      return events;
    }

    boolean hasWarnMessageContaining(String text) {
      return events.stream()
        .anyMatch(event -> event.getLevel() == Level.WARN
          && event.getMessage().getFormattedMessage().contains(text));
    }
  }

}
