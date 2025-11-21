package org.folio.rtaccache.service;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.folio.rtaccache.SameThreadAsyncTaskExecutor;
import org.folio.rtaccache.domain.RtacPreWarmingJobEntity;
import org.folio.rtaccache.domain.RtacPreWarmingJobEntity.JobStatus;
import org.folio.rtaccache.domain.dto.RtacPreWarmingJob;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.repository.RtacPreWarmingJobRepository;
import org.folio.spring.data.OffsetRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RtacCachePreWarmingServiceTest {

  @Mock
  private RtacCacheGenerationService rtacCacheGenerationService;
  @Mock
  private RtacHoldingRepository rtacHoldingRepository;
  @Mock
  private RtacPreWarmingJobRepository rtacPreWarmingJobRepository;
  @Spy
  private final AsyncTaskExecutor taskExecutor = new SameThreadAsyncTaskExecutor();
  @InjectMocks
  private RtacCachePreWarmingService service;

  @Test
  void getPreWarmingJobStatus_found() {
    var id = UUID.randomUUID();
    var entity = new RtacPreWarmingJobEntity();
    entity.setId(id);
    entity.setStatus(JobStatus.COMPLETED);
    entity.setEndDate(Instant.now());
    when(rtacPreWarmingJobRepository.findById(id)).thenReturn(Optional.of(entity));

    RtacPreWarmingJob dto = service.getPreWarmingJobStatus(id);

    assertEquals(id, dto.getId());
    assertEquals(RtacPreWarmingJob.StatusEnum.COMPLETED, dto.getStatus());
    verify(rtacPreWarmingJobRepository).findById(id);
  }

  @Test
  void getPreWarmingJobStatus_notFound() {
    var id = UUID.randomUUID();
    when(rtacPreWarmingJobRepository.findById(id)).thenReturn(Optional.empty());
    ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getPreWarmingJobStatus(id));
    assertEquals(404, ex.getStatusCode().value());
  }

  @Test
  void getPreWarmingJobs_mapping() {
    var e1 = new RtacPreWarmingJobEntity();
    e1.setId(UUID.randomUUID());
    e1.setStatus(JobStatus.FAILED);
    e1.setErrorMessage("err");
    var e2 = new RtacPreWarmingJobEntity();
    e2.setId(UUID.randomUUID());
    e2.setStatus(JobStatus.RUNNING);
    when(rtacPreWarmingJobRepository.findAllByOrderByStartDateDesc(any()))
      .thenReturn(new PageImpl<>(List.of(e1, e2)));

    var page = service.getPreWarmingJobs(new OffsetRequest(0, 10));

    assertEquals(2, page.getTotalElements());
    assertEquals(RtacPreWarmingJob.StatusEnum.FAILED, page.getContent().get(0).getStatus());
    assertEquals(RtacPreWarmingJob.StatusEnum.RUNNING, page.getContent().get(1).getStatus());
  }

  @Test
  void submitPreWarmingJob_success() {
    when(rtacCacheGenerationService.generateRtacCache(anyString()))
      .thenReturn(CompletableFuture.completedFuture(null));
    when(rtacPreWarmingJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = service.submitPreWarmingJob(List.of(UUID.randomUUID(), UUID.randomUUID()));

    assertNotNull(result.getId());
    RtacPreWarmingJobEntity captured = captureJobEntity(result.getId());

    assertEquals(JobStatus.COMPLETED, captured.getStatus());
    assertNotNull(captured.getEndDate());
    verify(rtacCacheGenerationService, times(2)).generateRtacCache(anyString());
    verify(rtacPreWarmingJobRepository, atLeast(2)).save(any());
  }

  @Test
  void submitPreWarmingJob_failureRollback() {
    UUID successInstanceId = UUID.randomUUID();
    UUID failedInstanceId = UUID.randomUUID();
    when(rtacCacheGenerationService.generateRtacCache(successInstanceId.toString()))
      .thenReturn(CompletableFuture.completedFuture(null));
    when(rtacCacheGenerationService.generateRtacCache(failedInstanceId.toString()))
      .thenReturn(CompletableFuture.failedFuture(new RuntimeException("failed")));
    when(rtacPreWarmingJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = service.submitPreWarmingJob(List.of(successInstanceId, failedInstanceId));

    RtacPreWarmingJobEntity job = captureJobEntity(result.getId());

    assertEquals(JobStatus.FAILED, job.getStatus());
    assertEquals("failed", job.getErrorMessage());
    verify(rtacHoldingRepository).deleteAllByIdInstanceId(failedInstanceId);
  }

  private RtacPreWarmingJobEntity captureJobEntity(UUID id) {
    ArgumentCaptor<RtacPreWarmingJobEntity> captor = ArgumentCaptor.forClass(RtacPreWarmingJobEntity.class);
    verify(rtacPreWarmingJobRepository, atLeastOnce()).save(captor.capture());
    return captor.getAllValues().stream().filter(e -> e.getId().equals(id)).reduce((first, second) -> second).orElseThrow();
  }

}
