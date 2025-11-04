package org.folio.rtaccache.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rtaccache.TestConstant.TEST_TENANT;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.domain.dto.RtacHoldings;
import org.folio.rtaccache.domain.dto.RtacHoldingsBatch;
import org.folio.rtaccache.domain.dto.RtacRequest;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RtacCacheControllerTest extends BaseIntegrationTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private RtacHoldingRepository repository;

  @Test
  void holdingsByInstanceId_success() throws Exception {
    var instanceId = UUID.randomUUID();
    var holdingId1 = UUID.randomUUID();
    var holdingId2 = UUID.randomUUID();
    var rtacHolding1 = new RtacHolding().id(holdingId1.toString());
    var rtacHolding2 = new RtacHolding().id(holdingId2.toString());
    var entity1 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.HOLDING, holdingId1), rtacHolding1, Instant.now());
    var entity2 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.HOLDING, holdingId2), rtacHolding2, Instant.now());
    repository.save(entity1);
    repository.save(entity2);

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
    var instanceId = UUID.randomUUID();

    var result = mockMvc.perform(get("/rtac-cache/" + instanceId)
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
      .andDo(print())
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldings = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldings.class);
    assertThat(rtacHoldings.getHoldings()).isEmpty();
    assertThat(rtacHoldings.getTotalRecords()).isZero();
  }

  @Test
  void postRtacCacheBatch_success() throws Exception {
    var instanceId1 = UUID.randomUUID();
    var instanceId2 = UUID.randomUUID();
    var holdingId1 = UUID.randomUUID();
    var holdingId2 = UUID.randomUUID();

    var rtacHolding1 = new RtacHolding().id(holdingId1.toString());
    var rtacHolding2 = new RtacHolding().id(holdingId2.toString());

    var entity1 = new RtacHoldingEntity(new RtacHoldingId(instanceId1, TypeEnum.HOLDING, holdingId1), rtacHolding1, Instant.now());
    var entity2 = new RtacHoldingEntity(new RtacHoldingId(instanceId2, TypeEnum.HOLDING, holdingId2), rtacHolding2, Instant.now());

    repository.save(entity1);
    repository.save(entity2);

    var rtacRequest = new RtacRequest().instanceIds(List.of(instanceId1.toString(), instanceId2.toString()));

    var result = mockMvc.perform(post("/rtac-cache/batch")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON))
        .content(new ObjectMapper().writeValueAsString(rtacRequest)))
      .andDo(print())
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldingsBatch = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldingsBatch.class);
    assertThat(rtacHoldingsBatch.getHoldings()).hasSize(2);
    assertThat(rtacHoldingsBatch.getErrors()).isEmpty();
  }

  @Test
  void postRtacCacheBatch_withInvalidId() throws Exception {
    var validInstanceId = UUID.randomUUID();
    var invalidInstanceId = UUID.randomUUID();
    var holdingId = UUID.randomUUID();

    var rtacHolding = new RtacHolding().id(holdingId.toString());
    var entity = new RtacHoldingEntity(new RtacHoldingId(validInstanceId, TypeEnum.HOLDING, holdingId), rtacHolding, Instant.now());
    repository.save(entity);

    var rtacRequest = new RtacRequest().instanceIds(List.of(validInstanceId.toString(), invalidInstanceId.toString()));

    var result = mockMvc.perform(post("/rtac-cache/batch")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON))
        .content(new ObjectMapper().writeValueAsString(rtacRequest)))
      .andDo(print())
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldingsBatch = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldingsBatch.class);
    assertThat(rtacHoldingsBatch.getHoldings()).hasSize(1);
    assertThat(rtacHoldingsBatch.getErrors()).hasSize(1);
    assertThat(rtacHoldingsBatch.getErrors().getFirst().getMessage()).contains(invalidInstanceId.toString());
  }

  @Test
  void postRtacCacheBatch_withInvalidUuid() throws Exception {
    var rtacRequest = new RtacRequest().instanceIds(List.of("invalid-uuid"));

    mockMvc.perform(post("/rtac-cache/batch")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON))
        .content(new ObjectMapper().writeValueAsString(rtacRequest)))
      .andDo(print())
      .andExpect(status().isBadRequest());
  }

  @Test
  void postRtacCacheBatch_withMalformedJson() throws Exception {
    var malformedJson = "{\"instanceIds\": [\"" + UUID.randomUUID() + "\"]"; // Missing closing brace

    mockMvc.perform(post("/rtac-cache/batch")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON))
        .content(malformedJson))
      .andDo(print())
      .andExpect(status().isBadRequest());
  }

  @Test
  void postRtacCacheBatch_withEmptyList() throws Exception {
    var rtacRequest = new RtacRequest().instanceIds(List.of());

    var result = mockMvc.perform(post("/rtac-cache/batch")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON))
        .content(new ObjectMapper().writeValueAsString(rtacRequest)))
      .andDo(print())
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldingsBatch = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldingsBatch.class);
    assertThat(rtacHoldingsBatch.getHoldings()).isEmpty();
    assertThat(rtacHoldingsBatch.getErrors()).isEmpty();
  }

  @Test
  void postRtacCacheBatch_withEmptyBody() throws Exception {
    mockMvc.perform(post("/rtac-cache/batch")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON))
        .content(""))
      .andDo(print())
      .andExpect(status().isBadRequest());
  }
}
