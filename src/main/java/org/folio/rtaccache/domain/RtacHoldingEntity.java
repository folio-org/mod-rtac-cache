package org.folio.rtaccache.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "rtac_holding")
@Getter
@Setter
@NoArgsConstructor
public class RtacHoldingEntity {

  @EmbeddedId
  private RtacHoldingId id;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "rtac_holding_json", columnDefinition = "jsonb", nullable = false)
  private RtacHolding rtacHolding;

  @Column(name = "created_at")
  private Instant createdAt;

  public RtacHoldingEntity(RtacHoldingId id, RtacHolding rtacHolding, Instant createdAt) {
    this.id = id;
    this.rtacHolding = rtacHolding;
    this.createdAt = createdAt;
  }
}
