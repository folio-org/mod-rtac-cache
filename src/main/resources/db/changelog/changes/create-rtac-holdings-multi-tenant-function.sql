CREATE OR REPLACE FUNCTION rtac_holdings_multi_tenant(
    schemas_str text, -- comma-separated
    instance_ids uuid[],
    only_shared boolean -- when true, restrict to shared holdings
)
RETURNS SETOF rtac_holding
LANGUAGE plpgsql
AS $$
DECLARE
    union_query text;
BEGIN
    -- Build a UNION ALL query across all tenant schemas
    union_query := (
        SELECT string_agg(
            format(
              'SELECT * FROM %I.rtac_holding h WHERE h.instance_id = ANY($1) AND ($2 IS NOT TRUE OR h.shared = TRUE)',
              schema_name
            ),
            ' UNION ALL '
        )
        FROM unnest(string_to_array(schemas_str, ',')) AS schema_name
    );

    -- If no schemas are provided, return nothing
    IF union_query IS NULL THEN
        RETURN;
    END IF;

    RETURN QUERY EXECUTE
        'WITH all_tenants AS MATERIALIZED (' || union_query || '),
         holding_suppression AS (
           SELECT
             instance_id,
             id AS holding_id,
             COALESCE((rtac_holding_json->>''suppressFromDiscovery'')::boolean, FALSE) AS is_suppressed
           FROM all_tenants
           WHERE type = ''HOLDING''
         ),
         instance_item_presence AS (
           SELECT
             instance_id,
             bool_or(type = ''ITEM'') AS has_item
           FROM all_tenants
           GROUP BY instance_id
         )
         SELECT h.*
         FROM all_tenants h
         LEFT JOIN holding_suppression hs
           ON hs.instance_id = h.instance_id
          AND hs.holding_id = (h.rtac_holding_json->>''holdingsId'')::uuid
         JOIN instance_item_presence ip
           ON ip.instance_id = h.instance_id
         WHERE COALESCE((h.rtac_holding_json->>''suppressFromDiscovery'')::boolean, FALSE) = FALSE
           AND COALESCE(hs.is_suppressed, FALSE) = FALSE
           AND (
             h.type IN (''PIECE'', ''ITEM'')
             OR (h.type = ''HOLDING'' AND ip.has_item IS NOT TRUE)
           )'
    USING instance_ids, only_shared;
END;
$$;
