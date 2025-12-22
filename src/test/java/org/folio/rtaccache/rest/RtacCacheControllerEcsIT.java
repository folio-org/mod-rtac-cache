package org.folio.rtaccache.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rtaccache.TestConstant.ECS_INSTANCE1_ID;
import static org.folio.rtaccache.TestConstant.TEST_CENTRAL_TENANT;
import static org.folio.rtaccache.TestConstant.TEST_MEMBER_TENANT;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.folio.rtaccache.BaseEcsIntegrationTest;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.domain.dto.RtacHoldings;
import org.folio.rtaccache.domain.dto.RtacHoldingsBatch;
import org.folio.rtaccache.domain.dto.RtacRequest;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

class RtacCacheControllerEcsIT extends BaseEcsIntegrationTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private RtacHoldingRepository rtacHoldingRepository;
  @Autowired
  private ObjectMapper objectMapper;
  @MockitoSpyBean
  private FolioExecutionContext folioExecutionContext;

  @AfterEach
  void tearDown()
  {
    when(folioExecutionContext.getTenantId()).thenReturn(TEST_MEMBER_TENANT);
    rtacHoldingRepository.deleteAll();
    Mockito.reset(folioExecutionContext);
  }

  @Test
  void ecsHoldingsByInstanceId_success() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TEST_MEMBER_TENANT);

    var instanceId = UUID.fromString(ECS_INSTANCE1_ID);
    var holdingId1 = UUID.randomUUID();
    var holdingId2 = UUID.randomUUID();
    var rtacHolding1 = new RtacHolding().id(holdingId1.toString());
    var rtacHolding2 = new RtacHolding().id(holdingId2.toString());
    var entity1 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.HOLDING, holdingId1), true, rtacHolding1, Instant.now());
    var entity2 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.HOLDING, holdingId2), false, rtacHolding2, Instant.now());
    rtacHoldingRepository.save(entity1);
    rtacHoldingRepository.save(entity2);

    Mockito.reset(folioExecutionContext);

    var result = mockMvc.perform(get("/rtac-cache/" + instanceId + "?offset=0&limit=1")
        .headers(defaultHeaders(TEST_CENTRAL_TENANT, APPLICATION_JSON)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldings = objectMapper.readValue(result.getResponse().getContentAsString(), RtacHoldings.class);
    assertThat(rtacHoldings.getHoldings()).hasSize(1);
    assertThat(rtacHoldings.getTotalRecords()).isEqualTo(1);
  }

  @Test
  void ecsPostRtacCacheBatch_success() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TEST_MEMBER_TENANT);

    var instanceId1 = UUID.fromString(ECS_INSTANCE1_ID);
    var instanceId2 = UUID.randomUUID(); // Not shared instance
    var holdingId1 = UUID.randomUUID();
    var holdingId2 = UUID.randomUUID();

    var rtacHolding1 = new RtacHolding().id(holdingId1.toString());
    var rtacHolding2 = new RtacHolding().id(holdingId2.toString());

    var entity1 = new RtacHoldingEntity(new RtacHoldingId(instanceId1, TypeEnum.HOLDING, holdingId1), true, rtacHolding1, Instant.now());
    var entity2 = new RtacHoldingEntity(new RtacHoldingId(instanceId2, TypeEnum.HOLDING, holdingId2), false, rtacHolding2, Instant.now());

    rtacHoldingRepository.save(entity1);
    rtacHoldingRepository.save(entity2);

    Mockito.reset(folioExecutionContext);

    var rtacRequest = new RtacRequest().instanceIds(List.of(instanceId1.toString(), instanceId2.toString()));

    var result = mockMvc.perform(post("/rtac-cache/batch")
        .headers(defaultHeaders(TEST_CENTRAL_TENANT, APPLICATION_JSON))
        .content(new ObjectMapper().writeValueAsString(rtacRequest)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldingsBatch = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldingsBatch.class);
    assertThat(rtacHoldingsBatch.getHoldings()).hasSize(1);
    assertThat(rtacHoldingsBatch.getErrors()).hasSize(1);
  }

  @Test
  void ecsSearchRtacHoldings_success() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TEST_MEMBER_TENANT);

    var instanceId = UUID.fromString(ECS_INSTANCE1_ID);
    var holdingId1 = UUID.randomUUID();
    var holdingId2 = UUID.randomUUID();
    var rtacHolding1 = new RtacHolding().id(holdingId1.toString()).volume("vol1");
    var rtacHolding2 = new RtacHolding().id(holdingId2.toString()).volume("vol1");
    var entity1 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.HOLDING, holdingId1), true, rtacHolding1, Instant.now());
    var entity2 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.HOLDING, holdingId2), false, rtacHolding2, Instant.now());

    rtacHoldingRepository.save(entity1);
    rtacHoldingRepository.save(entity2);

    Mockito.reset(folioExecutionContext);

    var result = mockMvc.perform(get("/rtac-cache/search/{instanceId}", instanceId)
        .param("query", "vol1")
        .headers(defaultHeaders(TEST_CENTRAL_TENANT, APPLICATION_JSON)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldings = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldings.class);
    assertThat(rtacHoldings.getHoldings()).hasSize(1);
    assertThat(rtacHoldings.getHoldings().getFirst().getVolume()).isEqualTo("vol1");
  }

}
