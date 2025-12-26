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

  @Column(name = "shared", nullable = false)
  private boolean shared;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "rtac_holding_json", columnDefinition = "jsonb", nullable = false)
  private RtacHolding rtacHolding;

  @Column(name = "created_at")
  private Instant createdAt;

  public RtacHoldingEntity(RtacHoldingId id, boolean shared, RtacHolding rtacHolding, Instant createdAt) {
    this.id = id;
    this.shared = shared;
    this.rtacHolding = rtacHolding;
    this.createdAt = createdAt;
  }
}
