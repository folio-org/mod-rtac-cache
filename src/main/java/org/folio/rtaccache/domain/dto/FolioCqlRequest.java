package org.folio.rtaccache.domain.dto;

public record FolioCqlRequest(String query, Integer limit, Integer offset) { }
