CREATE OR REPLACE FUNCTION public.delete_old_holdings_all_tenants(
    cutoff_time timestamp with time zone
)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    schema_name text;
    total_deleted integer := 0;
    deleted_count integer;
BEGIN
    -- Iterate over all tenant schemas matching the pattern
    FOR schema_name IN
        SELECT nspname
        FROM pg_catalog.pg_namespace
        WHERE nspname LIKE '%_mod_rtac_cache'
    LOOP
        -- Delete all holdings for instances that have at least one expired holding
        EXECUTE format(
            'DELETE FROM %I.rtac_holding h
             USING %I.rtac_holding old
             WHERE h.instance_id = old.instance_id
               AND old.created_at < $1',
            schema_name, schema_name
        ) USING cutoff_time;

        GET DIAGNOSTICS deleted_count = ROW_COUNT;
        total_deleted := total_deleted + deleted_count;

        RAISE NOTICE 'Deleted % old entries from schema %', deleted_count, schema_name;
    END LOOP;

    RETURN total_deleted;
END;
$$;
