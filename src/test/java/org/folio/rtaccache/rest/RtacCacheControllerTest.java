package org.folio.rtaccache.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rtaccache.TestConstant.TEST_TENANT;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.RtacHoldingId;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.folio.rtaccache.domain.dto.RtacHolding.TypeEnum;
import org.folio.rtaccache.domain.dto.RtacHoldings;
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
        var holdingId = UUID.randomUUID();
        var rtacHolding = new RtacHolding().id(holdingId.toString());
        var entity = new RtacHoldingEntity(new RtacHoldingId(instanceId, TypeEnum.HOLDING, holdingId), rtacHolding, Instant.now());
        repository.save(entity);

        var result = mockMvc.perform(get("/rtac-cache/" + instanceId + "?offset=0&limit=10")
                .headers(defaultHeaders(TEST_TENANT, APPLICATION_JSON)))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        var rtacHoldings = new ObjectMapper().readValue(result.getResponse().getContentAsString(), RtacHoldings.class);
        assertThat(rtacHoldings.getHoldings()).hasSize(1);
        assertThat(rtacHoldings.getHoldings().get(0).getId()).isEqualTo(holdingId.toString());
    }
}
