CREATE OR REPLACE FUNCTION rtac_holdings_multi_tenant(
    schemas_str text, -- comma-separated
    instance_ids uuid[]
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
            format('SELECT * FROM %I.rtac_holding', schema_name),
            ' UNION ALL '
        )
        FROM unnest(string_to_array(schemas_str, ',')) AS schema_name
    );

    -- If no schemas are provided, return nothing
    IF union_query IS NULL THEN
        RETURN;
    END IF;

    RETURN QUERY EXECUTE
        'WITH all_tenants AS (' || union_query || ')
         SELECT *
         FROM all_tenants h
         WHERE h.instance_id = ANY($1)
           AND (
             h.type = ''PIECE''
             OR h.type = ''ITEM''
             OR (
               h.type = ''HOLDING''
               AND NOT EXISTS (
                 SELECT 1
                 FROM all_tenants hi
                 WHERE hi.instance_id = h.instance_id
                   AND hi.type = ''ITEM''
               )
             )
           )'
    USING instance_ids;
END;
$$;
