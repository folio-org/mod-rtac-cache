package org.folio.rtaccache.service.handler;

public interface EventHandler <T,E> {

  T getEventType();

  void handle(E resourceEvent);

}
