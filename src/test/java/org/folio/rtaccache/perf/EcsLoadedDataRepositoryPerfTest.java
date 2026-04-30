package org.folio.rtaccache.perf;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
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
  private static final Pattern WORK_MEM_PATTERN = Pattern.compile("^\\d+\\s*(B|kB|MB|GB)?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern TEMP_READ_PATTERN = Pattern.compile("\\btemp read=(\\d+)\\b");
  private static final Pattern TEMP_WRITTEN_PATTERN = Pattern.compile("\\btemp written=(\\d+)\\b");

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
      rtacHoldingRepository.findAllByIdInstanceId(schemasParam, instanceId, true, pageable).getContent();

      long minMs = measureMinMs(5, () -> rtacHoldingRepository
        .findAllByIdInstanceId(schemasParam, instanceId, true, pageable)
        .getContent());

      log.info("RTAC PERF GET pageQuery (defaultSort=jsonKeys) minMs={}", minMs);

      assertOptionalMaxMs("perf.maxGetMs", minMs);

      if (getBooleanProperty("perf.explain", false)) {
        try (Connection connection = dataSource.getConnection()) {
          explainGetDefaultSort(connection, 100);
        }
      }
    });
  }

  @Test
  void perf_searchRtacCacheHoldings_query() {
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
      var warmup = rtacHoldingRepository.search(schemasParam, instanceId, "Library", null, true, pageable)
        .getContent();
      assertThat(warmup).isNotEmpty();

      long minMs = measureMinMs(5, () -> rtacHoldingRepository
        .search(schemasParam, instanceId, "Library", null, true, pageable)
        .getContent());

      log.info("RTAC PERF SEARCH query (defaultSort=jsonKeys) minMs={}", minMs);

      assertOptionalMaxMs("perf.maxSearchMs", minMs);

      if (getBooleanProperty("perf.explain", false)) {
        try (Connection connection = dataSource.getConnection()) {
          explainSearchDefaultSort(connection, "Library", 100);
        }
      }
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

      if (getBooleanProperty("perf.explain", false)) {
        try (Connection connection = dataSource.getConnection()) {
          explainBatchSummary(connection);
        }
      }
    });
  }

  private void explainGetDefaultSort(Connection connection, int limit) throws Exception {
    applyExplainSessionSettings(connection);
    String sql = """
      EXPLAIN (ANALYZE, BUFFERS)
      WITH FilteredHoldings AS (
        SELECT * FROM rtac_holdings_multi_tenant(?, ARRAY[?]::uuid[], ?)
      )
      SELECT
        *,
        rtac_holding_json->>'effectiveShelvingOrder' AS effectiveShelvingOrder,
        rtac_holding_json->'library'->>'name' AS libraryName,
        rtac_holding_json->'location'->>'name' AS locationName,
        rtac_holding_json->>'status' AS status
      FROM FilteredHoldings
      ORDER BY effectiveShelvingOrder desc, status asc, libraryName asc, locationName asc
      FETCH FIRST ? ROWS ONLY
      """;

    log.info("RTAC PERF EXPLAIN GET (defaultSort=jsonKeys) begin");
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setString(1, schemasParam);
      ps.setObject(2, instanceId);
      ps.setBoolean(3, true);
      ps.setInt(4, limit);
      ExplainTempIoStats stats = logExplainRows(ps);
      log.info("RTAC PERF EXPLAIN SUMMARY GET tempReadBlocks={} tempWrittenBlocks={}", stats.tempReadBlocks, stats.tempWrittenBlocks);
    }
    log.info("RTAC PERF EXPLAIN GET (defaultSort=jsonKeys) end");
  }

  private void explainSearchDefaultSort(Connection connection, String query, int limit) throws Exception {
    applyExplainSessionSettings(connection);
    String sql = """
      EXPLAIN (ANALYZE, BUFFERS)
      WITH Filtered AS (
        SELECT * FROM rtac_holdings_multi_tenant(?, ARRAY[?]::uuid[], ?)
      )
      SELECT *
      FROM Filtered h
      WHERE rtac_holding_search_text(h.rtac_holding_json) ILIKE ?
      ORDER BY h.rtac_holding_json->>'effectiveShelvingOrder' desc,
        h.rtac_holding_json->>'status' asc,
        h.rtac_holding_json->'library'->>'name' asc,
        h.rtac_holding_json->'location'->>'name' asc
      FETCH FIRST ? ROWS ONLY
      """;

    log.info("RTAC PERF EXPLAIN SEARCH (defaultSort=jsonKeys) begin");
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setString(1, schemasParam);
      ps.setObject(2, instanceId);
      ps.setBoolean(3, true);
      ps.setString(4, "%" + query + "%");
      ps.setInt(5, limit);
      ExplainTempIoStats stats = logExplainRows(ps);
      log.info("RTAC PERF EXPLAIN SUMMARY SEARCH tempReadBlocks={} tempWrittenBlocks={}", stats.tempReadBlocks, stats.tempWrittenBlocks);
    }
    log.info("RTAC PERF EXPLAIN SEARCH (defaultSort=jsonKeys) end");
  }

  private void explainBatchSummary(Connection connection) throws Exception {
    applyExplainSessionSettings(connection);
    String sql = """
      EXPLAIN (ANALYZE, BUFFERS)
      WITH Filtered AS (
        SELECT * FROM rtac_holdings_multi_tenant(?, ARRAY[?]::uuid[], ?)
      ),
      LocationStatusCounts AS (
        SELECT
          h_inner.instance_id,
          (h_inner.rtac_holding_json->'library'->>'id') AS libraryId,
          (h_inner.rtac_holding_json->'location'->>'id') AS locationId,
          (h_inner.rtac_holding_json->'location'->>'code') AS locationCode,
          (h_inner.rtac_holding_json->>'type') AS type,
          (h_inner.rtac_holding_json->>'status') AS status,
          COUNT(*) AS statusCount
        FROM
          Filtered h_inner
        GROUP BY
          h_inner.instance_id,
          (h_inner.rtac_holding_json->'library'->>'id'),
          (h_inner.rtac_holding_json->'location'->>'id'),
          (h_inner.rtac_holding_json->'location'->>'code'),
          (h_inner.rtac_holding_json->>'type'),
          (h_inner.rtac_holding_json->>'status')
      )
      SELECT
        h.instance_id AS instanceId,
        bool_or(h.rtac_holding_json->>'volume' IS NOT NULL AND h.rtac_holding_json->>'volume' != '') AS hasVolumes,
        (SELECT h_if.rtac_holding_json->>'instanceFormatIds' FROM Filtered h_if WHERE h_if.instance_id = h.instance_id LIMIT 1) AS instanceFormatIds,
        (
          SELECT
            json_agg(
              json_build_object(
                'libraryId', lsc.libraryId,
                'locationId', lsc.locationId,
                'locationCode', lsc.locationCode,
                'status', lsc.status,
                'statusCount', lsc.statusCount,
                'type', lsc.type
              )
            )
          FROM
            LocationStatusCounts lsc
          WHERE
            lsc.instance_id = h.instance_id
        ) AS locationStatusJson
      FROM
        Filtered h
      GROUP BY
        h.instance_id
      """;

    log.info("RTAC PERF EXPLAIN BATCH begin");
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setString(1, schemasParam);
      ps.setObject(2, instanceId);
      ps.setBoolean(3, true);
      ExplainTempIoStats stats = logExplainRows(ps);
      log.info("RTAC PERF EXPLAIN SUMMARY BATCH tempReadBlocks={} tempWrittenBlocks={}", stats.tempReadBlocks, stats.tempWrittenBlocks);
    }
    log.info("RTAC PERF EXPLAIN BATCH end");
  }

  private ExplainTempIoStats logExplainRows(PreparedStatement ps) throws Exception {
    long tempReadBlocks = 0;
    long tempWrittenBlocks = 0;
    try (ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        // EXPLAIN returns one text column named "QUERY PLAN"
        String row = rs.getString(1);
        log.info("RTAC PERF EXPLAIN {}", row);

        // Prefer a simple "max observed" across nodes; the top node usually has query totals.
        Long read = extractLong(TEMP_READ_PATTERN, row);
        if (read != null) {
          tempReadBlocks = Math.max(tempReadBlocks, read);
        }
        Long written = extractLong(TEMP_WRITTEN_PATTERN, row);
        if (written != null) {
          tempWrittenBlocks = Math.max(tempWrittenBlocks, written);
        }
      }
    }
    return new ExplainTempIoStats(tempReadBlocks, tempWrittenBlocks);
  }

  private static void applyExplainSessionSettings(Connection connection) throws Exception {
    String workMem = getStringProperty("perf.explainWorkMem");
    if (workMem == null) {
      return;
    }
    String normalized = workMem.trim().replaceAll("\\s+", "");
    if (!WORK_MEM_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Invalid perf.explainWorkMem value: " + workMem);
    }
    try (Statement statement = connection.createStatement()) {
      statement.execute("SET work_mem = '" + normalized + "'");
    }
    log.info("RTAC PERF EXPLAIN setting work_mem={}", normalized);
  }

  private static Long extractLong(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    if (!matcher.find()) {
      return null;
    }
    return Long.parseLong(matcher.group(1));
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

  private static boolean getBooleanProperty(String propertyName, boolean defaultValue) {
    String value = System.getProperty(propertyName);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return Boolean.parseBoolean(value);
  }

  private static String getStringProperty(String propertyName) {
    String value = System.getProperty(propertyName);
    return (value == null || value.isBlank()) ? null : value;
  }

  private record ExplainTempIoStats(long tempReadBlocks, long tempWrittenBlocks) { }
}
