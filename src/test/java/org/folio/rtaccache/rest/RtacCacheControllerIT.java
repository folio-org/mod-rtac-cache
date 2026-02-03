package org.folio.rtaccache.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.rtaccache.TestConstant.EMPTY_INSTANCE_ID;
import static org.folio.rtaccache.TestConstant.FAILING_INSTANCE_ID;
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
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.domain.dto.RtacHoldingLibrary;
import org.folio.rtaccache.domain.dto.RtacHoldingLocation;
import org.folio.rtaccache.domain.dto.RtacHoldings;
import org.folio.rtaccache.domain.dto.RtacHoldingsBatch;
import org.folio.rtaccache.domain.dto.RtacRequest;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

class RtacCacheControllerIT extends BaseIntegrationTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private RtacHoldingRepository rtacHoldingRepository;
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
    var entity1 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.HOLDING, holdingId1), false, rtacHolding1, Instant.now());
    var entity2 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.HOLDING, holdingId2), false, rtacHolding2, Instant.now());
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
    var instanceId = EMPTY_INSTANCE_ID;

    var result = mockMvc.perform(get("/rtac-cache/" + instanceId)
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldings = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldings.class);
    assertThat(rtacHoldings.getHoldings()).isEmpty();
    assertThat(rtacHoldings.getTotalRecords()).isZero();
  }

  @Test
  void holdingsByInstanceId_notFoundAndFailedToGenerate() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    mockMvc.perform(get("/rtac-cache/" + FAILING_INSTANCE_ID)
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
      .andExpect(status().isInternalServerError());
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

    var entity1 = new RtacHoldingEntity(new RtacHoldingId(instanceId1, TypeEnum.HOLDING, holdingId1), false, rtacHolding1, Instant.now());
    var entity2 = new RtacHoldingEntity(new RtacHoldingId(instanceId2, TypeEnum.HOLDING, holdingId2), false, rtacHolding2, Instant.now());

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
    var invalidInstanceId = UUID.fromString(EMPTY_INSTANCE_ID);
    var holdingId = UUID.randomUUID();

    var rtacHolding = new RtacHolding().id(holdingId.toString());
    var entity = new RtacHoldingEntity(new RtacHoldingId(validInstanceId, TypeEnum.HOLDING, holdingId), false, rtacHolding, Instant.now());
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
  void postRtacCacheBatch_withEmptyList_shouldReturnBadRequest() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    var rtacRequest = new RtacRequest().instanceIds(List.of());

    mockMvc.perform(post("/rtac-cache/batch")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON))
        .content(new ObjectMapper().writeValueAsString(rtacRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void holdingsByInstanceId_withSorting() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    when(folioExecutionContext.getOkapiUrl()).thenReturn(WIRE_MOCK.baseUrl());

    var instanceId = UUID.randomUUID();

    var rtacHolding1 = new RtacHolding().effectiveShelvingOrder("B").library(new RtacHoldingLibrary().name("Library Z"));
    var rtacHolding2 = new RtacHolding().effectiveShelvingOrder("C").library(new RtacHoldingLibrary().name("Library A"));
    var rtacHolding3 = new RtacHolding().effectiveShelvingOrder("A").library(new RtacHoldingLibrary().name("Library M"));

    var entity1 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.ITEM, UUID.randomUUID()), false, rtacHolding1, Instant.now());
    var entity2 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.ITEM, UUID.randomUUID()), false, rtacHolding2, Instant.now());
    var entity3 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.ITEM, UUID.randomUUID()), false, rtacHolding3, Instant.now());

    rtacHoldingRepository.saveAll(List.of(entity1, entity2, entity3));

    // Sort by effectiveShelvingOrder ascending
    var resultAsc = mockMvc.perform(get("/rtac-cache/" + instanceId + "?sort=effectiveShelvingOrder")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldingsAsc = new ObjectMapper().readValue(resultAsc.getResponse().getContentAsString(), RtacHoldings.class);
    assertThat(rtacHoldingsAsc.getHoldings()).extracting(RtacHolding::getEffectiveShelvingOrder).containsExactly("A", "B", "C");

    // Sort by libraryName descending
    var resultDesc = mockMvc.perform(get("/rtac-cache/" + instanceId + "?sort=libraryName,desc")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldingsDesc = new ObjectMapper().readValue(resultDesc.getResponse().getContentAsString(), RtacHoldings.class);
    assertThat(rtacHoldingsDesc.getHoldings()).extracting(h -> {
      Assertions.assertNotNull(h.getLibrary());
      return h.getLibrary().getName();
    }).containsExactly("Library Z", "Library M", "Library A");
  }

  @Test
  void holdingsByInstanceId_withMultiFieldSorting() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    when(folioExecutionContext.getOkapiUrl()).thenReturn(WIRE_MOCK.baseUrl());

    var instanceId = UUID.randomUUID();

    var rtacHolding1 = new RtacHolding().effectiveShelvingOrder("A").status("Z");
    var rtacHolding2 = new RtacHolding().effectiveShelvingOrder("A").status("M");
    var rtacHolding3 = new RtacHolding().effectiveShelvingOrder("B").status("A");

    var entity1 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.ITEM, UUID.randomUUID()), false, rtacHolding1, Instant.now());
    var entity2 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.ITEM, UUID.randomUUID()), false, rtacHolding2, Instant.now());
    var entity3 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.ITEM, UUID.randomUUID()), false, rtacHolding3, Instant.now());

    rtacHoldingRepository.saveAll(List.of(entity3, entity1, entity2));

    var resultMulti = mockMvc.perform(get("/rtac-cache/" + instanceId + "?sort=effectiveShelvingOrder,asc,status,desc")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldingsMulti = new ObjectMapper().readValue(resultMulti.getResponse().getContentAsString(), RtacHoldings.class);
    assertThat(rtacHoldingsMulti.getHoldings())
      .extracting(RtacHolding::getEffectiveShelvingOrder, RtacHolding::getStatus)
      .containsExactly(
        tuple("A", "Z"),
        tuple("A", "M"),
        tuple("B", "A")
      );
  }

  @Test
  void holdingsByInstanceId_defaultSort() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    when(folioExecutionContext.getOkapiUrl()).thenReturn(WIRE_MOCK.baseUrl());

    var instanceId = UUID.randomUUID();

    var rtacHoldingD =  new RtacHolding().effectiveShelvingOrder("A").status("Available").location(new RtacHoldingLocation().name("X"));
    var rtacHoldingE = new RtacHolding().effectiveShelvingOrder("A").status("Available").location(new RtacHoldingLocation().name("M"));
    var rtacHoldingF = new RtacHolding().effectiveShelvingOrder("A").status("Checked out").location(new RtacHoldingLocation().name("A"));
    var rtacHoldingG = new RtacHolding().effectiveShelvingOrder("B").status("Available").location(new RtacHoldingLocation().name("A"));

    var entityD = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.ITEM, UUID.randomUUID()), false, rtacHoldingD, Instant.now());
    var entityE = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.ITEM, UUID.randomUUID()), false, rtacHoldingE, Instant.now());
    var entityF = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.ITEM, UUID.randomUUID()), false, rtacHoldingF, Instant.now());
    var entityG = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.ITEM, UUID.randomUUID()), false, rtacHoldingG, Instant.now());

    rtacHoldingRepository.saveAll(List.of(entityD, entityE, entityF, entityG));

    var result = mockMvc.perform(get("/rtac-cache/" + instanceId)
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldings = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldings.class);
    assertThat(rtacHoldings.getHoldings())
      .extracting(RtacHolding::getEffectiveShelvingOrder, RtacHolding::getStatus, h -> {
        Assertions.assertNotNull(h.getLocation());
        return h.getLocation().getName();
      })
      .containsExactly(
        tuple("A", "Available", "M"),
        tuple("A", "Available", "X"),
        tuple("A", "Checked out", "A"),
        tuple("B", "Available", "A")
      );
  }

  @Test
  void holdingsByInstanceId_shouldSkipSuppressedItems() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    when(folioExecutionContext.getOkapiUrl()).thenReturn(WIRE_MOCK.baseUrl());

    var instanceId = UUID.randomUUID();
    var holdingId1 = UUID.randomUUID();
    var holdingId2 = UUID.randomUUID();

    var rtacHolding1 = new RtacHolding().id(holdingId1.toString()).suppressFromDiscovery(false);
    var rtacHolding2 = new RtacHolding().id(holdingId2.toString()).suppressFromDiscovery(true);

    var entity1 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.HOLDING, holdingId1), false, rtacHolding1, Instant.now());
    var entity2 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.HOLDING, holdingId2), false, rtacHolding2, Instant.now());
    rtacHoldingRepository.save(entity1);
    rtacHoldingRepository.save(entity2);

    var result = mockMvc.perform(get("/rtac-cache/" + instanceId)
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldings = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldings.class);
    assertThat(rtacHoldings.getHoldings()).hasSize(1);
    assertThat(rtacHoldings.getHoldings().get(0).getId()).isEqualTo(holdingId1.toString());
  }

  @Test
  void holdingsByInstanceId_shouldSkipNotSuppressedItemsIfCorrespondingHoldingIsSuppressed() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    when(folioExecutionContext.getOkapiUrl()).thenReturn(WIRE_MOCK.baseUrl());

    var instanceId = UUID.randomUUID();
    var holdingId1 = UUID.randomUUID();
    var holdingId2 = UUID.randomUUID();

    var rtacHolding1 = new RtacHolding().id(holdingId1.toString()).suppressFromDiscovery(true);
    var rtacHolding2 = new RtacHolding().id(holdingId2.toString()).holdingsId(holdingId1.toString()).suppressFromDiscovery(false);

    var entity1 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.HOLDING, holdingId1), false, rtacHolding1, Instant.now());
    var entity2 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.ITEM, holdingId2), false, rtacHolding2, Instant.now());
    rtacHoldingRepository.save(entity1);
    rtacHoldingRepository.save(entity2);

    var result = mockMvc.perform(get("/rtac-cache/" + instanceId)
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldings = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldings.class);
    assertThat(rtacHoldings.getHoldings()).hasSize(0);
  }

  @Test
  void holdingsByInstanceId_shouldReturnItemsIfSuppressFromDiscoveryIsNull() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);
    when(folioExecutionContext.getOkapiUrl()).thenReturn(WIRE_MOCK.baseUrl());

    var instanceId = UUID.randomUUID();
    var holdingId1 = UUID.randomUUID();
    var holdingId2 = UUID.randomUUID();

    var rtacHolding1 = new RtacHolding().id(holdingId1.toString()).suppressFromDiscovery(null);
    var rtacHolding2 = new RtacHolding().id(holdingId2.toString()).holdingsId(holdingId1.toString()).suppressFromDiscovery(null);

    var entity1 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.HOLDING, holdingId1), false, rtacHolding1, Instant.now());
    var entity2 = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.ITEM, holdingId2), false, rtacHolding2, Instant.now());
    rtacHoldingRepository.save(entity1);
    rtacHoldingRepository.save(entity2);

    var result = mockMvc.perform(get("/rtac-cache/" + instanceId)
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldings = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldings.class);
    assertThat(rtacHoldings.getHoldings()).hasSize(1);
    assertThat(rtacHoldings.getHoldings().get(0).getId()).isEqualTo(holdingId2.toString());
  }

  @Test
  void postRtacCacheBatch_withEmptyBody() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    mockMvc.perform(post("/rtac-cache/batch")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON))
        .content(""))
      .andExpect(status().isBadRequest());
  }

  @Test
  void postRtacCacheInvalidate_success() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    var instanceId1 = UUID.randomUUID();
    var instanceId2 = UUID.randomUUID();
    var instanceId3 = UUID.randomUUID();
    var holdingId1 = UUID.randomUUID();
    var holdingId2 = UUID.randomUUID();
    var holdingId3 = UUID.randomUUID();

    var rtacHolding1 = new RtacHolding().id(holdingId1.toString());
    var rtacHolding2 = new RtacHolding().id(holdingId2.toString());
    var rtacHolding3 = new RtacHolding().id(holdingId3.toString());

    var entity1 = new RtacHoldingEntity(new RtacHoldingId(instanceId1, TypeEnum.HOLDING, holdingId1), false, rtacHolding1, Instant.now());
    var entity2 = new RtacHoldingEntity(new RtacHoldingId(instanceId2, TypeEnum.HOLDING, holdingId2), false, rtacHolding2, Instant.now());
    var entity3 = new RtacHoldingEntity(new RtacHoldingId(instanceId3, TypeEnum.HOLDING, holdingId3), false, rtacHolding3, Instant.now());

    rtacHoldingRepository.save(entity1);
    rtacHoldingRepository.save(entity2);
    rtacHoldingRepository.save(entity3);

    assertThat(rtacHoldingRepository.countByIdInstanceId(instanceId1)).isEqualTo(1);
    assertThat(rtacHoldingRepository.countByIdInstanceId(instanceId2)).isEqualTo(1);
    assertThat(rtacHoldingRepository.countByIdInstanceId(instanceId3)).isEqualTo(1);

    var rtacRequest = new RtacRequest().instanceIds(List.of(instanceId1.toString(), instanceId2.toString()));

    mockMvc.perform(post("/rtac-cache/invalidate")
            .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON))
            .content(new ObjectMapper().writeValueAsString(rtacRequest)))
        .andExpect(status().isNoContent());

    assertThat(rtacHoldingRepository.countByIdInstanceId(instanceId1)).isZero();
    assertThat(rtacHoldingRepository.countByIdInstanceId(instanceId2)).isZero();
    assertThat(rtacHoldingRepository.countByIdInstanceId(instanceId3)).isEqualTo(1);
  }

  @Test
  void postRtacCacheInvalidate_withEmptyList_shouldReturnBadRequest() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    var rtacRequest = new RtacRequest().instanceIds(List.of());

    mockMvc.perform(post("/rtac-cache/invalidate")
            .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON))
            .content(new ObjectMapper().writeValueAsString(rtacRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void postRtacCacheInvalidateAll_success() throws Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TestConstant.TEST_TENANT);

    var instanceId1 = UUID.randomUUID();
    var instanceId2 = UUID.randomUUID();
    var instanceId3 = UUID.randomUUID();
    var holdingId1 = UUID.randomUUID();
    var holdingId2 = UUID.randomUUID();
    var holdingId3 = UUID.randomUUID();

    var rtacHolding1 = new RtacHolding().id(holdingId1.toString());
    var rtacHolding2 = new RtacHolding().id(holdingId2.toString());
    var rtacHolding3 = new RtacHolding().id(holdingId3.toString());

    var entity1 = new RtacHoldingEntity(new RtacHoldingId(instanceId1, TypeEnum.HOLDING, holdingId1), false, rtacHolding1, Instant.now());
    var entity2 = new RtacHoldingEntity(new RtacHoldingId(instanceId2, TypeEnum.HOLDING, holdingId2), false, rtacHolding2, Instant.now());
    var entity3 = new RtacHoldingEntity(new RtacHoldingId(instanceId3, TypeEnum.HOLDING, holdingId3), false, rtacHolding3, Instant.now());

    rtacHoldingRepository.save(entity1);
    rtacHoldingRepository.save(entity2);
    rtacHoldingRepository.save(entity3);

    assertThat(rtacHoldingRepository.count()).isEqualTo(3);

    mockMvc.perform(post("/rtac-cache/invalidate-all")
            .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
        .andExpect(status().isNoContent());

    assertThat(rtacHoldingRepository.count()).isZero();
  }
}
