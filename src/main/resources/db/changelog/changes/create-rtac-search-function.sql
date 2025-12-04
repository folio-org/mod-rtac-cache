CREATE OR REPLACE FUNCTION rtac_holding_search_text(data jsonb)
RETURNS text AS '
  SELECT CONCAT_WS('' '',
    data->>''volume'',
    data->>''callNumber'',
    data->''location''->>''name'',
    data->''library''->>''name''
  );
' LANGUAGE sql IMMUTABLE;
