package org.folio.rtaccache.domain.exception;

public class RtacKafkaUpdateException extends RuntimeException {

  public RtacKafkaUpdateException(String message, Throwable cause) {
    super(message, cause);
  }

  public RtacKafkaUpdateException(Throwable cause) {
    super(cause);
  }
}
