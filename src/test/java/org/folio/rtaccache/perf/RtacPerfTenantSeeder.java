package org.folio.rtaccache.perf;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

final class RtacPerfTenantSeeder {

  private RtacPerfTenantSeeder() {
  }

  static void resetAndSeed(Connection connection, SeedOptions options) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE rtac_holding;");
    }

    connection.setAutoCommit(false);
    try {
      seed(connection, options);
      try (Statement statement = connection.createStatement()) {
        statement.execute("ANALYZE rtac_holding;");
      }
      connection.commit();
    } catch (Exception e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(true);
    }
  }

  private static void seed(Connection connection, SeedOptions options) throws SQLException {
    var random = new Random(options.randomSeed);

    var holdingIds = new ArrayList<UUID>(options.holdingsCount);
    for (int i = 0; i < options.holdingsCount; i++) {
      holdingIds.add(new UUID(random.nextLong(), random.nextLong()));
    }

    var insertSql = """
      INSERT INTO rtac_holding (instance_id, type, id, rtac_holding_json, created_at, shared)
      VALUES (?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP, ?)
      """;

    try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
      int batchSize = 0;

      for (int i = 0; i < holdingIds.size(); i++) {
        UUID holdingId = holdingIds.get(i);
        ps.setObject(1, options.instanceId);
        ps.setString(2, "HOLDING");
        ps.setObject(3, holdingId);
        ps.setString(4, holdingJson(options.instanceId, holdingId, options.tenantOrdinal, i));
        ps.setBoolean(5, options.shared);
        ps.addBatch();
        batchSize++;
        if (batchSize >= options.jdbcBatchSize) {
          ps.executeBatch();
          batchSize = 0;
        }
      }

      for (int i = 0; i < options.itemsCount; i++) {
        UUID itemId = new UUID(random.nextLong(), random.nextLong());
        UUID holdingId = holdingIds.get(i % holdingIds.size());
        ps.setObject(1, options.instanceId);
        ps.setString(2, "ITEM");
        ps.setObject(3, itemId);
        ps.setString(4, itemJson(options.instanceId, itemId, holdingId, options.tenantOrdinal, i, random));
        ps.setBoolean(5, options.shared);
        ps.addBatch();
        batchSize++;
        if (batchSize >= options.jdbcBatchSize) {
          ps.executeBatch();
          batchSize = 0;
        }
      }

      if (batchSize > 0) {
        ps.executeBatch();
      }
    }
  }

  private static String holdingJson(UUID instanceId, UUID holdingId, int tenantOrdinal, int holdingIndex) {
    return """
      {
        "instanceId": "%s",
        "id": "%s",
        "type": "holding",
        "status": "Available",
        "suppressFromDiscovery": false,
        "instanceFormatIds": ["fmt1"],
        "effectiveShelvingOrder": "%s",
        "library": {"id": "lib-%d", "code": "L%d", "name": "Library %d"},
        "location": {"id": "loc-%d", "code": "LOC%d", "name": "Location %d"}
      }
      """.formatted(
      instanceId,
      holdingId,
      shelvingOrder(tenantOrdinal, holdingIndex),
      tenantOrdinal, tenantOrdinal, tenantOrdinal,
      tenantOrdinal, tenantOrdinal, tenantOrdinal
    ).trim();
  }

  private static String itemJson(UUID instanceId, UUID itemId, UUID holdingId, int tenantOrdinal, int itemIndex, Random random) {
    String status = (itemIndex % 10 == 0) ? "Checked out" : "Available";
    int locationBucket = random.nextInt(25);
    int libraryBucket = random.nextInt(10);
    return """
      {
        "instanceId": "%s",
        "id": "%s",
        "holdingsId": "%s",
        "type": "item",
        "status": "%s",
        "suppressFromDiscovery": false,
        "instanceFormatIds": ["fmt1"],
        "effectiveShelvingOrder": "%s",
        "library": {"id": "lib-%d-%d", "code": "L%d_%d", "name": "Library %d-%d"},
        "location": {"id": "loc-%d-%d", "code": "LOC%d_%d", "name": "Location %d-%d"}
      }
      """.formatted(
      instanceId,
      itemId,
      holdingId,
      status,
      shelvingOrder(tenantOrdinal, itemIndex),
      tenantOrdinal, libraryBucket, tenantOrdinal, libraryBucket, tenantOrdinal, libraryBucket,
      tenantOrdinal, locationBucket, tenantOrdinal, locationBucket, tenantOrdinal, locationBucket
    ).trim();
  }

  private static String shelvingOrder(int tenantOrdinal, int index) {
    return "T" + tenantOrdinal + "-" + String.format("%06d", index);
  }

  record SeedOptions(
    UUID instanceId,
    int tenantOrdinal,
    int holdingsCount,
    int itemsCount,
    boolean shared,
    long randomSeed,
    int jdbcBatchSize
  ) {
    static SeedOptions defaults(UUID instanceId, int tenantOrdinal, int itemsCount) {
      return new SeedOptions(instanceId, tenantOrdinal, 200, itemsCount, true, 42L + tenantOrdinal, 1_000);
    }
  }
}

