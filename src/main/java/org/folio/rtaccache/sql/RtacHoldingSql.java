package org.folio.rtaccache.sql;

/**
 * Centralized SQL fragments for Rtac Holding filtering logic.
 * These constants are intended for reuse in:
 *  - @Query(nativeQuery = true) annotations
 *  - StringBuilder composition in custom repository implementations
 *  - Incremental query extension when adding ORDER BY, JOIN, etc.
 */
public final class RtacHoldingSql {

  // Prevent instantiation
  private RtacHoldingSql() {}

  /**
   * Predicate fragment used in the WHERE clause.
   * This is the core filtering logic:
   *
   *   - Include "Piece" records
   *   - Include "Item" records
   *   - Include "Holding" records ONLY if there are no Item records for that instance
   */
  public static final String FILTER_PREDICATE = """
        (
          h.type = 'PIECE'
          OR h.type = 'ITEM'
          OR (
            h.type = 'HOLDING'
            AND NOT EXISTS (
              SELECT 1
              FROM rtac_holding hi
              WHERE hi.instance_id = h.instance_id
                AND hi.type = 'ITEM'
            )
          )
        )
        """;

  /**
   * Full CTE definition using the predicate.
   * This produces a WITH clause named "Filtered" containing the filtered rows.
   * Parameter: :instanceIds must be bound as a collection.
   */
  public static final String FILTER_CTE = """
        WITH Filtered AS (
          SELECT *
          FROM rtac_holding h
          WHERE h.instance_id IN (:instanceIds)
            AND\s""" + FILTER_PREDICATE + """
        )
        """;

  /**
   * Full CTE definition using the predicate for a single instanceId.
   * This produces a WITH clause named "Filtered" containing the filtered rows.
   * Parameter: :instanceId must be bound as a single UUID.
   */
  public static final String FILTER_CTE_SINGLE_ID = """
        WITH Filtered AS (
          SELECT *
          FROM rtac_holding h
          WHERE h.instance_id = :instanceId
            AND\s""" + FILTER_PREDICATE + """
        )
        """;

  /**
   * Basic "SELECT * FROM the CTE" fragment.
   * Most callers will concatenate this with FILTER_CTE.
   */
  public static final String SELECT_FROM_FILTER = """
        SELECT *
        FROM Filtered
        """;

}
