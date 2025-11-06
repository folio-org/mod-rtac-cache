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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RtacHoldingBulkRepository {

  private final DataSource dataSource;
  private final ObjectMapper objectMapper;
  private static final int BATCH_SIZE = 200;

  public void bulkUpsert(List<RtacHoldingEntity> holdings) throws SQLException, JsonProcessingException {
    String sql = """
      INSERT INTO rtac_holding (instance_id, type, id, rtac_holding_json, created_at)
      VALUES (?, ?, ?, ?::jsonb, ?)
      ON CONFLICT (instance_id, type, id)
      DO UPDATE SET
        rtac_holding_json = EXCLUDED.rtac_holding_json,
        created_at = EXCLUDED.created_at
      """;

    try (Connection connection = dataSource.getConnection();
      PreparedStatement ps = connection.prepareStatement(sql)) {

      int count = 0;
      for (var holding : holdings) {
        ps.setObject(1, holding.getId().getInstanceId());
        ps.setString(2, holding.getId().getType().name());
        ps.setObject(3, holding.getId().getId());
        ps.setString(4, objectMapper.writeValueAsString(holding.getRtacHolding()));
        ps.setTimestamp(5, Timestamp.from(holding.getCreatedAt()));
        ps.addBatch();

        if (++count % BATCH_SIZE == 0) {
          ps.executeBatch();
        }
      }

      ps.executeBatch();
    }
  }
}
