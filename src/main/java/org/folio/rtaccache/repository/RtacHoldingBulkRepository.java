package org.folio.rtaccache.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.dto.Instance;
import org.folio.rtaccache.domain.dto.Location;
import org.folio.rtaccache.domain.dto.Loclib;
import org.folio.rtaccache.domain.dto.RtacHolding;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RtacHoldingBulkRepository {

  private static final String BULK_UPSERT_SQL = """
    INSERT INTO rtac_holding (instance_id, type, id, shared,rtac_holding_json, created_at)
    VALUES (?, ?, ?, ?, ?::jsonb, ?)
    ON CONFLICT (instance_id, type, id)
    DO UPDATE SET
      rtac_holding_json = EXCLUDED.rtac_holding_json,
      created_at = EXCLUDED.created_at
  """;

  private static final String LOCATION_DATA_UPDATE_SQL = """
    UPDATE rtac_holding
    SET rtac_holding_json = jsonb_set(
        jsonb_set(
            rtac_holding_json,
            '{location,name}',
            to_jsonb(?::text)
        ),
        '{location,code}',
        to_jsonb(?::text)
    )
    WHERE rtac_holding_json->'location'->>'id' = ?
  """;

  private static final String LIBRARY_DATA_UPDATE_SQL = """
    UPDATE rtac_holding
    SET rtac_holding_json = jsonb_set(
        jsonb_set(
            rtac_holding_json,
            '{library,name}',
            to_jsonb(?::text)
        ),
        '{library,code}',
        to_jsonb(?::text)
    )
    WHERE rtac_holding_json->'library'->>'id' = ?
  """;

  private static final String FORMAT_IDS_DATA_UPDATE_SQL = """
    UPDATE rtac_holding
    SET rtac_holding_json = jsonb_set(
      rtac_holding_json,
      '{instanceFormatIds}',
      to_jsonb(?::text[])
    )
    WHERE instance_id = ?::uuid
  """;

  private static final String MARK_AS_SHARED_SQL = """
    UPDATE rtac_holding
    SET shared = TRUE
    WHERE instance_id = ?::uuid
  """;

  private static final String ITEM_HOLDINGS_COPY_NUMBER_DATA_UPDATE_SQL = """
    UPDATE rtac_holding
    SET rtac_holding_json = jsonb_set(
      rtac_holding_json,
      '{holdingsCopyNumber}',
      COALESCE(to_jsonb(?::text), 'null'::jsonb)
    )
    WHERE instance_id = ?::uuid
    AND type = 'ITEM'
    AND rtac_holding_json->>'holdingsId' = ?
  """;

  private static final String ITEM_DUE_DATE_UPDATE_SQL = """
    UPDATE rtac_holding
    SET rtac_holding_json = jsonb_set(
      rtac_holding_json,
      '{dueDate}',
      COALESCE(to_jsonb(?::text), 'null'::jsonb)
    )
    WHERE type = 'ITEM'
    AND id = ?::uuid
  """;

  private static final String ITEM_HOLD_COUNT_UPDATE_SQL = """
    UPDATE rtac_holding
    SET rtac_holding_json = jsonb_set(
      rtac_holding_json,
      '{totalHoldRequests}',
      to_jsonb(COALESCE((rtac_holding_json->>'totalHoldRequests')::int, 0) + ?::int)
    )
    WHERE instance_id = ?::uuid
    AND type = 'ITEM'
    AND id = ?::uuid
  """;

  private static final String ITEM_KAFKA_UPDATE_SQL = """
      UPDATE rtac_holding
      SET rtac_holding_json = rtac_holding_json || jsonb_build_object(
        'barcode', ?::text,
        'callNumber', ?::text,
        'itemCopyNumber', ?::text,
        'volume', ?::text,
        'effectiveShelvingOrder', ?::text,
        'status', ?::text,
        'suppressFromDiscovery', ?::boolean,
        'location', ?::jsonb,
        'library', ?::jsonb,
        'materialType', ?::jsonb,
        'temporaryLoanType', ?::text,
        'permanentLoanType', ?::text )
      WHERE instance_id = ?::uuid AND type = 'ITEM'
      AND id = ?::uuid
    """;

  private static final String HOLDINGS_KAFKA_UPDATE_SQL = """
      UPDATE rtac_holding
      SET rtac_holding_json = rtac_holding_json || jsonb_build_object(
        'callNumber', ?::text,
        'holdingsCopyNumber', ?::text,
        'status', ?::text,
        'suppressFromDiscovery', ?::boolean,
        'location', ?::jsonb,
        'library', ?::jsonb,
        'holdingsStatements', ?::jsonb,
        'holdingsStatementsForIndexes', ?::jsonb,
        'holdingsStatementsForSupplements', ?::jsonb,
        'notes', ?::jsonb )
      WHERE instance_id = ?::uuid AND type = 'HOLDING'
      AND id = ?::uuid
    """;

  private static final String HOLDINGS_PIECES_KAFKA_UPDATE_SQL = """
      UPDATE rtac_holding
      SET rtac_holding_json = rtac_holding_json || jsonb_build_object(
        'callNumber', ?::text,
        'location', ?::jsonb,
        'library', ?::jsonb)
      WHERE instance_id = ?::uuid AND type = 'PIECE'
      AND rtac_holding_json->>'holdingsId' = ?
    """;

  private static final String PIECE_KAFKA_UPDATE_SQL = """
      UPDATE rtac_holding
      SET rtac_holding_json = rtac_holding_json || jsonb_build_object(
        'holdingsCopyNumber', ?::text,
        'status', ?::text,
        'volume', ?::text,
        'suppressFromDiscovery', ?::boolean )
      WHERE type = 'PIECE'
      AND id = ?::uuid
    """;

  private final DataSource dataSource;
  private final ObjectMapper objectMapper;
  private static final int BATCH_SIZE = 200;

  public void bulkUpsert(List<RtacHoldingEntity> holdings) throws SQLException, JsonProcessingException {
    try (Connection connection = dataSource.getConnection();
      PreparedStatement ps = connection.prepareStatement(BULK_UPSERT_SQL)) {

      int count = 0;
      for (var holding : holdings) {
        ps.setObject(1, holding.getId().getInstanceId());
        ps.setString(2, holding.getId().getType().name());
        ps.setObject(3, holding.getId().getId());
        ps.setBoolean(4, holding.isShared());
        ps.setString(5, objectMapper.writeValueAsString(holding.getRtacHolding()));
        ps.setTimestamp(6, Timestamp.from(holding.getCreatedAt()));
        ps.addBatch();

        if (++count % BATCH_SIZE == 0) {
          ps.executeBatch();
        }
      }

      ps.executeBatch();
    }
  }

  public void bulkUpdateLocationData(Location location) throws SQLException {
    try (Connection connection = dataSource.getConnection();
      PreparedStatement ps = connection.prepareStatement(LOCATION_DATA_UPDATE_SQL)) {
      ps.setString(1, location.getName());
      ps.setString(2, location.getCode());
      ps.setString(3, location.getId());
      ps.executeUpdate();
    }
  }

  public void bulkUpdateLibraryData(Loclib library) throws SQLException {
    try (Connection connection = dataSource.getConnection();
      PreparedStatement ps = connection.prepareStatement(LIBRARY_DATA_UPDATE_SQL)) {
      ps.setString(1, library.getName());
      ps.setString(2, library.getCode());
      ps.setString(3, library.getId());
      ps.executeUpdate();
    }
  }

  public void bulkUpdateInstanceFormatIds(Instance instance) throws SQLException {
    try (Connection connection = dataSource.getConnection();
      PreparedStatement ps = connection.prepareStatement(FORMAT_IDS_DATA_UPDATE_SQL)) {
      var formatIds = instance.getInstanceFormatIds();
      var formatIdsArray = connection.createArrayOf(
        "text",
        (formatIds == null ? new String[0] : formatIds.toArray(String[]::new))
      );
      ps.setArray(1, formatIdsArray);
      ps.setObject(2, instance.getId());
      ps.executeUpdate();
    }
  }

  public void bulkMarkHoldingsAsSharedByInstanceId(UUID instanceId) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement ps = connection.prepareStatement(MARK_AS_SHARED_SQL)) {
      ps.setObject(1, instanceId);
      ps.executeUpdate();
    }
  }

  public void updateItemsHoldingsCopyNumber(UUID instanceId, String holdingsId, String holdingsCopyNumber) throws SQLException {
    var connection = DataSourceUtils.getConnection(dataSource);
    try (PreparedStatement ps = connection.prepareStatement(ITEM_HOLDINGS_COPY_NUMBER_DATA_UPDATE_SQL)) {
      ps.setString(1, holdingsCopyNumber);
      ps.setObject(2, instanceId);
      ps.setString(3, holdingsId);
      ps.executeUpdate();
    } finally {
      DataSourceUtils.releaseConnection(connection, dataSource);
    }
  }

  public void updateItemDataFromKafkaItemEvent(UUID instanceId, UUID itemId, RtacHolding rtacHolding)
    throws SQLException, JsonProcessingException {
    var connection = DataSourceUtils.getConnection(dataSource);
    try (PreparedStatement ps = connection.prepareStatement(ITEM_KAFKA_UPDATE_SQL)) {
      ps.setString(1, rtacHolding.getBarcode());
      ps.setString(2, rtacHolding.getCallNumber());
      ps.setString(3, rtacHolding.getItemCopyNumber());
      ps.setString(4, rtacHolding.getVolume());
      ps.setString(5, rtacHolding.getEffectiveShelvingOrder());
      ps.setString(6, rtacHolding.getStatus());
      ps.setBoolean(7, rtacHolding.getSuppressFromDiscovery() != null && rtacHolding.getSuppressFromDiscovery());
      ps.setString(8, objectMapper.writeValueAsString(rtacHolding.getLocation()));
      ps.setString(9, objectMapper.writeValueAsString(rtacHolding.getLibrary()));
      ps.setString(10, objectMapper.writeValueAsString(rtacHolding.getMaterialType()));
      ps.setString(11, rtacHolding.getTemporaryLoanType());
      ps.setString(12, rtacHolding.getPermanentLoanType());
      ps.setObject(13, instanceId);
      ps.setObject(14, itemId);
      ps.executeUpdate();
    } finally {
      DataSourceUtils.releaseConnection(connection, dataSource);
    }
  }

  public void updateItemsDueDate(UUID itemId, Date dueDate) throws SQLException {
    var connection = DataSourceUtils.getConnection(dataSource);
    try (PreparedStatement ps = connection.prepareStatement(ITEM_DUE_DATE_UPDATE_SQL)) {
      ps.setString(1, dueDate != null ? dueDate.toInstant().toString() : null);
      ps.setObject(2, itemId);
      ps.executeUpdate();
    } finally {
      DataSourceUtils.releaseConnection(connection, dataSource);
    }
  }

  public void updateItemsHoldCount(UUID instanceId, UUID itemId, int delta) throws SQLException {
    var connection = DataSourceUtils.getConnection(dataSource);
    try (PreparedStatement ps = connection.prepareStatement(ITEM_HOLD_COUNT_UPDATE_SQL)) {
      ps.setInt(1, delta);
      ps.setObject(2, instanceId);
      ps.setObject(3, itemId);
      ps.executeUpdate();
    } finally {
      DataSourceUtils.releaseConnection(connection, dataSource);
    }
  }

  public void updateHoldingsDataFromKafkaHoldingsEvent(UUID instanceId, UUID holdingsId, RtacHolding rtacHolding)
    throws SQLException, JsonProcessingException {
    var connection = DataSourceUtils.getConnection(dataSource);
    try (PreparedStatement ps = connection.prepareStatement(HOLDINGS_KAFKA_UPDATE_SQL)) {
      ps.setString(1, rtacHolding.getCallNumber());
      ps.setString(2, rtacHolding.getHoldingsCopyNumber());
      ps.setString(3, rtacHolding.getStatus());
      ps.setBoolean(4, rtacHolding.getSuppressFromDiscovery() != null && rtacHolding.getSuppressFromDiscovery());
      ps.setString(5, objectMapper.writeValueAsString(rtacHolding.getLocation()));
      ps.setString(6, objectMapper.writeValueAsString(rtacHolding.getLibrary()));
      ps.setString(7, objectMapper.writeValueAsString(rtacHolding.getHoldingsStatements()));
      ps.setString(8, objectMapper.writeValueAsString(rtacHolding.getHoldingsStatementsForIndexes()));
      ps.setString(9, objectMapper.writeValueAsString(rtacHolding.getHoldingsStatementsForSupplements()));
      ps.setString(10, objectMapper.writeValueAsString(rtacHolding.getNotes()));
      ps.setObject(11, instanceId);
      ps.setObject(12, holdingsId);
      ps.executeUpdate();
    } finally {
      DataSourceUtils.releaseConnection(connection, dataSource);
    }
  }

  public void updatePieceDataFromKafkaHoldingsEvent(UUID instanceId, String holdingsId, RtacHolding rtacHolding)
    throws SQLException, JsonProcessingException {
    var connection = DataSourceUtils.getConnection(dataSource);
    try (PreparedStatement ps = connection.prepareStatement(HOLDINGS_PIECES_KAFKA_UPDATE_SQL)) {
      ps.setString(1, rtacHolding.getCallNumber());
      ps.setString(2, objectMapper.writeValueAsString(rtacHolding.getLocation()));
      ps.setString(3, objectMapper.writeValueAsString(rtacHolding.getLibrary()));
      ps.setObject(4, instanceId);
      ps.setString(5, holdingsId);
      ps.executeUpdate();
    } finally {
      DataSourceUtils.releaseConnection(connection, dataSource);
    }
  }

  public void updatePieceDataFromKafkaPiecesEvent(UUID pieceId, RtacHolding rtacHolding)
    throws SQLException {
    var connection = DataSourceUtils.getConnection(dataSource);
    try (PreparedStatement ps = connection.prepareStatement(PIECE_KAFKA_UPDATE_SQL)) {
      ps.setString(1, rtacHolding.getHoldingsCopyNumber());
      ps.setString(2, rtacHolding.getStatus());
      ps.setString(3, rtacHolding.getVolume());
      ps.setBoolean(4, rtacHolding.getSuppressFromDiscovery() != null && rtacHolding.getSuppressFromDiscovery());
      ps.setObject(5, pieceId);
      ps.executeUpdate();
    } finally {
      DataSourceUtils.releaseConnection(connection, dataSource);
    }
  }

}
