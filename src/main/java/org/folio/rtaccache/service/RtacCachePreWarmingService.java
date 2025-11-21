package org.folio.rtaccache.service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.folio.rtaccache.domain.RtacPreWarmingJobEntity;
import org.folio.rtaccache.domain.RtacPreWarmingJobEntity.JobStatus;
import org.folio.rtaccache.domain.dto.RtacPreWarmingJob;
import org.folio.rtaccache.domain.dto.RtacPreWarmingJob.StatusEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.repository.RtacPreWarmingJobRepository;
import org.folio.spring.data.OffsetRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RequiredArgsConstructor
@Service
public class RtacCachePreWarmingService {

  @Qualifier("applicationTaskExecutor")
  private final AsyncTaskExecutor taskExecutor;
  private final RtacCacheGenerationService rtacCacheGenerationService;
  private final RtacHoldingRepository rtacHoldingRepository;
  private final RtacPreWarmingJobRepository rtacPreWarmingJobRepository;
  private static final int PRE_WARM_BATCH_SIZE = 30;

  public RtacPreWarmingJob getPreWarmingJobStatus(UUID jobId) {
    var preWarmJob = rtacPreWarmingJobRepository.findById(jobId)
      .orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RTAC Pre-Warming Job not found for id: " + jobId));
    return toResponse(preWarmJob);
  }

  public Page<RtacPreWarmingJob> getPreWarmingJobs(OffsetRequest offsetRequest) {
    var jobEntitiesPage = rtacPreWarmingJobRepository.findAllByOrderByStartDateDesc(offsetRequest);
    return jobEntitiesPage.map(this::toResponse);
  }

  public RtacPreWarmingJob submitPreWarmingJob(List<UUID> instanceIds) {
    log.info("Submitting RTAC cache pre-warming job for instances with instanceId count: {}", instanceIds.size());
    var preWarmJob = new RtacPreWarmingJobEntity();
    preWarmJob.setId(UUID.randomUUID());
    preWarmJob.setStatus(JobStatus.RUNNING);
    rtacPreWarmingJobRepository.save(preWarmJob);
    taskExecutor.submit(() -> startPreWarmingJob(preWarmJob, instanceIds));

    return toResponse(preWarmJob);
  }

  private void startPreWarmingJob(RtacPreWarmingJobEntity preWarmJob, List<UUID> instanceIds) {
    preWarmRtacCache(instanceIds)
      .whenCompleteAsync((r, ex) -> {
        if (ex != null) {
          log.error("RTAC cache pre-warming job {} failed. Cause: {}", preWarmJob.getId(), ex.getMessage(), ex);
          preWarmJob.setStatus(JobStatus.FAILED);
          preWarmJob.setErrorMessage(ex.getCause().getMessage());
        } else {
          log.info("RTAC cache pre-warming job {} completed successfully.", preWarmJob.getId());
          preWarmJob.setStatus(JobStatus.COMPLETED);
        }
        preWarmJob.setEndDate(Instant.now());
        rtacPreWarmingJobRepository.save(preWarmJob);
      }, taskExecutor);
  }

  private CompletableFuture<Void> preWarmRtacCache(List<UUID> instanceIds) {
    if (instanceIds == null || instanceIds.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

    for (int i = 0; i < instanceIds.size(); i += PRE_WARM_BATCH_SIZE) {
      List<UUID> batch = instanceIds.subList(i, Math.min(i + PRE_WARM_BATCH_SIZE, instanceIds.size()));
      chain = chain.thenCompose(v -> {
        CompletableFuture<?>[] futures = batch.stream()
          .map(instanceId -> rtacCacheGenerationService.generateRtacCache(instanceId.toString())
            .whenCompleteAsync((r, ex) -> handlePreWarmingCompletion(instanceId, ex), taskExecutor))
          .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
      });
    }

    return chain;
  }

  private void handlePreWarmingCompletion(UUID instanceId, Throwable ex) {
    if (ex != null) {
      log.error("Pre-warming failed for instanceId {}. Rolling back. Cause: {}", instanceId, ex.getMessage(), ex);
      try {
        rtacHoldingRepository.deleteAllByIdInstanceId(instanceId);
      } catch (Exception deleteEx) {
        log.error("Rollback delete failed for instanceId {}. Cause: {}", instanceId, deleteEx.getMessage(),
          deleteEx);
      }
    } else {
      log.info("Pre-warming completed for instanceId {}", instanceId);
    }
  }

  private RtacPreWarmingJob toResponse(RtacPreWarmingJobEntity entity) {
    var response = new RtacPreWarmingJob();
    response.setId(entity.getId());
    response.setStatus(StatusEnum.fromValue(entity.getStatus().name()));
    response.setStartDate(Date.from(entity.getStartDate()));
    if (entity.getEndDate() != null) {
      response.setEndDate(Date.from(entity.getEndDate()));
    }
    response.setErrorMessage(entity.getErrorMessage());
    return response;
  }
}
