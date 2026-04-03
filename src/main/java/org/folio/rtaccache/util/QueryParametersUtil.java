package org.folio.rtaccache.util;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.codehaus.plexus.util.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class QueryParametersUtil {

  public Map<String, String> toMap(String query, int limit, int offset) {
    var map = new LinkedHashMap<String, String>();
    if (StringUtils.isNotBlank(query)) {
      map.put("query", query);
    }
    map.put("limit", String.valueOf(limit));
    map.put("offset", String.valueOf(offset));
    return map;
  }
}

