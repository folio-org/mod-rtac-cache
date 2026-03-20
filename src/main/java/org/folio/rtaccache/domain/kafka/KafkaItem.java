package org.folio.rtaccache.domain.kafka;

import lombok.Data;
import org.folio.rtaccache.domain.dto.Item;

@Data
public class KafkaItem extends Item {
  private String instanceId;
}
