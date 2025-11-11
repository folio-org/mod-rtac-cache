package org.folio.rtaccache.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.folio.rtaccache.domain.dto.RtacHolding;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RtacHoldingId implements Serializable {

  @Column(name = "instance_id", nullable = false)
  private UUID instanceId;

  @Column(name = "type", length = 20, nullable = false)
  @Enumerated(EnumType.STRING)
  private RtacHolding.TypeEnum type;

  @Column(name = "id", nullable = false)
  private UUID id;

  public static RtacHoldingId from(RtacHolding rtacHolding) {
    return new RtacHoldingId(
        UUID.fromString(rtacHolding.getInstanceId()),
        rtacHolding.getType(),
        UUID.fromString(rtacHolding.getId())
    );
  }
}
