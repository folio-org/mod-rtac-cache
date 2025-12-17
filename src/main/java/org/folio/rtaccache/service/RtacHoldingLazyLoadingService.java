package org.folio.rtaccache.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RtacHoldingLazyLoadingService {

  private final RtacHoldingRepository rtacHoldingRepository;
  private final RtacCacheGenerationService rtacCacheGenerationService;

  public void lazyLoadRtacHoldings(UUID instanceId) {
    if (rtacHoldingRepository.countByIdInstanceId(instanceId) == 0) {
      var future = rtacCacheGenerationService.generateRtacCache(instanceId.toString());
      try {
        future.join();
      } catch (Exception ex) {
        log.error("RTAC cache generation failed for instanceId: {}", instanceId, ex);
        rtacHoldingRepository.deleteAllByIdInstanceId(instanceId);
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          String.format("RTAC cache generation failed for instanceId: %s", instanceId));
      }
    }
  }

  public void lazyLoadRtacHoldingsEcs(UUID instanceId) {
    if (rtacHoldingRepository.countByIdInstanceIdAndType(instanceId, TypeEnum.HOLDING, true) == 0) {
      var future = rtacCacheGenerationService.generateRtacCache(instanceId.toString());
      try {
        future.join();
      } catch (Exception ex) {
        log.error("RTAC cache generation failed for instanceId: {}", instanceId, ex);
        rtacHoldingRepository.deleteAllByIdInstanceId(instanceId);
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          String.format("RTAC cache generation failed for instanceId: %s", instanceId));
      }
    }
  }


}
