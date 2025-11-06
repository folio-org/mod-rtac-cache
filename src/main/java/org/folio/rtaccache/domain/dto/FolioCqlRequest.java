package org.folio.rtaccache.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class FolioCqlRequest {
  private String query;
  private Integer limit;
  private Integer offset;
}
