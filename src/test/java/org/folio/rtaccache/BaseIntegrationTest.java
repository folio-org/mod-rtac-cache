package org.folio.rtaccache;

import static org.folio.rtaccache.TestUtil.asString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "testcontainers-pg"})
public abstract class BaseIntegrationTest {
  private static final String MOD_OPAC_RTAC_MODULE = "mod-rtac-cache-1.0.0";
  protected static final String TOKEN = "test-token";

  @BeforeAll
  static void beforeAll(@Autowired MockMvc mockMvc) throws Exception {

    setUpTenant(mockMvc);
  }


  protected static HttpHeaders defaultHeaders(String tenant, MediaType mediaType) {
    final HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setContentType(mediaType);
    httpHeaders.put(XOkapiHeaders.TENANT, List.of(tenant));
    httpHeaders.put(XOkapiHeaders.URL, List.of("http://localhost"));
    httpHeaders.put(XOkapiHeaders.TOKEN, List.of(TOKEN));
    httpHeaders.put(HttpHeaders.ACCEPT, List.of(mediaType.toString()));
    return httpHeaders;
  }

  protected static void setUpTenant(MockMvc mockMvc) throws Exception {
    mockMvc.perform(post("/_/tenant")
        .content(asString(new TenantAttributes().moduleTo(MOD_OPAC_RTAC_MODULE)))
        .headers(defaultHeaders(TestConstant.TEST_TENANT, APPLICATION_JSON))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }

}
