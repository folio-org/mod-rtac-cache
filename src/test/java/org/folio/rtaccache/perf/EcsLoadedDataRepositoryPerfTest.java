package org.folio.rtaccache.perf;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.folio.rtaccache.BaseEcsIntegrationTest;
import org.folio.rtaccache.TestConstant;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EcsLoadedDataRepositoryPerfTest extends BaseEcsIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(EcsLoadedDataRepositoryPerfTest.class);

  @Autowired
  private javax.sql.DataSource dataSource;

  @Autowired
  private RtacHoldingRepository rtacHoldingRepository;

  @Autowired
  private MockMvc mockMvc;

  private UUID instanceId;
  private String schemasParam;

  @BeforeAll
  void seed() throws Exception {
    // Ensure we have multiple member tenants to simulate ECS fan-out.
    var memberTenants = List.of(
      TestConstant.TEST_MEMBER_TENANT,
      "perfMember2",
      "perfMember3",
      "perfMember4"
    );

    for (String tenant : memberTenants.subList(1, memberTenants.size())) {
      setUpTenant(mockMvc, tenant);
    }

    instanceId = UUID.fromString(System.getProperty("perf.instanceId", "11111111-1111-1111-1111-111111111111"));
    int itemsTotal = getIntProperty("perf.itemsTotal", 10_000);
    int itemsPerTenant = Math.max(1, itemsTotal / memberTenants.size());

    for (int i = 0; i < memberTenants.size(); i++) {
      String tenantId = memberTenants.get(i);
      int tenantOrdinal = i + 1;
      int itemCount = itemsPerTenant + (i == 0 ? (itemsTotal - itemsPerTenant * memberTenants.size()) : 0);
      withinTenant(tenantId, () -> {
        try (Connection connection = dataSource.getConnection()) {
          RtacPerfTenantSeeder.resetAndSeed(connection, RtacPerfTenantSeeder.SeedOptions.defaults(instanceId, tenantOrdinal, itemCount));
        }
      });
    }

    // Perf tests call the repository directly; make sure the multi-tenant function exists in the central tenant schema.
    withinTenant(TestConstant.TEST_CENTRAL_TENANT, () -> {
      try (Connection connection = dataSource.getConnection()) {
        RtacPerfDbObjects.ensureMultiTenantFunction(connection);
      }
    });

    schemasParam = String.join(",",
      memberTenants.stream().map(folioModuleMetadata::getDBSchemaName).toList()
    );

    log.info("RTAC PERF seeded instanceId={}, memberTenants={}, itemsTotal={}", instanceId, memberTenants.size(), itemsTotal);
  }

  @Test
  void perf_getRtacCacheHoldingsById_pageQuery() {
    var pageable = PageRequest.of(0, 100,
      Sort.by(
        Sort.Order.desc("effectiveShelvingOrder"),
        Sort.Order.asc("status"),
        Sort.Order.asc("libraryName"),
        Sort.Order.asc("locationName")
      )
    );

    withinTenant(TestConstant.TEST_CENTRAL_TENANT, () -> {
      // Warm-up
      rtacHoldingRepository.findAllByIdInstanceIdSingleQuery(schemasParam, instanceId, true, pageable)
        .getContent();

      long minMs = measureMinMs(5, () -> rtacHoldingRepository
        .findAllByIdInstanceIdSingleQuery(schemasParam, instanceId, true, pageable)
        .getContent());

      log.info("RTAC PERF GET pageQuery (defaultSort=jsonKeys) minMs={}", minMs);

      assertOptionalMaxMs("perf.maxGetMs", minMs);
    });
  }

  @Test
  void perf_getRtacCacheHoldingsById_pageQuery_cheapSort() {
    // Control experiment: same page query but with an index-friendly deterministic sort.
    var pageable = PageRequest.of(0, 100, Sort.by(Sort.Order.asc("type"), Sort.Order.asc("id")));

    withinTenant(TestConstant.TEST_CENTRAL_TENANT, () -> {
      // Warm-up
      rtacHoldingRepository.findAllByIdInstanceIdSingleQuery(schemasParam, instanceId, true, pageable)
        .getContent();

      long minMs = measureMinMs(5, () -> rtacHoldingRepository
        .findAllByIdInstanceIdSingleQuery(schemasParam, instanceId, true, pageable)
        .getContent());

      log.info("RTAC PERF GET pageQuery (cheapSort=type,id) minMs={}", minMs);
    });
  }

  @Test
  void perf_postRtacCacheBatch_holdingsSummaryQuery() {
    UUID[] instanceIds = {instanceId};

    withinTenant(TestConstant.TEST_CENTRAL_TENANT, () -> {
      // Warm-up
      var warmup = rtacHoldingRepository.findRtacSummariesByInstanceIds(schemasParam, instanceIds, true);
      assertThat(warmup).isNotNull();

      long minMs = measureMinMs(5, () -> rtacHoldingRepository
        .findRtacSummariesByInstanceIds(schemasParam, instanceIds, true));

      log.info("RTAC PERF BATCH summaryQuery minMs={}", minMs);

      assertOptionalMaxMs("perf.maxBatchMs", minMs);
    });
  }

  private static long measureMinMs(int iterations, Runnable action) {
    long minNanos = Long.MAX_VALUE;
    for (int i = 0; i < iterations; i++) {
      long start = System.nanoTime();
      action.run();
      long elapsed = System.nanoTime() - start;
      minNanos = Math.min(minNanos, elapsed);
    }
    return Duration.ofNanos(minNanos).toMillis();
  }

  private static void assertOptionalMaxMs(String propertyName, long actualMs) {
    String value = System.getProperty(propertyName);
    if (value == null || value.isBlank()) {
      return;
    }
    long maxMs = Long.parseLong(value);
    assertThat(actualMs).isLessThanOrEqualTo(maxMs);
  }

  private static int getIntProperty(String propertyName, int defaultValue) {
    String value = System.getProperty(propertyName);
    return (value == null || value.isBlank()) ? defaultValue : Integer.parseInt(value);
  }
}
