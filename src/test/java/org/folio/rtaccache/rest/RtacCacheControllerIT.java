package org.folio.rtaccache.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rtaccache.TestConstant.TEST_TENANT;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.domain.dto.RtacHoldings;
import org.folio.rtaccache.domain.dto.RtacHoldingsBatch;
import org.folio.rtaccache.domain.dto.RtacRequest;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.rtaccache.service.RtacCacheGenerationService;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

class RtacCacheControllerIT extends BaseIntegrationTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private RtacHoldingRepository rtacHoldingRepository;
  @MockitoBean
  private RtacCacheGenerationService rtacCacheGenerationService;
  @MockitoSpyBean
  private FolioExecutionContext folioExecutionContext;

  @AfterEach
  void tearDown() {
    rtacHoldingRepository.deleteAll();
  }

  @Test
  void holdingsByInstanceId_success() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    var instanceId = UUID.randomUUID();
    var holdingId1 = UUID.randomUUID();
    var holdingId2 = UUID.randomUUID();
    var rtacHolding1 = new RtacHolding().id(holdingId1.toString());
    var rtacHolding2 = new RtacHolding().id(holdingId2.toString());
    var entity1 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.HOLDING, holdingId1), rtacHolding1, Instant.now());
    var entity2 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.HOLDING, holdingId2), rtacHolding2, Instant.now());
    rtacHoldingRepository.save(entity1);
    rtacHoldingRepository.save(entity2);

    var result = mockMvc.perform(get("/rtac-cache/" + instanceId + "?offset=0&limit=1")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldings = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldings.class);
    assertThat(rtacHoldings.getHoldings()).hasSize(1);
    assertThat(rtacHoldings.getTotalRecords()).isEqualTo(2);
  }

  @Test
  void holdingsByInstanceId_notFound() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    var instanceId = UUID.randomUUID();

    when(rtacCacheGenerationService.generateRtacCache(instanceId.toString())).thenReturn(CompletableFuture.completedFuture(null));
    var result = mockMvc.perform(get("/rtac-cache/" + instanceId)
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldings = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldings.class);
    assertThat(rtacHoldings.getHoldings()).isEmpty();
    assertThat(rtacHoldings.getTotalRecords()).isZero();
  }

  @Test
  void postRtacCacheBatch_success() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    var instanceId1 = UUID.randomUUID();
    var instanceId2 = UUID.randomUUID();
    var holdingId1 = UUID.randomUUID();
    var holdingId2 = UUID.randomUUID();

    var rtacHolding1 = new RtacHolding().id(holdingId1.toString());
    var rtacHolding2 = new RtacHolding().id(holdingId2.toString());

    var entity1 = new RtacHoldingEntity(new RtacHoldingId(instanceId1, TypeEnum.HOLDING, holdingId1), rtacHolding1, Instant.now());
    var entity2 = new RtacHoldingEntity(new RtacHoldingId(instanceId2, TypeEnum.HOLDING, holdingId2), rtacHolding2, Instant.now());

    rtacHoldingRepository.save(entity1);
    rtacHoldingRepository.save(entity2);

    var rtacRequest = new RtacRequest().instanceIds(List.of(instanceId1.toString(), instanceId2.toString()));

    var result = mockMvc.perform(post("/rtac-cache/batch")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON))
        .content(new ObjectMapper().writeValueAsString(rtacRequest)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldingsBatch = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldingsBatch.class);
    assertThat(rtacHoldingsBatch.getHoldings()).hasSize(2);
    assertThat(rtacHoldingsBatch.getErrors()).isEmpty();
  }

  @Test
  void postRtacCacheBatch_withInvalidId() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    var validInstanceId = UUID.randomUUID();
    var invalidInstanceId = UUID.randomUUID();
    var holdingId = UUID.randomUUID();

    var rtacHolding = new RtacHolding().id(holdingId.toString());
    var entity = new RtacHoldingEntity(new RtacHoldingId(validInstanceId, TypeEnum.HOLDING, holdingId), rtacHolding, Instant.now());
    rtacHoldingRepository.save(entity);

    var rtacRequest = new RtacRequest().instanceIds(List.of(validInstanceId.toString(), invalidInstanceId.toString()));

    var result = mockMvc.perform(post("/rtac-cache/batch")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON))
        .content(new ObjectMapper().writeValueAsString(rtacRequest)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldingsBatch = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldingsBatch.class);
    assertThat(rtacHoldingsBatch.getHoldings()).hasSize(1);
    assertThat(rtacHoldingsBatch.getErrors()).hasSize(1);

    var error = rtacHoldingsBatch.getErrors().get(0);
    assertThat(error.getMessage()).contains(invalidInstanceId.toString());
    assertThat(error.getParameters()).hasSize(1);

    var parameter = error.getParameters().get(0);
    assertThat(parameter.getKey()).isEqualTo("instanceId");
    assertThat(parameter.getValue()).isEqualTo(invalidInstanceId.toString());
  }

  @Test
  void postRtacCacheBatch_withInvalidUuid() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    var rtacRequest = new RtacRequest().instanceIds(List.of("invalid-uuid"));

    mockMvc.perform(post("/rtac-cache/batch")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON))
        .content(new ObjectMapper().writeValueAsString(rtacRequest)))
      .andExpect(status().isBadRequest());
  }

  @Test
  void postRtacCacheBatch_withMalformedJson() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    var malformedJson = "{\"instanceIds\": [\"" + UUID.randomUUID() + "\"]"; // Missing closing brace

    mockMvc.perform(post("/rtac-cache/batch")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON))
        .content(malformedJson))
      .andExpect(status().isBadRequest());
  }

  @Test
  void postRtacCacheBatch_withEmptyList() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    var rtacRequest = new RtacRequest().instanceIds(List.of());

    var result = mockMvc.perform(post("/rtac-cache/batch")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON))
        .content(new ObjectMapper().writeValueAsString(rtacRequest)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldingsBatch = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldingsBatch.class);
    assertThat(rtacHoldingsBatch.getHoldings()).isEmpty();
    assertThat(rtacHoldingsBatch.getErrors()).isEmpty();
  }

  @Test
  void postRtacCacheBatch_withEmptyBody() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    mockMvc.perform(post("/rtac-cache/batch")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON))
        .content(""))
      .andExpect(status().isBadRequest());
  }
}
