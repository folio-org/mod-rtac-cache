package org.folio.rtaccache;

import static org.folio.rtaccache.TestUtil.asString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.folio.tenant.domain.dto.Parameter;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

public class BaseEcsIntegrationTest extends BaseIntegrationTest{

  @BeforeAll
  static void beforeAll(@Autowired MockMvc mockMvc) throws Exception {
    setUpTenants(mockMvc);
  }

  protected static void setUpTenants(MockMvc mockMvc) throws Exception {
    var tenantAttributes = new TenantAttributes()
      .moduleTo(MOD_OPAC_RTAC_MODULE)
      .parameters(List.of(new Parameter().key("loadReference").value("true")));

    mockMvc.perform(post("/_/tenant")
        .content(asString(tenantAttributes))
        .headers(defaultHeaders(TestConstant.TEST_CENTRAL_TENANT, APPLICATION_JSON))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());

    mockMvc.perform(post("/_/tenant")
        .content(asString(tenantAttributes))
        .headers(defaultHeaders(TestConstant.TEST_MEMBER_TENANT, APPLICATION_JSON))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }
}
