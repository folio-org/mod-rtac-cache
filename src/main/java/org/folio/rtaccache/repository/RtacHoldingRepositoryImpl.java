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
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class RtacHoldingRepositoryImpl implements RtacHoldingRepositoryCustom {

  private static final Pattern SPLIT_PATTERN = Pattern.compile("\\s+");

  @PersistenceContext
  private EntityManager entityManager;

  @Override
  @SuppressWarnings("unchecked") // Ok to suppress because RtacHoldingEntity is passed to createNativeQuery
  public Page<RtacHoldingEntity> search(String schemas, UUID instanceId, String query, Boolean available, boolean onlyShared, Pageable pageable) {
    var params = new HashMap<String, Object>();
    params.put("schemas", schemas);
    params.put("instanceIds", new UUID[]{instanceId});
    params.put("onlyShared", onlyShared);

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
    String filterCte = "WITH Filtered AS (SELECT * FROM rtac_holdings_multi_tenant(:schemas, :instanceIds, :onlyShared)) ";

    // Create and execute count query
    String countSql = filterCte + " SELECT count(h.id) " + fromClause + whereSql;
    Query countQuery = entityManager.createNativeQuery(countSql);
    params.forEach(countQuery::setParameter);
    long total = ((Number) countQuery.getSingleResult()).longValue();

    // Create and execute data query
    String orderByClause = toOrderByClause(pageable.getSort());
    String dataSql = filterCte + " SELECT * " + fromClause + whereSql + orderByClause;
    Query dataQuery = entityManager.createNativeQuery(dataSql, RtacHoldingEntity.class);
    params.forEach(dataQuery::setParameter);
    dataQuery.setFirstResult((int) pageable.getOffset());
    dataQuery.setMaxResults(pageable.getPageSize());

    List<RtacHoldingEntity> content = total > pageable.getOffset() ? dataQuery.getResultList() : List.of();

    return new PageImpl<>(content, pageable, total);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Page<RtacHoldingEntity> findAllByIdInstanceIdSingleQuery(String schemas, UUID instanceId, boolean onlyShared, Pageable pageable) {
    String filterCte = "WITH FilteredHoldings AS (SELECT * FROM rtac_holdings_multi_tenant(:schemas, ARRAY[:instanceId], :onlyShared)) ";
    String orderByClause = toOrderByClause(pageable.getSort());

    String sql = filterCte + """
      SELECT
        h.*,
        count(*) OVER() AS total_count
      FROM FilteredHoldings h
      """ + orderByClause;

    var jpaQuery = entityManager.createNativeQuery(sql);
    var nativeQuery = jpaQuery.unwrap(NativeQuery.class);
    nativeQuery.addEntity(RtacHoldingEntity.class);
    nativeQuery.addScalar("total_count", StandardBasicTypes.LONG);
    nativeQuery.setParameter("schemas", schemas);
    nativeQuery.setParameter("instanceId", instanceId);
    nativeQuery.setParameter("onlyShared", onlyShared);
    nativeQuery.setFirstResult((int) pageable.getOffset());
    nativeQuery.setMaxResults(pageable.getPageSize());

    List<Object[]> rows = (List<Object[]>) nativeQuery.getResultList();
    if (rows.isEmpty()) {
      // For out-of-bounds pages, total_count isn't available, so fall back to a dedicated count query.
      if (pageable.getOffset() > 0) {
        String countSql = "SELECT count(*) FROM rtac_holdings_multi_tenant(:schemas, ARRAY[:instanceId], :onlyShared)";
        Query countQuery = entityManager.createNativeQuery(countSql);
        countQuery.setParameter("schemas", schemas);
        countQuery.setParameter("instanceId", instanceId);
        countQuery.setParameter("onlyShared", onlyShared);
        long total = ((Number) countQuery.getSingleResult()).longValue();
        return new PageImpl<>(List.of(), pageable, total);
      }
      return new PageImpl<>(List.of(), pageable, 0);
    }

    var content = new ArrayList<RtacHoldingEntity>(rows.size());
    long total = 0;
    for (Object[] row : rows) {
      content.add((RtacHoldingEntity) row[0]);
      total = ((Number) row[1]).longValue();
    }
    return new PageImpl<>(content, pageable, total);
  }

  private String toOrderByClause(Sort sort) {
    if (sort.isUnsorted()) {
      return "";
    }
    var orders = new ArrayList<String>();
    for (var order : sort) {
      orders.add(toSqlOrder(order));
    }
    return " ORDER BY " + String.join(", ", orders);
  }

  private String toSqlOrder(Sort.Order order) {
    String property = switch (order.getProperty()) {
      case "id" -> "h.id";
      case "type" -> "h.type";
      case "instanceId" -> "h.instance_id";
      case "libraryName" -> "h.library_name";
      case "locationName" -> "h.location_name";
      case "effectiveShelvingOrder" -> "h.effective_shelving_order";
      case "status" -> "h.status_name";
      default -> order.getProperty(); // Fallback for safety, though should not be used for JSON properties
    };
    return property + " " + order.getDirection();
  }
}
