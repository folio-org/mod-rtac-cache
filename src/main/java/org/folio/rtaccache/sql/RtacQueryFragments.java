package org.folio.rtaccache.sql;

public final class RtacQueryFragments {
    private RtacQueryFragments() {}

    public static final String SORT_COLUMN_PROJECTIONS = """
        ,
        rtac_holding_json->>'effectiveShelvingOrder' AS effectiveShelvingOrder,
        rtac_holding_json->'library'->>'name' AS libraryName,
        rtac_holding_json->'location'->>'name' AS locationName,
        rtac_holding_json->>'status' AS status
    """;
}