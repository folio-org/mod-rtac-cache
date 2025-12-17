package org.folio.rtaccache.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.folio.rtaccache.domain.dto.Location;
import org.folio.rtaccache.domain.dto.Loclib;
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
      ps.setObject(1, location.getName());
      ps.setString(2, location.getCode());
      ps.setObject(3, location.getId());
      ps.executeUpdate();
    }
  }

  public void bulkUpdateLibraryData(Loclib library) throws SQLException {
    try (Connection connection = dataSource.getConnection();
      PreparedStatement ps = connection.prepareStatement(LIBRARY_DATA_UPDATE_SQL)) {
      ps.setObject(1, library.getName());
      ps.setString(2, library.getCode());
      ps.setObject(3, library.getId());
      ps.executeUpdate();
    }
  }



}
