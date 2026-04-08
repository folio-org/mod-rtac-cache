package org.folio.rtaccache.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.rtaccache.TestConstant.EMPTY_INSTANCE_ID;
import static org.folio.rtaccache.TestConstant.TEST_TENANT;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.folio.rtaccache.domain.dto.RtacHoldingLibrary;
import org.folio.rtaccache.domain.dto.RtacHoldingLocation;
import org.folio.rtaccache.domain.dto.RtacHoldings;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class RtacCacheControllerSearchIT extends BaseIntegrationTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private RtacHoldingRepository rtacHoldingRepository;

  @AfterEach
  void tearDown() {
    withinTenant(TEST_TENANT, rtacHoldingRepository::deleteAll);
  }

  @Test
  void testSearchRtacHoldings() throws Exception {
    var instanceId1 = UUID.randomUUID();
    var instanceId2 = UUID.randomUUID();

    withinTenant(TEST_TENANT, () -> {
      rtacHoldingRepository.save(createRtacHoldingEntity(instanceId1, "vol1", "call1", "loc1", "lib1", "Available"));
      rtacHoldingRepository.save(createRtacHoldingEntity(instanceId1, "vol2", "call2", "loc2", "lib2", "Checked out"));
      rtacHoldingRepository.save(createRtacHoldingEntity(instanceId2, "vol3", "call3", "loc3", "lib3", "Available"));
    });

    var result = mockMvc.perform(get("/rtac-cache/search/{instanceId}", instanceId1)
        .param("query", "vol1 call1")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldings = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldings.class);
    assertThat(rtacHoldings.getHoldings()).hasSize(1);
    assertThat(rtacHoldings.getHoldings().getFirst().getVolume()).isEqualTo("vol1");
  }

  @Test
  void testSearchRtacHoldings_notFound() throws Exception {
    var result = mockMvc.perform(get("/rtac-cache/search/{instanceId}", EMPTY_INSTANCE_ID)
        .param("query", "test")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldings = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldings.class);
    assertThat(rtacHoldings.getHoldings()).isEmpty();
    assertThat(rtacHoldings.getTotalRecords()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "\t", "\n"})
  void searchRtacCacheHoldings_shouldReturn200_whenQueryIsBlankOrEmpty(String query) throws Exception {
    var instanceId = UUID.randomUUID();
    withinTenant(TEST_TENANT,
      () -> rtacHoldingRepository.save(createRtacHoldingEntity(instanceId, "vol1", "call1", "loc1", "lib1", "Available")));

    var result = mockMvc.perform(get("/rtac-cache/search/{instanceId}", instanceId)
        .param("query", query)
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldings = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldings.class);
    assertThat(rtacHoldings.getHoldings()).hasSize(1);
    assertThat(rtacHoldings.getTotalRecords()).isEqualTo(1);
  }

  @Test
  void testSearchRtacHoldings_withInvalidUuid() throws Exception {
    mockMvc.perform(get("/rtac-cache/search/{instanceId}", "invalid-uuid")
        .param("query", "test")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
      .andExpect(status().isBadRequest());
  }

  @Test
  void searchRtacCacheHoldings_defaultSort() throws Exception {
    var instanceId = UUID.randomUUID();

    withinTenant(TEST_TENANT, () -> rtacHoldingRepository.saveAll(
      List.of(
        createRtacHoldingEntity(instanceId, "v1", "c1", "Loc A", "Lib A", "Available", "A"),
        createRtacHoldingEntity(instanceId, "v2", "c2", "Loc 2", "Lib B", "Available", "B"),
        createRtacHoldingEntity(instanceId, "v3", "c3", "Loc 1", "Lib A", "Available", "B"),
        createRtacHoldingEntity(instanceId, "v4", "c4", "Loc Z", "Lib A", "Available", "A"),
        createRtacHoldingEntity(instanceId, "v5", "c5", "Loc X", "Lib B", "Available", "A"),
        createRtacHoldingEntity(instanceId, "v6", "c6", "Loc C", "Lib A", "Checked out", "A")
      )
    ));

    var result = mockMvc.perform(get("/rtac-cache/search/{instanceId}", instanceId)
        .param("query", " ")
        .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
      .andExpect(status().isOk())
      .andReturn();

    var rtacHoldings = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldings.class);
    assertThat(rtacHoldings.getHoldings())
      .extracting(
         RtacHolding::getEffectiveShelvingOrder,
         RtacHolding::getStatus,
        h -> h.getLibrary().getName(),
        h -> h.getLocation().getName()
      )
      .containsExactly(
        tuple("B", "Available", "Lib A", "Loc 1"),
        tuple("B", "Available", "Lib B", "Loc 2"),
        tuple("A", "Available", "Lib A", "Loc A"),
        tuple("A", "Available", "Lib A", "Loc Z"),
        tuple("A", "Available", "Lib B", "Loc X"),
        tuple("A", "Checked out", "Lib A", "Loc C")
      );
  }

  private RtacHoldingEntity createRtacHoldingEntity(UUID instanceId, String volume, String callNumber, String locationName, String libraryName, String status) {
    return createRtacHoldingEntity(instanceId, volume, callNumber, locationName, libraryName, status, null);
  }

  private RtacHoldingEntity createRtacHoldingEntity(UUID instanceId, String volume, String callNumber, String locationName,
                                                    String libraryName, String status, String effectiveShelvingOrder) {
    final var rtacHolding = new RtacHolding()
      .instanceId(instanceId.toString())
      .status(status)
      .volume(volume)
      .callNumber(callNumber)
      .effectiveShelvingOrder(effectiveShelvingOrder)
      .location(new RtacHoldingLocation().name(locationName))
      .library(new RtacHoldingLibrary().name(libraryName));
    return new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.ITEM, UUID.randomUUID()), false, rtacHolding, Instant.now());
  }
}
