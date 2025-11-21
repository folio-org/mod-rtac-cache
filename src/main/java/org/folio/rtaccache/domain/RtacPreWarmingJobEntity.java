package org.folio.rtaccache.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "rtac_pre_warming_job")
public class RtacPreWarmingJobEntity {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "start_date", nullable = false)
  private Instant startDate = Instant.now();

  @Column(name = "end_date")
  private Instant endDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private JobStatus status = JobStatus.RUNNING;

  @Column(name = "error_message")
  private String errorMessage;

  public enum JobStatus {
    RUNNING,
    COMPLETED,
    FAILED
  }
}
