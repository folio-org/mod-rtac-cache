package org.folio.rtaccache.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.folio.rtaccache.domain.RtacHoldingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class RtacHoldingRepositoryImpl implements RtacHoldingRepositoryCustom {

  private static final Pattern SPLIT_PATTERN = Pattern.compile("\\s+");

  @PersistenceContext
  private EntityManager entityManager;

  @Override
  @SuppressWarnings("unchecked") // Ok to suppress because RtacHoldingEntity is passed to createNativeQuery
  public Page<RtacHoldingEntity> search(String schemas, UUID instanceId, String query, Boolean available, Pageable pageable) {
    var params = new HashMap<String, Object>();
    params.put("schemas", schemas);
    params.put("instanceIds", new UUID[]{instanceId});

    var whereClause = new ArrayList<String>();

    var terms = Arrays.stream(SPLIT_PATTERN.split(query))
      .filter(s -> !s.isEmpty())
      .toList();

    IntStream.range(0, terms.size()).forEach(i -> {
      String paramName = "term" + i;
      whereClause.add("rtac_holding_search_text(h.rtac_holding_json) ILIKE :" + paramName);
      params.put(paramName, "%" + terms.get(i) + "%");
    });

    if (Boolean.TRUE.equals(available)) {
      whereClause.add("cast(h.rtac_holding_json ->> 'status' as text) = 'Available'");
    }

    String whereSql = whereClause.isEmpty() ? "" : "WHERE " + String.join(" AND ", whereClause);
    String fromClause = "FROM Filtered h ";
    String filterCte = "WITH Filtered AS (SELECT * FROM rtac_holdings_multi_tenant(:schemas, :instanceIds)) ";

    // Create and execute count query
    String countSql = filterCte + " SELECT count(h.id) " + fromClause + whereSql;
    Query countQuery = entityManager.createNativeQuery(countSql);
    params.forEach(countQuery::setParameter);
    long total = ((Number) countQuery.getSingleResult()).longValue();

    // Create and execute data query
    String dataSql = filterCte + " SELECT * " + fromClause + whereSql + " ORDER BY h.id";
    Query dataQuery = entityManager.createNativeQuery(dataSql, RtacHoldingEntity.class);
    params.forEach(dataQuery::setParameter);
    dataQuery.setFirstResult((int) pageable.getOffset());
    dataQuery.setMaxResults(pageable.getPageSize());

    List<RtacHoldingEntity> content = total > pageable.getOffset() ? dataQuery.getResultList() : List.of();

    return new PageImpl<>(content, pageable, total);
  }
}
