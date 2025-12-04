package org.folio.rtaccache;

import static org.folio.rtaccache.TestUtil.asString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import java.util.List;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.tenant.domain.dto.Parameter;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.contract.wiremock.WireMockSpring;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles({"test"})
public abstract class BaseIntegrationTest {
  private static final String MOD_OPAC_RTAC_MODULE = "mod-rtac-cache-1.0.0";
  protected static final String TOKEN = "test-token";

  protected static final WireMockServer WIRE_MOCK = new WireMockServer(
    WireMockSpring.options()
      .dynamicPort()
      .extensions(new ResponseTemplateTransformer(false)));

  private static final String IMAGE_VERSION = "postgres:16-alpine";
  private static final String KAFKA_IMAGE_VERSION = "confluentinc/cp-kafka:7.6.1";
  public static final ConfluentKafkaContainer kafkaContainer = new ConfluentKafkaContainer(DockerImageName.parse(KAFKA_IMAGE_VERSION));
  public static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>(IMAGE_VERSION);

  static {
    postgreSQLContainer.start();
    kafkaContainer.start();
    WIRE_MOCK.start();
  }

  @Autowired
  protected MockMvc mockMvc;

  @DynamicPropertySource
  static void dynamicProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
    registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
    registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
    registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    registry.add("folio.okapiUrl", WIRE_MOCK::baseUrl);
  }

  @BeforeAll
  static void beforeAll(@Autowired MockMvc mockMvc) throws Exception {
    setUpTenant(mockMvc);
  }

  protected static HttpHeaders defaultHeaders(String tenant, MediaType mediaType) {
    final HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setContentType(mediaType);
    httpHeaders.put(XOkapiHeaders.TENANT, List.of(tenant));
    httpHeaders.put(XOkapiHeaders.URL, List.of(WIRE_MOCK.baseUrl()));
    httpHeaders.put(XOkapiHeaders.TOKEN, List.of(TOKEN));
    httpHeaders.put(HttpHeaders.ACCEPT, List.of(mediaType.toString()));
    return httpHeaders;
  }

  protected static void setUpTenant(MockMvc mockMvc) throws Exception {
    var tenantAttributes = new TenantAttributes()
        .moduleTo(MOD_OPAC_RTAC_MODULE)
        .parameters(List.of(new Parameter().key("loadReference").value("true")));

    mockMvc.perform(post("/_/tenant")
        .content(asString(tenantAttributes))
        .headers(defaultHeaders(TestConstant.TEST_TENANT, APPLICATION_JSON))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }

}
